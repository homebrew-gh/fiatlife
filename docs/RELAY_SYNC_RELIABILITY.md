# Relay Sync Reliability – Why Data Sometimes Doesn’t All Load

This doc explains why the app sometimes doesn’t get all data from the relay (missing credit cards, goals, bills) and what was done to improve it.

---

## Likely causes

### 1. **Relay event limit (most likely)**

- Each sync (bills, goals, credit accounts, etc.) sends a **REQ** with filter `{ authors: [pubkey], kinds: [30078] }` and **no `limit`**.
- Many relays enforce a **default limit** (e.g. 250, 500, 1000) per subscription. If you have **more** kind-30078 events than that, the relay sends only the first N, then **EOSE**.
- The app then **stops collecting** for that subscription (it closes on EOSE). Any events after the limit are **never received**.
- Because we don’t send a `#d` filter, the relay returns **all** 30078 events for the pubkey (bills, goals, credit, bank, billers, CypherLog, etc.). So “first N” is whatever order the relay uses (often creation time or event id), not “all bills then all goals.” So you can miss **any** type: e.g. some bills, or some goals, or some credit accounts.

**Fix (in code):** Request a high explicit `limit` in the subscription filter (e.g. 2000 or 5000) so the relay doesn’t cap at a low default. Optionally split by `#d` prefix so each sync only gets its own event type and uses the limit for that type.

---

### 2. **SharedFlow buffer overflow**

- Incoming relay messages are pushed into a **SharedFlow** with `extraBufferCapacity = 64`.
- **tryEmit** is used: if the buffer is full, the emit **fails** and the event is **dropped** (no retry).
- When **8 syncs run in parallel**, 8 subscriptions are active. Each EVENT from the relay is tryEmit’d once. If the relay sends events faster than the app can process them (decrypt + parse + DB write), the buffer can fill and **later events are dropped**.
- Dropped events are never written to the DB, so items appear “missing” at random.

**Fix:** Increase the buffer size and/or run syncs **sequentially** (or in small batches) so the event rate stays below what the app can drain. Optionally switch to a blocking emit so the WebSocket thread is back‑pressured instead of dropping.

---

### 3. **EOSE before all events**

- The app **closes each subscription when it receives EOSE** for that subscription id.
- Per NIP-01, EOSE means “end of stored events” for that REQ. Some relays:
  - Send EOSE **before** sending all events (bug or implementation choice).
  - Or send EOSE after a **limited** set (see cause 1).
- If EOSE is processed **before** some EVENTs (e.g. out-of-order delivery), the app would also close early and miss those events.

**Mitigation:** Same as (1): request a high `limit` and, if needed, run syncs in a way that reduces message burst so the buffer (2) doesn’t drop events.

---

### 4. **30-second timeout**

- Each repo’s `syncFromNostr()` uses **withTimeout(30_000)** around the collect.
- If the relay is slow (many events, slow network), the collect might **time out** before EOSE. The subscription is then cancelled and **only events received before the timeout** are stored.

**Mitigation:** Increase timeout for sync (e.g. 60s) or make it configurable; combined with (1)–(2), the app is more likely to receive EOSE within the timeout.

---

### 5. **Decryption or parse failures**

- If **decryption** fails (wrong key, NIP-44 issue, corrupted payload), the event is **logged and skipped** – it is never stored.
- If **parsing** fails (unknown enum, malformed JSON), the same: skip and log. Goals use `decodeGoalSafely` so some bad payloads are repaired; others are still dropped.
- So **individual** items can be missing because of one bad event, not because of “sync didn’t fetch it.”

**Mitigation:** Keep logging; consider a “sync report” that counts decryption/parse failures so the user knows when data was skipped.

---

### 6. **Auth timing**

- REQs are **queued** if the connection isn’t authenticated yet, then **drained** after NIP-42 auth completes. So sync doesn’t send REQ before auth.
- If there’s a bug in drain order or the relay ignores REQs sent immediately after auth, some subscriptions could get no or partial data. Less common than (1)–(2), but possible on strict relays.

---

## Summary

| Cause                    | Effect                         | Fix / mitigation                          |
|--------------------------|--------------------------------|-------------------------------------------|
| Relay limit (no `limit`) | Only first N events returned   | Add high `limit` (e.g. 2000) to REQ       |
| SharedFlow buffer (64)   | Events dropped when busy       | Bigger buffer; sequential or batched sync|
| EOSE too early           | Subscription closes early      | Same as limit + buffer                    |
| 30s timeout              | Partial sync on slow relay     | Longer or configurable timeout            |
| Decrypt/parse failure    | Single items missing           | Logging; optional sync report             |

The most impactful changes are: **add an explicit high `limit` to the app-data subscription** and **run syncs sequentially** (and optionally **increase the message buffer**) so the app doesn’t drop events when the relay sends a lot of data.
