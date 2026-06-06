//! Nostr key, NIP-44, and relay helpers for the web backend.
//!
//! The Linux app already uses `nostr`/`nostr-sdk`; this module mirrors those
//! patterns so the Start9 package speaks the same NIP-42/NIP-44/kind-30078
//! dialect as the desktop client.

use std::collections::HashMap;
use std::time::Duration;

use anyhow::{anyhow, Context};
use nostr::message::RelayMessage;
use nostr::nips::nip44;
use nostr::{Event, EventBuilder, Filter, Keys, Kind, Tag, ToBech32};
use nostr_sdk::Client;
use serde::Serialize;

use crate::config::resolve_relay_url;
use crate::relay_raw::{self, RelayConnectOptions};

pub const KIND_APP_DATA: u16 = 30078;
pub const KIND_CYPHERLOG_SUBSCRIPTION: u16 = 37004;
const KIND_EVENT_DELETION: u16 = 5;

const RELAY_CONNECT_TIMEOUT: Duration = Duration::from_secs(30);
const RELAY_AUTH_SETTLE: Duration = Duration::from_secs(3);

#[derive(Debug, Clone, Serialize)]
pub struct KeyIdentity {
    pub npub: String,
    pub public_key_hex: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct AppDataRecord {
    pub event_id: String,
    pub d_tag: Option<String>,
    pub ciphertext: String,
    pub plaintext: Option<String>,
    pub decrypt_error: Option<String>,
    /// Event tags (used for CypherLog `subscription:` records published tag-only).
    pub tags: Vec<Vec<String>>,
}

#[derive(Debug, Clone, Serialize)]
pub struct CypherLogSubscriptionRecord {
    pub event_id: String,
    pub d_tag: String,
    pub created_at: u64,
    pub tags: Vec<Vec<String>>,
    pub content: String,
    pub plaintext: Option<String>,
    pub decrypt_error: Option<String>,
}

pub fn parse_nsec(nsec: &str) -> anyhow::Result<(Keys, KeyIdentity)> {
    let nsec = nsec.trim();
    if !nsec.starts_with("nsec1") {
        return Err(anyhow!("secret key must be an nsec1... string"));
    }
    let keys = Keys::parse(nsec).map_err(|e| anyhow!("invalid nsec: {e}"))?;
    let public_key = keys.public_key();
    let identity = KeyIdentity {
        npub: public_key
            .to_bech32()
            .map_err(|e| anyhow!("encode npub: {e}"))?,
        public_key_hex: public_key.to_string(),
    };
    Ok((keys, identity))
}

pub fn keys_from_nsec_bytes(secret: &[u8]) -> anyhow::Result<Keys> {
    let nsec = std::str::from_utf8(secret).context("session secret is not utf-8")?;
    let (keys, _) = parse_nsec(nsec)?;
    Ok(keys)
}

pub fn encrypt_to_self(keys: &Keys, plaintext: &str) -> anyhow::Result<String> {
    nip44::encrypt(
        keys.secret_key(),
        &keys.public_key(),
        plaintext,
        nip44::Version::V2,
    )
    .map_err(|e| anyhow!("nip44 encrypt: {e}"))
}

pub fn decrypt_from_self(keys: &Keys, ciphertext: &str) -> anyhow::Result<String> {
    nip44::decrypt(keys.secret_key(), &keys.public_key(), ciphertext)
        .map_err(|e| anyhow!("nip44 decrypt: {e}"))
}

fn looks_like_json(content: &str) -> bool {
    let trimmed = content.trim();
    trimmed.starts_with('{') || trimmed.starts_with('[')
}

fn decrypt_subscription_content(
    keys: &Keys,
    content: &str,
) -> (Option<String>, Option<String>) {
    let trimmed = content.trim();
    if trimmed.is_empty() {
        return (None, None);
    }
    if looks_like_json(trimmed) {
        return (Some(trimmed.to_string()), None);
    }
    match decrypt_from_self(keys, trimmed) {
        Ok(plain) => (Some(plain), None),
        Err(err) => (None, Some(err.to_string())),
    }
}

pub async fn fetch_kind_events(
    keys: &Keys,
    relay_url: &str,
    kind: u16,
    opts: RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    let relay_url = resolve_relay_url(relay_url);
    if opts.insecure_tls && relay_url.starts_with("wss://") {
        let filter = Filter::new()
            .author(keys.public_key())
            .kind(Kind::Custom(kind))
            .limit(200);
        return relay_raw::fetch_events(keys, &relay_url, filter, opts).await;
    }

    let client = prepare_relay_client(keys, &relay_url).await?;

    let filter = Filter::new()
        .author(keys.public_key())
        .kind(Kind::Custom(kind))
        .limit(200);

    let sub_output = client.subscribe_to([&relay_url], filter, None).await?;
    let sub_id = sub_output.val;
    let mut notifications = client.notifications();
    let mut events = Vec::new();
    let deadline = tokio::time::Instant::now() + Duration::from_secs(25);

    while tokio::time::Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        let Ok(notification) = tokio::time::timeout(remaining, notifications.recv()).await else {
            break;
        };
        match notification {
            Ok(nostr_sdk::RelayPoolNotification::Event {
                subscription_id,
                event,
                ..
            }) if subscription_id == sub_id => events.push(*event),
            Ok(nostr_sdk::RelayPoolNotification::Message { message, .. }) => {
                if let RelayMessage::EndOfStoredEvents(eose_id) = &message {
                    if eose_id.as_ref() == &sub_id {
                        break;
                    }
                }
            }
            Ok(nostr_sdk::RelayPoolNotification::Shutdown) => break,
            Err(_) => break,
            _ => {}
        }
    }

    client.unsubscribe(&sub_id).await;
    client.shutdown().await;
    Ok(events)
}

pub async fn fetch_app_data_events(
    keys: &Keys,
    relay_url: &str,
    opts: RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    fetch_kind_events(keys, relay_url, KIND_APP_DATA, opts).await
}

pub async fn fetch_cypherlog_subscription_events(
    keys: &Keys,
    relay_url: &str,
    opts: RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    fetch_kind_events(keys, relay_url, KIND_CYPHERLOG_SUBSCRIPTION, opts).await
}

/// Fetch from every relay and merge kind-30078 replaceable events by `#d` tag
/// (newest `created_at` wins — matches Android read-primary / multi-write semantics).
pub async fn fetch_app_data_events_from_relays(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let mut all_events = Vec::new();
    let mut errors = Vec::new();
    for relay_url in relay_urls {
        let connect_url = resolve_relay_url(relay_url);
        if connect_url != *relay_url {
            tracing::info!(
                configured = %relay_url,
                resolved = %connect_url,
                "relay host rewritten for StartOS container DNS"
            );
        }
        let opts = relay_opts(relay_url);
        match fetch_app_data_events(keys, relay_url, opts).await {
            Ok(events) => {
                tracing::debug!(%connect_url, count = events.len(), "fetched app-data events");
                all_events.extend(events);
            }
            Err(err) => {
                tracing::warn!(configured = %relay_url, resolved = %connect_url, ?err, "relay fetch failed");
                errors.push(format!("{connect_url}: {err}"));
            }
        }
    }

    if all_events.is_empty() && !errors.is_empty() {
        return Err(anyhow!("all relay fetches failed: {}", errors.join("; ")));
    }

    Ok(merge_replaceable_app_data_events(all_events))
}

pub async fn fetch_cypherlog_subscriptions_from_relays(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let mut all_events = Vec::new();
    let mut errors = Vec::new();
    for relay_url in relay_urls {
        let connect_url = resolve_relay_url(relay_url);
        let opts = relay_opts(relay_url);
        match fetch_cypherlog_subscription_events(keys, relay_url, opts).await {
            Ok(events) => {
                tracing::debug!(%connect_url, count = events.len(), "fetched CypherLog 37004 events");
                all_events.extend(events);
            }
            Err(err) => {
                tracing::warn!(configured = %relay_url, resolved = %connect_url, ?err, "CypherLog 37004 fetch failed");
                errors.push(format!("{connect_url}: {err}"));
            }
        }
    }

    if all_events.is_empty() && !errors.is_empty() {
        return Err(anyhow!(
            "all CypherLog subscription fetches failed: {}",
            errors.join("; ")
        ));
    }

    Ok(merge_replaceable_app_data_events(all_events))
}

pub async fn fetch_cypherlog_subscription_records(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<Vec<CypherLogSubscriptionRecord>> {
    let events = fetch_cypherlog_subscriptions_from_relays(keys, relay_urls, relay_opts).await?;
    Ok(events
        .into_iter()
        .filter_map(|event| {
            let d_tag = event.tags.identifier()?.trim().to_string();
            if d_tag.is_empty() {
                return None;
            }
            let (plaintext, decrypt_error) = {
                let (plain, err) = decrypt_subscription_content(keys, &event.content);
                (plain, err)
            };
            Some(CypherLogSubscriptionRecord {
                event_id: event.id.to_string(),
                d_tag,
                created_at: event.created_at.as_secs(),
                tags: event.tags.iter().map(|t| t.clone().to_vec()).collect(),
                content: event.content,
                plaintext,
                decrypt_error,
            })
        })
        .collect())
}

/// For each `#d` identifier, keep the newest event (NIP-33 replaceable semantics).
pub fn merge_replaceable_app_data_events(events: Vec<Event>) -> Vec<Event> {
    let mut by_d_tag: HashMap<String, Event> = HashMap::new();
    let mut without_d_tag = Vec::new();

    for event in events {
        let Some(d_tag) = event.tags.identifier().map(str::to_string) else {
            without_d_tag.push(event);
            continue;
        };
        match by_d_tag.get(&d_tag) {
            None => {
                by_d_tag.insert(d_tag, event);
            }
            Some(existing) if event.created_at > existing.created_at => {
                by_d_tag.insert(d_tag, event);
            }
            _ => {}
        }
    }

    let mut merged: Vec<Event> = by_d_tag.into_values().collect();
    merged.extend(without_d_tag);
    merged
}

async fn send_signed_event(
    keys: &Keys,
    relay_url: &str,
    event: &Event,
    opts: RelayConnectOptions,
) -> anyhow::Result<()> {
    let relay_url = resolve_relay_url(relay_url);
    if opts.insecure_tls && relay_url.starts_with("wss://") {
        relay_raw::send_event(keys, &relay_url, event, opts).await?;
        return Ok(());
    }

    let client = prepare_relay_client(keys, &relay_url).await?;
    client.send_event(event).await?;
    tokio::time::sleep(Duration::from_millis(300)).await;
    client.shutdown().await;
    Ok(())
}

async fn prepare_relay_client(keys: &Keys, relay_url: &str) -> anyhow::Result<Client> {
    let client = Client::new(keys.clone());
    client.add_relay(relay_url).await?;
    client
        .try_connect_relay(relay_url, RELAY_CONNECT_TIMEOUT)
        .await?;
    tokio::time::sleep(RELAY_AUTH_SETTLE).await;
    Ok(client)
}

pub async fn fetch_decrypted_app_data(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<Vec<AppDataRecord>> {
    let events = fetch_app_data_events_from_relays(keys, relay_urls, relay_opts).await?;
    Ok(events
        .into_iter()
        .map(|event| {
            let decrypted = decrypt_from_self(keys, &event.content);
            let (plaintext, decrypt_error) = match decrypted {
                Ok(plain) => (Some(plain), None),
                Err(err) => (None, Some(err.to_string())),
            };
            AppDataRecord {
                event_id: event.id.to_string(),
                d_tag: event.tags.identifier().map(str::to_owned),
                ciphertext: event.content,
                plaintext,
                decrypt_error,
                tags: event
                    .tags
                    .iter()
                    .map(|t| t.clone().to_vec())
                    .collect(),
            }
        })
        .collect())
}


pub async fn publish_app_data_plaintext(
    keys: &Keys,
    relay_urls: &[String],
    d_tag: &str,
    plaintext: &str,
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<String> {
    if d_tag.trim().is_empty() {
        return Err(anyhow!("d_tag cannot be empty"));
    }
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let ciphertext = encrypt_to_self(keys, plaintext)?;
    let event = EventBuilder::new(Kind::Custom(KIND_APP_DATA), ciphertext)
        .tag(Tag::identifier(d_tag))
        .sign_with_keys(keys)
        .map_err(|e| anyhow!("sign event: {e}"))?;
    let event_id = event.id.to_string();

    let mut errors = Vec::new();
    let mut any_ok = false;
    for relay_url in relay_urls {
        let opts = relay_opts(relay_url);
        match send_signed_event(keys, relay_url, &event, opts).await {
            Ok(()) => {
                tracing::debug!(%relay_url, %d_tag, "published app-data event");
                any_ok = true;
            }
            Err(err) => {
                tracing::warn!(%relay_url, %d_tag, ?err, "relay publish failed");
                errors.push(format!("{relay_url}: {err}"));
            }
        }
    }

    if !any_ok {
        return Err(anyhow!(
            "failed to publish to any relay: {}",
            errors.join("; ")
        ));
    }

    Ok(event_id)
}

/// Publish a tag-based kind-37004 CypherLog subscription event.
/// Content is empty; bill fields live in event tags — matches Android
/// `publishReplaceable37004Detailed`.
pub async fn publish_cypherlog_subscription_tags(
    keys: &Keys,
    relay_urls: &[String],
    tags: &[Vec<String>],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<String> {
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }
    if tags.is_empty() {
        return Err(anyhow!("tags cannot be empty"));
    }

    let mut builder = EventBuilder::new(Kind::Custom(KIND_CYPHERLOG_SUBSCRIPTION), "");
    for parts in tags {
        if parts.len() < 2 {
            continue;
        }
        let tag_parts: Vec<String> = parts.iter().map(|s| s.to_string()).collect();
        builder = builder.tag(
            Tag::parse(tag_parts).map_err(|e| anyhow!("invalid tag: {e}"))?,
        );
    }

    let event = builder
        .sign_with_keys(keys)
        .map_err(|e| anyhow!("sign event: {e}"))?;
    let event_id = event.id.to_string();

    let mut errors = Vec::new();
    let mut any_ok = false;
    for relay_url in relay_urls {
        let opts = relay_opts(relay_url);
        match send_signed_event(keys, relay_url, &event, opts).await {
            Ok(()) => {
                tracing::debug!(%relay_url, "published CypherLog kind-37004 subscription");
                any_ok = true;
            }
            Err(err) => {
                tracing::warn!(%relay_url, ?err, "CypherLog subscription publish failed");
                errors.push(format!("{relay_url}: {err}"));
            }
        }
    }

    if !any_ok {
        return Err(anyhow!(
            "failed to publish subscription to any relay: {}",
            errors.join("; ")
        ));
    }

    Ok(event_id)
}

/// NIP-09 deletion for addressable events (`kind:pubkey:d-tag`).
pub async fn publish_addressable_deletion(
    keys: &Keys,
    relay_urls: &[String],
    kind: u16,
    d_tag: &str,
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> anyhow::Result<String> {
    if d_tag.trim().is_empty() {
        return Err(anyhow!("d_tag cannot be empty"));
    }
    if relay_urls.is_empty() {
        return Err(anyhow!("no relay urls configured"));
    }

    let a_tag = format!("{kind}:{}:{}", keys.public_key(), d_tag.trim());
    let event = EventBuilder::new(Kind::Custom(KIND_EVENT_DELETION), "")
        .tag(
            Tag::parse(vec!["a".to_string(), a_tag])
                .map_err(|e| anyhow!("invalid deletion tag: {e}"))?,
        )
        .sign_with_keys(keys)
        .map_err(|e| anyhow!("sign deletion: {e}"))?;
    let event_id = event.id.to_string();

    let mut errors = Vec::new();
    let mut any_ok = false;
    for relay_url in relay_urls {
        let opts = relay_opts(relay_url);
        match send_signed_event(keys, relay_url, &event, opts).await {
            Ok(()) => {
                tracing::debug!(%relay_url, %d_tag, kind, "published NIP-09 deletion");
                any_ok = true;
            }
            Err(err) => {
                tracing::warn!(%relay_url, %d_tag, kind, ?err, "deletion publish failed");
                errors.push(format!("{relay_url}: {err}"));
            }
        }
    }

    if !any_ok {
        return Err(anyhow!(
            "failed to publish deletion to any relay: {}",
            errors.join("; ")
        ));
    }

    Ok(event_id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nip44_roundtrip_to_self() {
        let keys = Keys::generate();
        let plaintext = r#"{"v":1,"items":[]}"#;

        let ciphertext = encrypt_to_self(&keys, plaintext).expect("encrypt");
        assert_ne!(ciphertext, plaintext);

        let decrypted = decrypt_from_self(&keys, &ciphertext).expect("decrypt");
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn parse_nsec_rejects_non_nsec_input() {
        let err = parse_nsec("not-a-secret").expect_err("invalid secret");
        assert!(err.to_string().contains("nsec1"));
    }

    #[test]
    fn merge_replaceable_dedupes_same_d_tag() {
        let keys = Keys::generate();
        let event = EventBuilder::new(Kind::Custom(KIND_APP_DATA), "payload")
            .tag(Tag::identifier("io.nomoxcel.utxo.wallets"))
            .sign_with_keys(&keys)
            .expect("sign");
        let merged = merge_replaceable_app_data_events(vec![event.clone(), event]);
        assert_eq!(merged.len(), 1);
    }
}
