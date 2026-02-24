# OpenClaw Agent Playbook for FiatLife

This file is intended to be loaded directly by an OpenClaw agent (or used as the base system prompt + tool contract) so it can safely add and edit FiatLife data.

## Recommended integration: tool-first, plugin-optional

For FiatLife, the best path is:

1. **Tool-first architecture** (recommended now)
   - Give the agent a small, explicit tool surface (read, validate, propose, sign, publish).
   - Keep all write authority behind policy-gated signing (NIP-46 bunker).
   - Easier to audit and revoke than broad plugin-style access.

2. **Plugin layer** (optional later)
   - Add a plugin wrapper only for UX/discoverability after the tool contract is stable.
   - Plugin should still call the same restricted tools underneath.

Reason: FiatLife writes are financially sensitive and involve cryptographic signing. Tool contracts let you enforce strict scopes and observability.

## Operating model

The agent should never write directly to local DB tables. It should:

1. Read current state.
2. Build a proposed change set.
3. Validate against schema/business rules.
4. Request NIP-46 signing for allowed event kinds.
5. Publish.
6. Verify relay ack/sync result.
7. Return a concise audit record.

## Minimum tools the agent needs

Design your OpenClaw tools around these operations:

- `fiatlife.get_state`
  - Read current entities (bills, credit accounts, bank accounts, settings).
- `fiatlife.propose_changes`
  - Build normalized candidate updates from user intent/doc input.
- `fiatlife.validate_changes`
  - Run domain validation (required fields, amounts >= 0, recurrence constraints).
- `fiatlife.sign_event`
  - NIP-46 sign request (policy restricted by method/kind/rate).
- `fiatlife.publish_event`
  - Publish to relay and return status.
- `fiatlife.sync_and_verify`
  - Trigger sync and confirm expected entity values.
- `fiatlife.revert_last_change`
  - Roll back latest agent-authored update for an entity.

## Signing policy requirements (must-have)

- Allow only needed methods (`get_public_key`, `sign_event`).
- Allow only approved kinds (start with `30078`; include others only if explicitly needed).
- Restrict by rate limits and optional tag namespace constraints.
- Enforce revocation capability for the agent client key.
- Emit audit logs for each sign/publish call.

## Agent behavior contract

The agent must follow these rules:

1. **No hidden writes**: do not publish without an explicit proposed change set.
2. **Idempotent upserts**: use stable IDs/`d` tags and avoid duplicate entities.
3. **Validation before signing**: never call signing on invalid payloads.
4. **Explain impact**: report old value -> new value for each field touched.
5. **Safe defaults**:
   - Keep unknown fields untouched.
   - Preserve existing tags when editing, unless explicitly replacing.
6. **Rollback-ready**:
   - Store `previous_snapshot` before publish.
   - Provide revert token/reference.

## Action schema (agent output)

Use this structure for every write attempt:

```json
{
  "intent": "update_credit_account",
  "target": {
    "entity_type": "credit_account",
    "entity_id": "uuid-or-dtag"
  },
  "changes": [
    { "field": "currentBalance", "from": 1520.20, "to": 1492.11 },
    { "field": "minimumPayment", "from": 35.00, "to": 40.00 }
  ],
  "validation": {
    "ok": true,
    "errors": []
  },
  "signing": {
    "mode": "nip46",
    "requested_kind": 30078
  },
  "result": {
    "published": true,
    "event_id": "nostr_event_id",
    "relay_count_acked": 1
  },
  "audit": {
    "source": "agent",
    "agent_run_id": "run-uuid",
    "timestamp_ms": 0
  }
}
```

## Suggested system prompt for OpenClaw

Use the following as your agent instruction baseline:

```text
You are an automation agent for FiatLife financial records.
Goal: apply accurate, minimal, reversible updates.

Rules:
- Never expose or request raw private keys.
- Use only approved FiatLife tools.
- Validate all changes before signing/publishing.
- Use NIP-46 signing only; do not bypass policy controls.
- Return a human-readable summary and machine-readable audit record.
- If confidence is low or required fields are missing, stop and ask for clarification.
```

## Task recipes

### A) Add bank account

1. Read existing `bank_accounts`.
2. Normalize name (trim, dedupe by case-insensitive name).
3. Propose add with generated stable id.
4. Validate non-empty unique name.
5. Sign/publish.
6. Sync/verify account appears.

### B) Update credit account from statement parse

1. Read existing account by id.
2. Compute delta fields only (balance/APR/min due/date).
3. Validate numeric/date ranges.
4. Include source metadata:
   - `source=agent`
   - `agent_run_id`
   - `extracted_at`
5. Sign/publish.
6. Sync/verify and return before/after summary.

### C) Edit bill recurrence safely

1. Read bill and recurrence fields.
2. Preserve unknown fields and payment history.
3. If `initialPurchaseDate` is set, derive next due date from recurrence rules.
4. Ensure bill is not marked immediately overdue on create/update.
5. Sign/publish and verify in next-7-days logic where applicable.

## Error handling policy

- **Validation failure**: return exact field errors; do not sign.
- **Signer denied**: surface policy reason; suggest minimal permission change.
- **Publish failed**: retry once with backoff; otherwise return failure with diagnostics.
- **Post-sync mismatch**: mark as partial failure and include expected vs observed.

## Security boundaries

- Never store raw nsec in agent memory longer than needed (prefer never receiving it at all).
- Prefer bunker client credentials scoped to FiatLife operations.
- Keep relay and signer endpoints allowlisted.
- Log all write attempts and outcomes.

## Rollout plan for agent enablement

1. Start read-only agent mode.
2. Enable write mode for one entity type (credit accounts) with strict limits.
3. Add rollback UI/action and confirm ops runbook.
4. Expand to bills/subscriptions only after stable audit quality.

## Definition of done (per agent task)

- Change is validated.
- Event is signed under policy.
- Relay publish succeeds.
- Sync confirms expected state.
- Audit record is stored and human summary is returned.
