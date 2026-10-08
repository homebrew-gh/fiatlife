//! SimpleFIN Bridge client (balances only).
//!
//! Protocol: <https://www.simplefin.org/protocol.html>. A user-supplied setup
//! token is a base64 claim URL; POSTing to it once returns an access URL with
//! HTTP Basic credentials embedded. That access URL grants read access to the
//! user's bank data, so callers must keep it encrypted at rest and never send
//! it to the browser. Credentials are stripped from every URL we hand to
//! reqwest so they can't surface in error messages or logs.

use std::collections::HashMap;
use std::time::Duration;

use base64::{
    engine::general_purpose::{STANDARD, STANDARD_NO_PAD, URL_SAFE, URL_SAFE_NO_PAD},
    Engine as _,
};
use reqwest::{header::CONTENT_LENGTH, Client, StatusCode, Url};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::error::{AppError, AppResult};

/// Where users create a setup token for FiatLife.
pub const CREATE_TOKEN_URL: &str = "https://bridge.simplefin.org/simplefin/create";

const REQUEST_TIMEOUT: Duration = Duration::from_secs(90);
const MAX_MESSAGE_LEN: usize = 300;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SimpleFinAccount {
    /// Stable link key: `conn_id:id` (account ids are only unique per connection).
    pub key: String,
    pub name: String,
    pub institution: String,
    pub currency: String,
    pub balance: Option<f64>,
    pub available_balance: Option<f64>,
    pub balance_date_ms: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SimpleFinMessage {
    pub code: String,
    pub message: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BalancesSnapshot {
    pub accounts: Vec<SimpleFinAccount>,
    pub messages: Vec<SimpleFinMessage>,
}

fn bad(msg: impl Into<String>) -> AppError {
    AppError::BadRequest(msg.into())
}

fn client() -> AppResult<Client> {
    Client::builder()
        .https_only(true)
        .timeout(REQUEST_TIMEOUT)
        .build()
        .map_err(|e| AppError::Internal(e.into()))
}

/// Decode a pasted setup token into its claim URL.
pub fn claim_url_from_token(token: &str) -> AppResult<Url> {
    let compact: String = token.chars().filter(|c| !c.is_whitespace()).collect();
    if compact.is_empty() {
        return Err(bad("Paste your SimpleFIN setup token."));
    }
    let bytes = [STANDARD, STANDARD_NO_PAD, URL_SAFE, URL_SAFE_NO_PAD]
        .iter()
        .find_map(|engine| engine.decode(&compact).ok())
        .ok_or_else(|| bad("That doesn't look like a SimpleFIN setup token."))?;
    let text = String::from_utf8(bytes)
        .map_err(|_| bad("That doesn't look like a SimpleFIN setup token."))?;
    let url = Url::parse(text.trim())
        .map_err(|_| bad("That doesn't look like a SimpleFIN setup token."))?;
    if url.scheme() != "https" {
        return Err(bad("SimpleFIN setup token must point to an https:// URL."));
    }
    Ok(url)
}

/// Exchange a claim URL for an access URL. Each setup token works only once.
pub async fn claim_access_url(claim_url: Url) -> AppResult<String> {
    let response = client()?
        .post(claim_url)
        .header(CONTENT_LENGTH, "0")
        .send()
        .await
        .map_err(|e| bad(format!("Could not reach SimpleFIN: {}", e.without_url())))?;
    match response.status() {
        s if s.is_success() => {}
        StatusCode::FORBIDDEN => {
            return Err(bad(
                "SimpleFIN rejected this setup token: it was already used or doesn't exist. \
                 If you didn't use it yourself, someone else may have claimed it — disable it \
                 in SimpleFIN Bridge, then create a new token.",
            ))
        }
        s => return Err(bad(format!("SimpleFIN token claim failed ({s})."))),
    }
    let body = response
        .text()
        .await
        .map_err(|e| bad(format!("Could not read SimpleFIN response: {}", e.without_url())))?;
    let access_url = body.trim().to_string();
    parse_access_url(&access_url)?;
    Ok(access_url)
}

/// Fetch current balances for every account behind the access URL.
pub async fn fetch_balances(access_url: &str) -> AppResult<BalancesSnapshot> {
    let access = parse_access_url(access_url)?;
    let mut url = Url::parse(&format!(
        "{}/accounts",
        access.base.as_str().trim_end_matches('/')
    ))
    .map_err(|e| AppError::Internal(e.into()))?;
    url.query_pairs_mut()
        .append_pair("version", "2")
        .append_pair("balances-only", "1");

    let response = client()?
        .get(url)
        .basic_auth(&access.username, Some(&access.password))
        .send()
        .await
        .map_err(|e| bad(format!("Could not reach SimpleFIN: {}", e.without_url())))?;
    match response.status() {
        s if s.is_success() => {}
        StatusCode::PAYMENT_REQUIRED => {
            return Err(bad(
                "SimpleFIN says payment is required. Check your SimpleFIN Bridge subscription.",
            ))
        }
        StatusCode::FORBIDDEN => {
            return Err(bad(
                "SimpleFIN denied access. The connection may have been disabled in SimpleFIN \
                 Bridge — disconnect here and connect again with a new setup token.",
            ))
        }
        s => return Err(bad(format!("SimpleFIN balance fetch failed ({s})."))),
    }
    let set: AccountSetWire = response
        .json()
        .await
        .map_err(|e| bad(format!("Unexpected SimpleFIN response: {}", e.without_url())))?;
    Ok(snapshot_from_wire(set))
}

struct AccessUrl {
    base: Url,
    username: String,
    password: String,
}

fn parse_access_url(raw: &str) -> AppResult<AccessUrl> {
    let invalid = || bad("SimpleFIN returned an invalid access URL.");
    let mut url = Url::parse(raw).map_err(|_| invalid())?;
    if url.scheme() != "https" {
        return Err(invalid());
    }
    let username = percent_decode(url.username());
    let password = url.password().map(percent_decode).unwrap_or_default();
    if username.is_empty() {
        return Err(invalid());
    }
    url.set_username("").map_err(|_| invalid())?;
    url.set_password(None).map_err(|_| invalid())?;
    Ok(AccessUrl {
        base: url,
        username,
        password,
    })
}

fn percent_decode(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let (Some(hi), Some(lo)) = (hex_value(bytes[i + 1]), hex_value(bytes[i + 2])) {
                out.push(hi << 4 | lo);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn hex_value(b: u8) -> Option<u8> {
    (b as char).to_digit(16).map(|d| d as u8)
}

#[derive(Deserialize)]
struct AccountSetWire {
    #[serde(default)]
    errlist: Vec<ErrorWire>,
    /// Deprecated in protocol v2 but still sent by some servers.
    #[serde(default)]
    errors: Vec<Value>,
    #[serde(default)]
    connections: Vec<ConnectionWire>,
    #[serde(default)]
    accounts: Vec<AccountWire>,
}

#[derive(Deserialize)]
struct ErrorWire {
    #[serde(default)]
    code: String,
    #[serde(default)]
    msg: String,
}

#[derive(Deserialize)]
struct ConnectionWire {
    conn_id: String,
    #[serde(default)]
    name: String,
    #[serde(default)]
    org_name: Option<String>,
}

#[derive(Deserialize)]
struct OrgWire {
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    domain: Option<String>,
}

#[derive(Deserialize)]
struct AccountWire {
    id: String,
    #[serde(default)]
    name: String,
    #[serde(default)]
    conn_id: Option<String>,
    #[serde(default)]
    conn_name: Option<String>,
    /// Protocol v1 only.
    #[serde(default)]
    org: Option<OrgWire>,
    #[serde(default)]
    currency: String,
    #[serde(default)]
    balance: Value,
    #[serde(rename = "available-balance", default)]
    available_balance: Value,
    #[serde(rename = "balance-date", default)]
    balance_date: Value,
}

fn number(value: &Value) -> Option<f64> {
    match value {
        Value::Number(n) => n.as_f64(),
        Value::String(s) => s.trim().parse::<f64>().ok(),
        _ => None,
    }
    .filter(|n| n.is_finite())
}

/// Server-provided text is untrusted: drop control characters and cap length.
fn sanitize(text: &str) -> String {
    let cleaned: String = text
        .chars()
        .filter(|c| !c.is_control())
        .take(MAX_MESSAGE_LEN)
        .collect();
    cleaned.trim().to_string()
}

fn snapshot_from_wire(set: AccountSetWire) -> BalancesSnapshot {
    let connections: HashMap<&str, &ConnectionWire> = set
        .connections
        .iter()
        .map(|c| (c.conn_id.as_str(), c))
        .collect();

    let accounts = set
        .accounts
        .iter()
        .filter(|a| !a.id.trim().is_empty())
        .map(|a| {
            let connection = a.conn_id.as_deref().and_then(|id| connections.get(id));
            let institution = a
                .conn_name
                .clone()
                .or_else(|| connection.map(|c| c.name.clone()))
                .or_else(|| connection.and_then(|c| c.org_name.clone()))
                .or_else(|| a.org.as_ref().and_then(|o| o.name.clone().or(o.domain.clone())))
                .unwrap_or_default();
            let key = match a.conn_id.as_deref().filter(|c| !c.is_empty()) {
                Some(conn) => format!("{conn}:{}", a.id),
                None => a.id.clone(),
            };
            SimpleFinAccount {
                key,
                name: sanitize(&a.name),
                institution: sanitize(&institution),
                currency: sanitize(&a.currency),
                balance: number(&a.balance),
                available_balance: number(&a.available_balance),
                balance_date_ms: number(&a.balance_date).map(|secs| (secs * 1000.0) as i64),
            }
        })
        .collect();

    let mut messages: Vec<SimpleFinMessage> = set
        .errlist
        .iter()
        .map(|e| SimpleFinMessage {
            code: sanitize(&e.code),
            message: sanitize(&e.msg),
        })
        .collect();
    if messages.is_empty() {
        messages = set
            .errors
            .iter()
            .filter_map(|v| v.as_str())
            .map(|msg| SimpleFinMessage {
                code: "gen.".into(),
                message: sanitize(msg),
            })
            .collect();
    }
    messages.retain(|m| !m.message.is_empty());

    BalancesSnapshot { accounts, messages }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn snapshot_survives_encrypted_round_trip() {
        use crate::nostr_support::{decrypt_from_self, encrypt_to_self};
        let keys = nostr::Keys::generate();
        let snapshot = BalancesSnapshot {
            accounts: vec![SimpleFinAccount {
                key: "CON-1:ACT-1".into(),
                name: "Visa".into(),
                institution: "Bank".into(),
                currency: "USD".into(),
                balance: Some(-1234.5),
                available_balance: None,
                balance_date_ms: Some(1_791_152_704_000),
            }],
            messages: vec![SimpleFinMessage {
                code: "con.auth".into(),
                message: "Reauthenticate Fidelity".into(),
            }],
        };
        let json = serde_json::to_string(&snapshot).unwrap();
        let ciphertext = encrypt_to_self(&keys, &json).unwrap();
        assert!(!ciphertext.contains("Visa"));
        let restored: BalancesSnapshot =
            serde_json::from_str(&decrypt_from_self(&keys, &ciphertext).unwrap()).unwrap();
        assert_eq!(restored.accounts[0].key, "CON-1:ACT-1");
        assert_eq!(restored.accounts[0].balance, Some(-1234.5));
        assert_eq!(restored.messages[0].message, "Reauthenticate Fidelity");
    }

    #[test]
    fn decodes_demo_setup_token() {
        let url = claim_url_from_token(
            " aHR0cHM6Ly9icmlkZ2Uuc2ltcGxlZmluLm9yZy9zaW1wbGVmaW4vY2xhaW0vZGVtbw==\n",
        )
        .unwrap();
        assert_eq!(url.as_str(), "https://bridge.simplefin.org/simplefin/claim/demo");
    }

    #[test]
    fn rejects_non_https_token() {
        let token = STANDARD.encode("http://example.com/claim/x");
        assert!(claim_url_from_token(&token).is_err());
    }

    #[test]
    fn strips_credentials_from_access_url() {
        let access = parse_access_url("https://user%40x:p%3Ass@bridge.example.com/simplefin")
            .unwrap();
        assert_eq!(access.username, "user@x");
        assert_eq!(access.password, "p:ss");
        assert_eq!(access.base.as_str(), "https://bridge.example.com/simplefin");
    }

    #[test]
    fn parses_v2_account_set() {
        let json = serde_json::json!({
            "errlist": [{"code": "con.auth", "msg": "Authentication failed\u{0007} for My Bank", "conn_id": "CON-1"}],
            "connections": [{"conn_id": "CON-1", "name": "My Bank", "org_id": "ORG", "sfin_url": "https://x"}],
            "accounts": [{
                "id": "A1", "name": "Checking", "conn_id": "CON-1", "currency": "USD",
                "balance": "100.23", "available-balance": "75.23", "balance-date": 978366153
            }]
        });
        let set: AccountSetWire = serde_json::from_value(json).unwrap();
        let snap = snapshot_from_wire(set);
        assert_eq!(snap.accounts.len(), 1);
        let a = &snap.accounts[0];
        assert_eq!(a.key, "CON-1:A1");
        assert_eq!(a.institution, "My Bank");
        assert_eq!(a.balance, Some(100.23));
        assert_eq!(a.available_balance, Some(75.23));
        assert_eq!(a.balance_date_ms, Some(978_366_153_000));
        assert_eq!(snap.messages[0].message, "Authentication failed for My Bank");
    }
}
