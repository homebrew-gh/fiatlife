//! Background relay-publish outbox.
//!
//! Handlers sign events synchronously (cheap, local) and hand the signed event
//! to the outbox, which performs the slow relay round-trip in a detached task
//! with retry + backoff. This lets the HTTP request return immediately so the
//! UI can update optimistically while delivery happens in the background.
//!
//! Note: NIP-42 AUTH means sending needs the user's keys, so a queued send can
//! only run while the keys are in memory. The outbox therefore keeps an
//! in-memory cloned `Keys` for the lifetime of each pending/failed item (the
//! same trust model as the unlocked session). Pending sends do not survive a
//! server restart; the client reconciles by re-fetching from the relay.

use std::sync::Arc;
use std::time::Duration;

use nostr::{Event, Keys};
use serde::Serialize;
use tokio::sync::Mutex;

use crate::config::Config;
use crate::nostr_support::send_event_to_relays;

/// Max relay-send attempts before an item is parked in `failed`.
const MAX_ATTEMPTS: u32 = 5;
/// Backoff schedule (seconds) between attempts.
const BACKOFF_SECS: [u64; 4] = [1, 3, 8, 20];

#[derive(Clone)]
pub struct Outbox {
    inner: Arc<Mutex<Inner>>,
}

struct Inner {
    seq: u64,
    pending: usize,
    failed: Vec<FailedItem>,
}

/// A send that exhausted its retries and is awaiting a manual retry.
struct FailedItem {
    id: u64,
    label: String,
    keys: Keys,
    event: Event,
    relay_urls: Vec<String>,
    cfg: Config,
    last_error: String,
}

#[derive(Serialize, Clone)]
pub struct OutboxStatus {
    pub pending: usize,
    pub failed: usize,
    pub failed_items: Vec<OutboxFailedView>,
}

#[derive(Serialize, Clone)]
pub struct OutboxFailedView {
    pub id: u64,
    pub label: String,
    pub error: String,
}

impl Outbox {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(Mutex::new(Inner {
                seq: 0,
                pending: 0,
                failed: Vec::new(),
            })),
        }
    }

    /// Queue a signed event for background delivery. Returns immediately; the
    /// relay round-trip happens in a detached task.
    pub async fn enqueue(
        &self,
        cfg: Config,
        keys: Keys,
        event: Event,
        relay_urls: Vec<String>,
        label: String,
    ) {
        let id = {
            let mut inner = self.inner.lock().await;
            inner.seq += 1;
            inner.pending += 1;
            inner.seq
        };
        self.spawn_send(id, cfg, keys, event, relay_urls, label);
    }

    fn spawn_send(
        &self,
        id: u64,
        cfg: Config,
        keys: Keys,
        event: Event,
        relay_urls: Vec<String>,
        label: String,
    ) {
        let outbox = self.clone();
        tokio::spawn(async move {
            let mut last_error = String::new();
            for attempt in 0..MAX_ATTEMPTS {
                let cfg_for_opts = cfg.clone();
                let result = send_event_to_relays(&keys, &relay_urls, &event, |url| {
                    cfg_for_opts.relay_connect_options(url)
                })
                .await;
                match result {
                    Ok(()) => {
                        let mut inner = outbox.inner.lock().await;
                        inner.pending = inner.pending.saturating_sub(1);
                        tracing::debug!(%id, %label, "outbox delivered event");
                        return;
                    }
                    Err(err) => {
                        last_error = err.to_string();
                        tracing::warn!(%id, %label, attempt, error = %last_error, "outbox send failed");
                        if let Some(backoff) = BACKOFF_SECS.get(attempt as usize) {
                            tokio::time::sleep(Duration::from_secs(*backoff)).await;
                        }
                    }
                }
            }
            let mut inner = outbox.inner.lock().await;
            inner.pending = inner.pending.saturating_sub(1);
            inner.failed.push(FailedItem {
                id,
                label,
                keys,
                event,
                relay_urls,
                cfg,
                last_error,
            });
        });
    }

    pub async fn status(&self) -> OutboxStatus {
        let inner = self.inner.lock().await;
        OutboxStatus {
            pending: inner.pending,
            failed: inner.failed.len(),
            failed_items: inner
                .failed
                .iter()
                .map(|f| OutboxFailedView {
                    id: f.id,
                    label: f.label.clone(),
                    error: f.last_error.clone(),
                })
                .collect(),
        }
    }

    /// Re-queue all currently-failed items for another round of delivery.
    pub async fn retry_failed(&self) -> usize {
        let items: Vec<FailedItem> = {
            let mut inner = self.inner.lock().await;
            std::mem::take(&mut inner.failed)
        };
        let count = items.len();
        for item in items {
            let id = {
                let mut inner = self.inner.lock().await;
                inner.pending += 1;
                item.id
            };
            self.spawn_send(id, item.cfg, item.keys, item.event, item.relay_urls, item.label);
        }
        count
    }
}

impl Default for Outbox {
    fn default() -> Self {
        Self::new()
    }
}
