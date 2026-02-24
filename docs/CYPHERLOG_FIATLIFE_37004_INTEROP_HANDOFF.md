# CypherLog ↔ FiatLife Interop Handoff (Kind 37004)

This document is intended for the CypherLog project team/AI to resolve interoperability issues between CypherLog subscriptions and FiatLife bill UI.

## Problem Summary

FiatLife successfully receives kind `37004` events, but cannot render subscription details because incoming events currently contain only minimal tags and encrypted content that FiatLife cannot decrypt.

Observed cached events in FiatLife:

- `tagsJson` contains only:
  - `["d", "<uuid>"]`
  - `["alt", "Encrypted Cypher Log subscription data"]`
  - `["client", "Cypher Log", "..."]`
- `contentDecryptedJson` is `null` (decryption failed)

Result: FiatLife has no `name`, `cost`, `billing_frequency`, or `subscription_type` to display.

## Why UI Looks Wrong

FiatLife can display `37004` subscriptions only if at least one is true:

1. canonical subscription fields are present in tags, or
2. encrypted content decrypts to valid JSON.

Current CypherLog events satisfy neither condition in FiatLife.

## Required Interop Contract for Kind 37004

CypherLog should publish addressable kind `37004` events with at least these tags:

- `d` (required)
- `alt` (required)
- `name` (required)
- `subscription_type` (required)
- `cost` (required)
- `billing_frequency` (required)

Optional but recommended:

- `currency`
- `company_id` or `company_name`
- `linked_asset_type`, `linked_asset_id`, `linked_asset_name`
- `notes`

### Canonical Example Event

```json
{
  "kind": 37004,
  "content": "",
  "tags": [
    ["d", "d1955978-dc61-4112-b324-8ad0da28dfa5"],
    ["alt", "Subscription: Netflix"],
    ["name", "Netflix"],
    ["subscription_type", "Streaming"],
    ["cost", "15.99"],
    ["currency", "USD"],
    ["billing_frequency", "monthly"],
    ["company_name", "Netflix"],
    ["notes", "4K plan"]
  ]
}
```

## CypherLog-Side Action Items

1. **Always include canonical tags**
   - Do not publish only `d/alt/client` for subscriptions.
   - Keep `alt` human readable (`Subscription: <name>`), not generic encrypted text.

2. **If using encrypted content, keep tags usable**
   - Encryption is fine, but tags must still carry non-sensitive display fields needed for cross-client rendering (`name`, `cost`, `billing_frequency`, `subscription_type`).

3. **NIP-44 recipient/key compatibility**
   - Ensure encryption recipient(s) allow the same Nostr identity to decrypt from FiatLife.
   - If DM-style encryption is used, verify appropriate `p` tags and recipient semantics.

4. **Type/format normalization**
   - `cost` as parseable decimal string (e.g. `"15.99"`).
   - `billing_frequency` values: `weekly|monthly|quarterly|semi-annually|annually|one-time`.
   - `subscription_type` examples: `Streaming`, `Software`, `Health/Wellness`, etc.

5. **Round-trip safety**
   - Unknown/custom tags should remain stable across edits.
   - `d` must remain stable for replaceable updates.

## FiatLife Parser Notes (Already Implemented)

FiatLife currently:

- reads tags case-insensitively,
- maps `subscription_type` to local subscription categories,
- supports numeric and string cost parsing,
- falls back to tags if content parse/decrypt fails,
- publishes `alt`, `name`, `subscription_type`, `cost`, `billing_frequency` on writes.

So remaining blockers are primarily on the event payload/decryption side.

## Interop Acceptance Tests

CypherLog change is complete when all pass:

1. Create subscription in CypherLog.
2. FiatLife syncs and displays:
   - correct `name`
   - correct `cost`
   - correct `frequency`
   - mapped `subscription_type` category
3. FiatLife debug export row shows:
   - tags include canonical fields above, and/or
   - `contentDecryptedJson` contains valid JSON with those fields.
4. Edit in CypherLog and confirm FiatLife updates same `d` record.
5. Delete in FiatLife and confirm CypherLog respects NIP-09 deletion.

## Current Failure Signature (for quick diagnosis)

If you see this pattern again, interop is still broken:

- tags only `d + alt("Encrypted ...") + client`
- missing `name/cost/billing_frequency/subscription_type`
- FiatLife `contentDecryptedJson = null`

---

If needed, attach one raw relay event and the corresponding FiatLife debug export row to validate exact field mapping.
