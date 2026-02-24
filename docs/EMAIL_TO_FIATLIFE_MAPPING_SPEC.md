# Email-to-FiatLife Mapping Spec (OpenClaw Agent)

This spec defines how an agent should convert forwarded statement/bill emails into safe, structured FiatLife updates.

## Goal

When a user forwards a monthly statement email, the agent should:

1. Parse key fields from email content/attachments.
2. Match the data to an existing FiatLife entity when possible.
3. Apply minimal updates (or create a new bill where appropriate).
4. Sign/publish through approved policy.
5. Return an audit record with confidence and explainability.

---

## 1) Supported intents

## A) Credit account update (existing account)

Primary use case: credit card statement email updates an existing credit account:

- `currentBalance`
- `minimumPayment`
- `dueDay` (derived from due date)
- optional metadata (statement period, confidence, source)

## B) Utility/one-off bill ingestion

Primary use case: utility invoice email updates existing bill or creates a new bill:

- bill `name` (issuer/service)
- `amount`
- `dueDay` or explicit due date anchor
- category/subcategory inference

---

## 2) Input sources and precedence

The agent may parse from:

1. Structured attachment (PDF/HTML statement with reliable fields)
2. Email body text
3. Subject line
4. Sender/domain metadata

Field precedence:

- If attachment parser confidence >= body parser confidence, prefer attachment values.
- If values conflict and confidence difference < 0.1, do not auto-write; require approval/manual review.

---

## 3) Canonical extraction payload

All parsers normalize into this shape before mapping:

```json
{
  "source": {
    "email_id": "string",
    "message_id": "string",
    "received_at_ms": 0,
    "from_address": "billing@issuer.com",
    "subject": "Your statement is ready"
  },
  "document": {
    "doc_type": "credit_statement",
    "issuer_name": "Ally",
    "account_hint_last4": "1234",
    "currency": "USD"
  },
  "fields": {
    "statement_date_iso": "2026-02-18",
    "statement_period_start_iso": "2026-01-19",
    "statement_period_end_iso": "2026-02-18",
    "due_date_iso": "2026-03-12",
    "new_balance": 1492.11,
    "minimum_payment": 40.0,
    "total_due": 1492.11
  },
  "confidence": {
    "overall": 0.96,
    "due_date": 0.98,
    "new_balance": 0.97,
    "minimum_payment": 0.94
  }
}
```

---

## 4) Entity matching rules

## 4.1 Credit account matching

Scoring strategy (0.0 to 1.0):

- +0.50 issuer name exact/fuzzy match (normalized)
- +0.30 account last4 match
- +0.10 sender-domain match allowlist
- +0.10 existing alias match (optional future field)

Decision:

- Score >= 0.85: auto-match existing account.
- Score 0.70 to 0.84: tentative match, require approval.
- Score < 0.70: no match; do not update account automatically.

If multiple accounts tie within 0.05, require approval.

## 4.2 Bill matching (utility/invoice)

Scoring strategy:

- +0.60 issuer/service name match
- +0.20 sender-domain match
- +0.20 recurring cadence consistency (historical due-window)

Decision:

- Score >= 0.85: update existing bill.
- Score 0.70 to 0.84: require approval.
- Score < 0.70: create candidate new bill (approval recommended).

---

## 5) Mapping rules (field-level)

## 5.1 Credit statement -> CreditAccount

- `fields.new_balance` -> `currentBalance`
- `fields.minimum_payment` -> `minimumPayment`
- `fields.due_date_iso` -> `dueDay` (day-of-month only)
- optional notes/audit metadata:
  - `source=agent`
  - `agent_run_id`
  - statement period fields

Constraints:

- Reject negative balance/minimum payment unless explicit credit/refund workflow.
- `dueDay` must be 1..31.
- If due date parse fails, keep existing `dueDay` and issue warning.

## 5.2 Utility invoice -> Bill

If existing bill matched:

- Update `amount`
- Update due anchor (`dueDay`, optionally recurrence anchor)
- Keep category/subcategory unless classifier confidence > 0.9

If no existing bill matched and confidence is high:

- Create new bill with:
  - `name = issuer/service`
  - `amount = extracted amount`
  - `generalCategory = UTILITIES` (or inferred category)
  - `frequency = MONTHLY` (default for utilities)
  - `dueDay` from due date

If confidence low:

- Create proposal only; do not publish.

---

## 6) Write policy and approval thresholds

## Auto-write allowed when all conditions pass

- Overall extraction confidence >= 0.90
- Match score >= 0.85
- Required fields present for intent
- Schema/business validation passes
- Signing policy allows intended kind

## Require approval when any are true

- Confidence in [0.70, 0.89]
- Ambiguous match (multi-candidate close scores)
- Category change proposed
- Large variance from prior values (see anomaly checks)

## Reject (no write) when any are true

- Confidence < 0.70
- Validation errors
- Missing required fields
- Signing denied

---

## 7) Anomaly checks before publish

## Credit account checks

- `new_balance` changed by > 5x previous balance and > $5,000 absolute change -> approval required.
- `minimum_payment` > `new_balance` and `new_balance > 0` -> validation warning/error.

## Bill checks

- Amount change > 3x trailing 3-cycle average -> approval required.
- Due day jumps by > 10 days from historical pattern -> approval required.

---

## 8) Idempotency and deduplication

Use idempotency key:

`sha256(message_id + issuer_name + account_hint_last4 + statement_period_end + new_balance)`

Rules:

- If same idempotency key already applied, skip publish and return `duplicate=true`.
- If same email reprocessed with improved parser but same financial values, no-op.

---

## 9) Publish contract

Agent write sequence:

1. `get_state`
2. `propose_changes`
3. `validate_changes`
4. `sign_event` (NIP-46)
5. `publish_event`
6. `sync_and_verify`

Agent must not call `sign_event` if validation fails.

---

## 10) Audit record requirements

Every run should emit:

```json
{
  "source": "agent",
  "agent_run_id": "run-uuid",
  "intent": "update_credit_account",
  "matched_entity_id": "credit-ally-visa",
  "match_score": 0.91,
  "confidence_overall": 0.96,
  "changes": [
    { "field": "currentBalance", "from": 1520.2, "to": 1492.11 },
    { "field": "minimumPayment", "from": 35.0, "to": 40.0 },
    { "field": "dueDay", "from": 12, "to": 12 }
  ],
  "publish": {
    "ok": true,
    "event_id": "nostr_event_id",
    "relay_count_acked": 1
  },
  "timestamp_ms": 0
}
```

---

## 11) Error handling behavior

- Parse failure: return explicit missing fields and keep email queued for retry/manual review.
- Match failure: generate ranked candidates and request confirmation.
- Signing failure: return bunker policy reason and required scope.
- Publish failure: one retry with backoff; if still failing, persist pending change set.

---

## 12) Minimal rollout plan

1. Read-only shadow mode (no writes): parse + match + report only.
2. Auto-update credit accounts only (no bill creates), strict thresholds.
3. Enable bill update/create for utilities with approval fallback.
4. Expand issuer templates and anomaly controls.

---

## 13) Definition of done (per processed email)

- Intent detected.
- Required fields extracted and validated.
- Entity matched or create-path chosen per policy.
- Write published (or safely deferred) with full audit.
- FiatLife sync verification result captured.
