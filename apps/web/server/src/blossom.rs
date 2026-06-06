//! Blossom (BUD-01) proxy helpers — signs kind-24242 auth events with the unlocked key
//! and forwards upload/download to the Blossom URL from `fiatlife/settings/app`.

use axum::{
    body::Body,
    http::{header, StatusCode},
    response::Response,
};
use base64::{engine::general_purpose::STANDARD, Engine as _};
use nostr::{EventBuilder, JsonUtil, Keys, Kind, Tag};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::error::{AppError, AppResult};
use crate::nostr_support::fetch_decrypted_app_data;
use crate::relay_raw::RelayConnectOptions;

const KIND_BLOSSOM_AUTH: u16 = 24242;
pub const APP_SETTINGS_D_TAG: &str = "fiatlife/settings/app";

#[derive(Serialize)]
pub struct BlossomStatusResponse {
    pub configured: bool,
    pub url: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct BlobDescriptor {
    pub url: String,
    pub sha256: String,
    pub size: u64,
    #[serde(rename = "type")]
    pub mime_type: String,
    pub uploaded: u64,
}

fn normalize_blossom_url(url: &str) -> String {
    url.trim().trim_end_matches('/').to_string()
}

fn blossom_url_from_settings_json(plain: &str) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(plain).ok()?;
    let raw = value
        .get("blossomUrl")
        .or_else(|| value.get("blossom_url"))
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .trim();
    if raw.is_empty() {
        None
    } else {
        Some(normalize_blossom_url(raw))
    }
}

pub async fn resolve_blossom_url(
    keys: &Keys,
    relay_urls: &[String],
    relay_opts: impl Fn(&str) -> RelayConnectOptions,
) -> AppResult<Option<String>> {
    let records = fetch_decrypted_app_data(keys, relay_urls, relay_opts)
    .await
    .map_err(AppError::Internal)?;

    for record in records {
        if record.d_tag.as_deref() != Some(APP_SETTINGS_D_TAG) {
            continue;
        }
        if let Some(plain) = record.plaintext {
            if let Some(url) = blossom_url_from_settings_json(&plain) {
                return Ok(Some(url));
            }
        }
    }
    Ok(None)
}

fn create_blossom_auth(keys: &Keys, action: &str, sha256: Option<&str>) -> AppResult<String> {
    let expiration = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| AppError::Internal(anyhow::anyhow!("time: {e}")))?
        .as_secs()
        + 300;

    let mut builder = EventBuilder::new(
        Kind::Custom(KIND_BLOSSOM_AUTH),
        format!("Authorize {action}"),
    )
    .tag(
        Tag::parse(["t", action])
            .map_err(|e| AppError::Internal(anyhow::anyhow!("tag t: {e}")))?,
    )
    .tag(
        Tag::parse(["expiration", &expiration.to_string()])
            .map_err(|e| AppError::Internal(anyhow::anyhow!("tag expiration: {e}")))?,
    );

    if let Some(hash) = sha256 {
        builder = builder.tag(
            Tag::parse(["x", hash])
                .map_err(|e| AppError::Internal(anyhow::anyhow!("tag x: {e}")))?,
        );
    }

    let event = builder
        .sign_with_keys(keys)
        .map_err(|e| AppError::Internal(anyhow::anyhow!("sign blossom auth: {e}")))?;
    let json = event.as_json();
    Ok(STANDARD.encode(json.as_bytes()))
}

fn sha256_hex(data: &[u8]) -> String {
    let digest = Sha256::digest(data);
    hex::encode(digest)
}

pub async fn upload_blob(
    blossom_url: &str,
    keys: &Keys,
    data: Vec<u8>,
    content_type: &str,
    filename: &str,
) -> AppResult<BlobDescriptor> {
    let sha256 = sha256_hex(&data);
    let auth = create_blossom_auth(keys, "upload", Some(&sha256))?;

    let client = Client::new();
    let response = client
        .put(format!("{blossom_url}/upload"))
        .header("Authorization", format!("Nostr {auth}"))
        .header(header::CONTENT_TYPE, content_type)
        .header("X-Filename", filename)
        .body(data)
        .send()
        .await
        .map_err(|e| AppError::BadRequest(format!("blossom upload failed: {e}")))?;

    if !response.status().is_success() {
        let body = response.text().await.unwrap_or_default();
        return Err(AppError::BadRequest(format!(
            "blossom upload failed: {body}"
        )));
    }

    response
        .json()
        .await
        .map_err(|e| AppError::BadRequest(format!("blossom response: {e}")))
}

pub async fn download_blob(
    blossom_url: &str,
    keys: &Keys,
    sha256: &str,
) -> AppResult<Response> {
    let auth = create_blossom_auth(keys, "get", Some(sha256))?;
    let client = Client::new();
    let response = client
        .get(format!("{blossom_url}/{sha256}"))
        .header("Authorization", format!("Nostr {auth}"))
        .send()
        .await
        .map_err(|e| AppError::BadRequest(format!("blossom download failed: {e}")))?;

    if !response.status().is_success() {
        let body = response.text().await.unwrap_or_default();
        return Err(AppError::BadRequest(format!(
            "blossom download failed: {body}"
        )));
    }

    let content_type = response
        .headers()
        .get(header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("application/octet-stream")
        .to_string();
    let bytes = response
        .bytes()
        .await
        .map_err(|e| AppError::BadRequest(format!("blossom body: {e}")))?;

    Ok(Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, content_type)
        .header(header::CACHE_CONTROL, "private, max-age=3600")
        .body(Body::from(bytes))
        .map_err(|e| AppError::Internal(anyhow::anyhow!("response: {e}")))?)
}
