//! On-disk state. Single JSON file at `$FL_DATA_DIR/state.json` containing:
//!
//! - the sealed nsec blob (`salt`, `nonce`, `ciphertext`, all base64),
//! - the configured relay URL list (if any).
//!
//! Atomically written via temp-file + rename so a crash mid-write can't leave
//! a partial blob behind.

use std::path::Path;

use anyhow::{anyhow, Context};
use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use serde::{Deserialize, Serialize};

use crate::crypto::SealedBlob;

const STATE_VERSION: u32 = 1;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PersistentState {
    pub v: u32,
    pub sealed: Option<SealedRecord>,
    /// Primary relay (legacy). Kept in sync with the first entry of `relay_urls`.
    pub relay_url: Option<String>,
    /// Ordered relay list: first is primary (reads merge all; writes go to every URL).
    #[serde(default)]
    pub relay_urls: Vec<String>,
    /// Cached npub derived once at setup, so /api/auth/status can show it
    /// without holding the secret key. The npub is public, so persisting
    /// it in plaintext is fine.
    pub npub: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SealedRecord {
    pub salt_b64: String,
    pub nonce_b64: String,
    pub ciphertext_b64: String,
}

impl Default for PersistentState {
    fn default() -> Self {
        Self {
            v: STATE_VERSION,
            sealed: None,
            relay_url: None,
            relay_urls: Vec::new(),
            npub: None,
        }
    }
}

impl PersistentState {
    /// Normalize relay list after load (migrate legacy single `relay_url` field).
    pub fn normalize_relays(&mut self) {
        if self.relay_urls.is_empty() {
            if let Some(url) = self.relay_url.clone().filter(|u| !u.is_empty()) {
                self.relay_urls = vec![url];
            }
        } else {
            self.relay_urls = dedupe_relay_urls(std::mem::take(&mut self.relay_urls));
            self.relay_url = self.relay_urls.first().cloned();
        }
    }

    pub fn relay_urls(&self) -> &[String] {
        &self.relay_urls
    }

    pub fn primary_relay_url(&self) -> Option<&str> {
        self.relay_urls.first().map(String::as_str)
    }

    pub fn set_relay_urls(&mut self, urls: Vec<String>) {
        self.relay_urls = dedupe_relay_urls(urls);
        self.relay_url = self.relay_urls.first().cloned();
    }

    pub fn load(path: &Path) -> anyhow::Result<Self> {
        if !path.exists() {
            return Ok(Self::default());
        }
        let bytes = std::fs::read(path).with_context(|| format!("read {}", path.display()))?;
        let mut state: PersistentState =
            serde_json::from_slice(&bytes).with_context(|| format!("parse {}", path.display()))?;
        if state.v != STATE_VERSION {
            return Err(anyhow!(
                "unexpected state.json version {}; expected {}",
                state.v,
                STATE_VERSION
            ));
        }
        state.normalize_relays();
        Ok(state)
    }

    pub fn save(&self, path: &Path) -> anyhow::Result<()> {
        let parent = path
            .parent()
            .ok_or_else(|| anyhow!("state path has no parent"))?;
        std::fs::create_dir_all(parent)?;
        let tmp = path.with_extension("json.tmp");
        let json = serde_json::to_vec_pretty(self)?;
        std::fs::write(&tmp, &json).with_context(|| format!("write {}", tmp.display()))?;
        set_owner_only(&tmp)?;
        std::fs::rename(&tmp, path)
            .with_context(|| format!("rename {} -> {}", tmp.display(), path.display()))?;
        Ok(())
    }
}

pub fn dedupe_relay_urls(urls: Vec<String>) -> Vec<String> {
    let mut out = Vec::new();
    for url in urls {
        let trimmed = url.trim().to_string();
        if trimmed.is_empty() {
            continue;
        }
        if !out.iter().any(|existing| existing == &trimmed) {
            out.push(trimmed);
        }
    }
    out
}

impl SealedRecord {
    pub fn from_blob(blob: &SealedBlob) -> Self {
        Self {
            salt_b64: B64.encode(blob.salt),
            nonce_b64: B64.encode(blob.nonce),
            ciphertext_b64: B64.encode(&blob.ciphertext),
        }
    }

    pub fn to_blob(&self) -> anyhow::Result<SealedBlob> {
        let salt = B64.decode(&self.salt_b64).context("decode salt")?;
        let nonce = B64.decode(&self.nonce_b64).context("decode nonce")?;
        let ciphertext = B64
            .decode(&self.ciphertext_b64)
            .context("decode ciphertext")?;
        if salt.len() != 16 {
            return Err(anyhow!("salt length {} != 16", salt.len()));
        }
        if nonce.len() != 24 {
            return Err(anyhow!("nonce length {} != 24", nonce.len()));
        }
        let mut salt_arr = [0u8; 16];
        salt_arr.copy_from_slice(&salt);
        let mut nonce_arr = [0u8; 24];
        nonce_arr.copy_from_slice(&nonce);
        Ok(SealedBlob {
            salt: salt_arr,
            nonce: nonce_arr,
            ciphertext,
        })
    }
}

#[cfg(unix)]
fn set_owner_only(path: &std::path::Path) -> anyhow::Result<()> {
    use std::os::unix::fs::PermissionsExt;
    let perm = std::fs::Permissions::from_mode(0o600);
    std::fs::set_permissions(path, perm)
        .with_context(|| format!("chmod 0600 {}", path.display()))?;
    Ok(())
}

#[cfg(not(unix))]
fn set_owner_only(_path: &std::path::Path) -> anyhow::Result<()> {
    Ok(())
}
