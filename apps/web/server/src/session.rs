//! In-memory session store. The cookie carries an opaque session id; the
//! server-side map turns that id into the unlocked state for the user.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::{Duration, Instant};

use rand::RngCore;
use tokio::sync::Mutex;
use zeroize::Zeroizing;

use crate::nostr_support::{keys_from_nsec_bytes, KeyIdentity};

/// Cookie name carrying the session id.
pub const SESSION_COOKIE: &str = "fl_sid";

#[derive(Clone)]
pub struct SessionStore {
    inner: Arc<Mutex<Inner>>,
    idle: Duration,
}

struct Inner {
    sessions: HashMap<String, SessionEntry>,
}

struct SessionEntry {
    secret: Zeroizing<Vec<u8>>,
    identity: KeyIdentity,
    last_seen: Instant,
}

impl SessionStore {
    pub fn new(idle: Duration) -> Self {
        Self {
            inner: Arc::new(Mutex::new(Inner {
                sessions: HashMap::new(),
            })),
            idle,
        }
    }

    pub async fn open(&self, secret: Zeroizing<Vec<u8>>, identity: KeyIdentity) -> String {
        let sid = new_sid();
        let mut inner = self.inner.lock().await;
        inner.sessions.insert(
            sid.clone(),
            SessionEntry {
                secret,
                identity,
                last_seen: Instant::now(),
            },
        );
        sid
    }

    pub async fn touch_unlocked(&self, sid: &str) -> bool {
        let mut inner = self.inner.lock().await;
        let now = Instant::now();
        let expired = match inner.sessions.get(sid) {
            Some(s) => now.duration_since(s.last_seen) > self.idle,
            None => return false,
        };
        if expired {
            inner.sessions.remove(sid);
            return false;
        }
        if let Some(s) = inner.sessions.get_mut(sid) {
            s.last_seen = now;
        }
        true
    }

    pub async fn keys_for(
        &self,
        sid: &str,
    ) -> anyhow::Result<Option<(nostr::Keys, KeyIdentity)>> {
        let mut inner = self.inner.lock().await;
        let now = Instant::now();
        let expired = match inner.sessions.get(sid) {
            Some(s) => now.duration_since(s.last_seen) > self.idle,
            None => return Ok(None),
        };
        if expired {
            inner.sessions.remove(sid);
            return Ok(None);
        }
        let Some(entry) = inner.sessions.get_mut(sid) else {
            return Ok(None);
        };
        entry.last_seen = now;
        let keys = keys_from_nsec_bytes(entry.secret.as_slice())?;
        Ok(Some((keys, entry.identity.clone())))
    }

    pub async fn close(&self, sid: &str) {
        let mut inner = self.inner.lock().await;
        inner.sessions.remove(sid);
    }

    pub async fn close_all(&self) {
        let mut inner = self.inner.lock().await;
        inner.sessions.clear();
    }
}

fn new_sid() -> String {
    let mut bytes = [0u8; 32];
    rand::rngs::OsRng.fill_bytes(&mut bytes);
    hex::encode(bytes)
}
