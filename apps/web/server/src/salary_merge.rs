//! Content-aware merge for `fiatlife/salary` replaceable events.
//!
//! NIP-33 keeps the newest event by `created_at`, but the web app can publish a
//! newer copy that omits `paycheckLog` while Android still has logs locally.
//! When reading, fold all salary copies by JSON `updatedAt` and preserve logs
//! from older events when the newer copy is missing them (matches Android).

use nostr::{Event, Keys};
use serde_json::{Map, Value};

use crate::nostr_support::{decrypt_from_self, encrypt_to_self, AppDataRecord};

pub const SALARY_D_TAG: &str = "fiatlife/salary";

fn salary_updated_at_ms(plaintext: &str) -> i64 {
    serde_json::from_str::<Value>(plaintext)
        .ok()
        .and_then(|v| v.get("updatedAt").and_then(Value::as_i64))
        .unwrap_or(0)
}

fn json_array_len(value: Option<&Value>) -> usize {
    value
        .and_then(Value::as_array)
        .map(|a| a.len())
        .unwrap_or(0)
}

/// Mirror of `mergeSalaryConfigPreserveLogs` in the web/Android clients.
fn merge_salary_plaintext(incoming: &str, existing: &str) -> anyhow::Result<String> {
    let mut incoming_val: Map<String, Value> = serde_json::from_str(incoming)?;
    let existing_val: Map<String, Value> = serde_json::from_str(existing)?;

    if json_array_len(incoming_val.get("paycheckLog")) == 0
        && json_array_len(existing_val.get("paycheckLog")) > 0
    {
        if let Some(logs) = existing_val.get("paycheckLog") {
            incoming_val.insert("paycheckLog".into(), logs.clone());
        }
    }

    let incoming_id = incoming_val
        .get("id")
        .and_then(Value::as_str)
        .unwrap_or("")
        .trim();
    if incoming_id.is_empty() {
        if let Some(id) = existing_val.get("id") {
            incoming_val.insert("id".into(), id.clone());
        }
    }

    if json_array_len(incoming_val.get("payRateHistory")) == 0
        && json_array_len(existing_val.get("payRateHistory")) > 0
    {
        if let Some(history) = existing_val.get("payRateHistory") {
            incoming_val.insert("payRateHistory".into(), history.clone());
        }
    }

    Ok(serde_json::to_string(&incoming_val)?)
}

struct DecryptedSalary {
    updated_at: i64,
    plaintext: String,
    event: Event,
}

fn decrypt_salary_event(keys: &Keys, event: Event) -> anyhow::Result<DecryptedSalary> {
    let plaintext = decrypt_from_self(keys, &event.content)?;
    Ok(DecryptedSalary {
        updated_at: salary_updated_at_ms(&plaintext),
        plaintext,
        event,
    })
}

/// Merge multiple salary events into one `AppDataRecord` (newest metadata, merged JSON).
pub fn merge_salary_events_to_record(
    keys: &Keys,
    events: Vec<Event>,
) -> anyhow::Result<Option<AppDataRecord>> {
    if events.is_empty() {
        return Ok(None);
    }

    let mut decrypted: Vec<DecryptedSalary> = Vec::new();
    for event in events {
        match decrypt_salary_event(keys, event) {
            Ok(entry) => decrypted.push(entry),
            Err(err) => tracing::warn!(?err, "skipping undecryptable salary event"),
        }
    }

    if decrypted.is_empty() {
        return Ok(None);
    }

    decrypted.sort_by_key(|entry| entry.updated_at);
    let mut merged_plaintext = decrypted[0].plaintext.clone();
    for entry in decrypted.iter().skip(1) {
        merged_plaintext = merge_salary_plaintext(&entry.plaintext, &merged_plaintext)?;
    }

    let template = decrypted
        .into_iter()
        .max_by_key(|entry| entry.event.created_at)
        .expect("non-empty");
    let ciphertext = encrypt_to_self(keys, &merged_plaintext)?;

    Ok(Some(AppDataRecord {
        event_id: template.event.id.to_string(),
        d_tag: Some(SALARY_D_TAG.to_string()),
        ciphertext,
        plaintext: Some(merged_plaintext),
        decrypt_error: None,
        tags: template
            .event
            .tags
            .iter()
            .map(|t| t.clone().to_vec())
            .collect(),
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use nostr::{EventBuilder, Kind, Tag};

    #[test]
    fn preserves_logs_from_older_copy_when_newer_is_empty() {
        let older = r#"{"id":"abc","updatedAt":100,"paycheckLog":[{"id":"1","payDate":1000,"grossPay":1000,"netPay":800}]}"#;
        let newer = r#"{"id":"abc","updatedAt":200,"hourlyRate":25,"paycheckLog":[]}"#;
        let merged = merge_salary_plaintext(newer, older).expect("merge");
        let value: Value = serde_json::from_str(&merged).expect("parse");
        let logs = value
            .get("paycheckLog")
            .and_then(Value::as_array)
            .expect("logs");
        assert_eq!(logs.len(), 1);
        assert_eq!(value.get("hourlyRate").and_then(Value::as_f64), Some(25.0));
    }

    #[test]
    fn merge_events_picks_newest_metadata() {
        let keys = Keys::generate();
        let older_plain = r#"{"updatedAt":100,"paycheckLog":[{"id":"1","payDate":1000,"grossPay":1,"netPay":1}]}"#;
        let newer_plain = r#"{"updatedAt":200,"hourlyRate":30,"paycheckLog":[]}"#;
        let older = EventBuilder::new(
            Kind::Custom(30078),
            encrypt_to_self(&keys, older_plain).expect("encrypt"),
        )
        .tag(Tag::identifier(SALARY_D_TAG))
        .sign_with_keys(&keys)
        .expect("sign");
        let newer = EventBuilder::new(
            Kind::Custom(30078),
            encrypt_to_self(&keys, newer_plain).expect("encrypt"),
        )
        .tag(Tag::identifier(SALARY_D_TAG))
        .sign_with_keys(&keys)
        .expect("sign");

        let record = merge_salary_events_to_record(&keys, vec![older, newer])
            .expect("merge events")
            .expect("record");
        let merged: Value = serde_json::from_str(record.plaintext.as_ref().unwrap()).expect("json");
        assert_eq!(
            merged.get("hourlyRate").and_then(Value::as_f64),
            Some(30.0)
        );
        assert_eq!(
            merged
                .get("paycheckLog")
                .and_then(Value::as_array)
                .map(|a| a.len()),
            Some(1)
        );
    }
}
