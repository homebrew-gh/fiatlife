# OpenClaw FiatLife Schemas (Machine-Readable)

Use these schemas to validate agent payloads before calling signing/publish tools.

## 1) Action envelope schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.agent.action-envelope.v1",
  "title": "FiatLife Agent Action Envelope",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "intent",
    "target",
    "changes",
    "validation",
    "signing",
    "audit"
  ],
  "properties": {
    "intent": {
      "type": "string",
      "enum": [
        "add_bank_account",
        "update_bank_account",
        "delete_bank_account",
        "add_credit_account",
        "update_credit_account",
        "update_bill"
      ]
    },
    "target": {
      "type": "object",
      "additionalProperties": false,
      "required": ["entity_type", "entity_id"],
      "properties": {
        "entity_type": {
          "type": "string",
          "enum": ["bank_account", "credit_account", "bill"]
        },
        "entity_id": {
          "type": "string",
          "minLength": 1
        }
      }
    },
    "changes": {
      "type": "array",
      "minItems": 1,
      "items": { "$ref": "#/$defs/changeItem" }
    },
    "validation": { "$ref": "#/$defs/validationResult" },
    "signing": { "$ref": "#/$defs/signingRequest" },
    "result": { "$ref": "#/$defs/publishResult" },
    "audit": { "$ref": "#/$defs/auditRecord" }
  },
  "$defs": {
    "changeItem": {
      "type": "object",
      "additionalProperties": false,
      "required": ["field", "to"],
      "properties": {
        "field": { "type": "string", "minLength": 1 },
        "from": {},
        "to": {},
        "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
      }
    },
    "validationResult": {
      "type": "object",
      "additionalProperties": false,
      "required": ["ok", "errors"],
      "properties": {
        "ok": { "type": "boolean" },
        "errors": {
          "type": "array",
          "items": { "type": "string" }
        },
        "warnings": {
          "type": "array",
          "items": { "type": "string" }
        }
      }
    },
    "signingRequest": {
      "type": "object",
      "additionalProperties": false,
      "required": ["mode", "requested_kind"],
      "properties": {
        "mode": { "type": "string", "enum": ["nip46"] },
        "requested_kind": { "type": "integer", "minimum": 1 },
        "policy_scope": { "type": "string" }
      }
    },
    "publishResult": {
      "type": "object",
      "additionalProperties": false,
      "required": ["published"],
      "properties": {
        "published": { "type": "boolean" },
        "event_id": { "type": "string" },
        "relay_count_acked": { "type": "integer", "minimum": 0 },
        "error": { "type": "string" }
      }
    },
    "auditRecord": {
      "type": "object",
      "additionalProperties": false,
      "required": ["source", "agent_run_id", "timestamp_ms"],
      "properties": {
        "source": { "type": "string", "const": "agent" },
        "agent_run_id": { "type": "string", "minLength": 1 },
        "timestamp_ms": { "type": "integer", "minimum": 1 },
        "trace_id": { "type": "string" }
      }
    }
  }
}
```

## 2) Domain patch schemas

### 2.1 Bank account patch

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.agent.patch.bank-account.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "name"],
  "properties": {
    "id": { "type": "string", "minLength": 1 },
    "name": { "type": "string", "minLength": 1, "maxLength": 120 }
  }
}
```

### 2.2 Credit account patch

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.agent.patch.credit-account.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id"],
  "properties": {
    "id": { "type": "string", "minLength": 1 },
    "name": { "type": "string", "minLength": 1, "maxLength": 120 },
    "currentBalance": { "type": "number", "minimum": 0 },
    "minimumPayment": { "type": "number", "minimum": 0 },
    "aprPercent": { "type": "number", "minimum": 0, "maximum": 1000 },
    "dueDay": { "type": "integer", "minimum": 1, "maximum": 31 },
    "creditLimit": { "type": "number", "minimum": 0 },
    "notes": { "type": "string", "maxLength": 5000 }
  }
}
```

### 2.3 Bill recurrence patch

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.agent.patch.bill.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id"],
  "properties": {
    "id": { "type": "string", "minLength": 1 },
    "name": { "type": "string", "minLength": 1, "maxLength": 120 },
    "amount": { "type": "number", "minimum": 0 },
    "currency": { "type": "string", "minLength": 3, "maxLength": 8 },
    "frequency": {
      "type": "string",
      "enum": ["WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY", "SEMI_ANNUALLY", "ANNUALLY", "ONE_TIME"]
    },
    "dueDay": { "type": "integer", "minimum": 1, "maximum": 31 },
    "initialPurchaseDateMillis": { "type": "integer", "minimum": 0 },
    "recurrenceUnit": { "type": "string", "enum": ["DAY", "WEEK", "MONTH", "YEAR"] },
    "recurrenceIntervalCount": { "type": "integer", "minimum": 1, "maximum": 120 },
    "recurrenceTimezone": { "type": "string", "minLength": 1, "maxLength": 80 },
    "payFromBankAccountId": { "type": "string", "minLength": 1 },
    "payFromCreditAccountId": { "type": "string", "minLength": 1 }
  }
}
```

## 3) Tool IO contracts

### 3.1 `fiatlife.validate_changes` request

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.tool.validate-changes.request.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["intent", "payload"],
  "properties": {
    "intent": { "type": "string" },
    "payload": { "type": "object" }
  }
}
```

### 3.2 `fiatlife.validate_changes` response

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.tool.validate-changes.response.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["ok", "errors"],
  "properties": {
    "ok": { "type": "boolean" },
    "errors": { "type": "array", "items": { "type": "string" } },
    "warnings": { "type": "array", "items": { "type": "string" } },
    "normalized_payload": { "type": "object" }
  }
}
```

### 3.3 `fiatlife.sign_event` request

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.tool.sign-event.request.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["kind", "unsigned_event_json"],
  "properties": {
    "kind": { "type": "integer", "minimum": 1 },
    "unsigned_event_json": { "type": "string", "minLength": 2 },
    "policy_scope": { "type": "string" }
  }
}
```

### 3.4 `fiatlife.sign_event` response

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.tool.sign-event.response.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["ok"],
  "properties": {
    "ok": { "type": "boolean" },
    "signed_event_json": { "type": "string" },
    "error": { "type": "string" }
  }
}
```

### 3.5 `fiatlife.publish_event` response

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.tool.publish-event.response.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["ok", "relay_count_acked"],
  "properties": {
    "ok": { "type": "boolean" },
    "event_id": { "type": "string" },
    "relay_count_acked": { "type": "integer", "minimum": 0 },
    "error": { "type": "string" }
  }
}
```

## 4) Gating rules (agent must enforce)

- Do not call `fiatlife.sign_event` when `validation.ok = false`.
- Reject payloads with unknown keys (`additionalProperties=false`).
- For bill updates, reject `recurrenceIntervalCount < 1`.
- For financial values, reject negatives unless explicitly a credit/refund workflow.
- If both `payFromBankAccountId` and `payFromCreditAccountId` are present, require explicit conflict resolution.

## 5) Versioning

- Current version: `v1`.
- Any breaking schema change must increment version suffix and `$id`.
- Keep at least one previous version available during migration windows.

## 6) Golden examples (preflight tests)

Run these payloads through your validator in CI or startup checks.

### 6.1 Valid examples

#### Valid A: add bank account

```json
{
  "intent": "add_bank_account",
  "target": {
    "entity_type": "bank_account",
    "entity_id": "bank-2ad065a4"
  },
  "changes": [
    { "field": "name", "to": "Chase Checking", "confidence": 0.99 }
  ],
  "validation": { "ok": true, "errors": [] },
  "signing": { "mode": "nip46", "requested_kind": 30078 },
  "audit": {
    "source": "agent",
    "agent_run_id": "run-20260219-001",
    "timestamp_ms": 1771500000000
  }
}
```

#### Valid B: update credit account from statement parse

```json
{
  "intent": "update_credit_account",
  "target": {
    "entity_type": "credit_account",
    "entity_id": "credit-ally-visa"
  },
  "changes": [
    { "field": "currentBalance", "from": 1520.2, "to": 1492.11, "confidence": 0.97 },
    { "field": "minimumPayment", "from": 35.0, "to": 40.0, "confidence": 0.93 },
    { "field": "dueDay", "from": 12, "to": 12, "confidence": 1.0 }
  ],
  "validation": { "ok": true, "errors": [], "warnings": [] },
  "signing": { "mode": "nip46", "requested_kind": 30078 },
  "audit": {
    "source": "agent",
    "agent_run_id": "run-20260219-002",
    "timestamp_ms": 1771500033000,
    "trace_id": "trace-abc-123"
  }
}
```

#### Valid C: update bill recurrence

```json
{
  "intent": "update_bill",
  "target": {
    "entity_type": "bill",
    "entity_id": "bill-netflix"
  },
  "changes": [
    { "field": "frequency", "from": "MONTHLY", "to": "MONTHLY" },
    { "field": "dueDay", "from": 15, "to": 15 },
    { "field": "recurrenceUnit", "from": "MONTH", "to": "MONTH" },
    { "field": "recurrenceIntervalCount", "from": 1, "to": 1 },
    { "field": "payFromBankAccountId", "from": null, "to": "bank-2ad065a4" }
  ],
  "validation": { "ok": true, "errors": [] },
  "signing": { "mode": "nip46", "requested_kind": 37004 },
  "audit": {
    "source": "agent",
    "agent_run_id": "run-20260219-003",
    "timestamp_ms": 1771500077000
  }
}
```

### 6.2 Invalid examples

#### Invalid A: unknown top-level field

Expected failure reason: `additionalProperties=false` violation on root object.

```json
{
  "intent": "add_bank_account",
  "target": {
    "entity_type": "bank_account",
    "entity_id": "bank-demo"
  },
  "changes": [{ "field": "name", "to": "Demo" }],
  "validation": { "ok": true, "errors": [] },
  "signing": { "mode": "nip46", "requested_kind": 30078 },
  "audit": {
    "source": "agent",
    "agent_run_id": "run-bad-001",
    "timestamp_ms": 1771500100000
  },
  "debug_note": "this key is not allowed"
}
```

#### Invalid B: recurrence interval out of bounds

Expected failure reason: `recurrenceIntervalCount` must be `>= 1`.

```json
{
  "id": "bill-1",
  "recurrenceUnit": "MONTH",
  "recurrenceIntervalCount": 0
}
```

#### Invalid C: conflicting payment account links

Expected failure reason: business-rule conflict (both payment account fields present at once).

```json
{
  "id": "bill-2",
  "payFromBankAccountId": "bank-123",
  "payFromCreditAccountId": "credit-456"
}
```
