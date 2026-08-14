# Local LLM Document Ingestion Plan (llama.cpp on Start9)

Status: planning / iterate-on draft. No code yet.

This is the single source of truth for adding AI/LLM features to FiatLife. It supersedes
and consolidates the earlier external-agent docs (OpenClaw playbook/schemas, email-to-FiatLife
mapping spec, AI agent NIP-26/NIP-46 signing doc). Those were written assuming an **external**
agent that had to solve a hard signing problem. Running the LLM **locally on the Start9 box**
changes the architecture enough that those docs were more confusing than helpful, so the still-useful
parts have been folded in here.

---

## 1. Core idea

Use a local `llama.cpp` server (running on the same Start9 device as the FiatLife web server) to
turn uploaded documents — **bill/statement PDFs and paystub PDFs** — into structured FiatLife data,
so the user doesn't have to type it in.

Initial target use cases:

1. **Paystub PDF -> paycheck log entry** (highest value; most tedious manual entry).
2. **Credit card statement PDF -> credit account update** (small, bounded field set).
3. **Utility / recurring bill PDF -> new or updated bill** (the original motivating idea).

---

## 2. Architectural insight: local LLM removes the signing problem

The earlier docs spent most of their effort on one hard problem: *how does an external agent sign
events without holding the user's key?* (NIP-26 delegation vs NIP-46 bunker, delegation windows,
revocation, a self-hosted bunker on a NAS, etc.)

That problem **largely disappears** when the LLM runs on the same box as `apps/web/server/`:

- The web server **already holds the sealed nsec**, unlocks it into memory on session unlock, and
  **already signs + publishes kind-30078 events** (`build_app_data_event` -> outbox -> relays).
  Manual "Add bill" / "Log paycheck" already use this exact path.
- So the LLM needs **no key, no bunker, no delegation**. It is purely a **structured-data extractor**
  feeding the *same* publish path the manual forms already use.

NIP-46 / NIP-26 only become relevant again if parsing ever moves **off-box** (cloud LLM or a separate
machine). For local llama.cpp on Start9, that complexity is explicitly **out of scope**.

### Trust boundary

```
[ PDF upload ] -> [ deterministic text extract ] -> [ local LLM: text -> JSON (domain fields only) ]
   -> [ Rust server: validate + anomaly checks ] -> [ review/approve UI ] -> [ existing publishAppData -> sign -> relay ]
```

The LLM output is treated as **untrusted data** that must pass schema + business validation before it
can reach the (already-trusted) signing/publish path.

---

## 3. Critical design rule: the LLM does NOT generate Nostr events

The LLM's only job is: **extracted document text -> strict JSON matching a FiatLife domain shape**
(`Bill` / `PaycheckLogEntry` / credit-account patch). It knows nothing about Nostr.

The **Rust server** owns everything Nostr: building the `d`-tag (`fiatlife/bill/{uuid}`,
`fiatlife/salary`, etc.), NIP-44 encrypting the content, setting `created_at`, signing, and publishing
via the outbox. It already does all of this.

Why:

- LLMs are unreliable at producing exact, valid signed events (kind, tag conventions, encryption,
  Schnorr signature). A mistake there yields a malformed event on the relay.
- We never want the model near signing material.
- A hallucinated field becomes a **validation failure**, not a bad signed event.

---

## 4. Model & hardware

### 4.1 Start9 hardware (measured)

- **~32 GB total RAM**, ~19 GB available at the time of measurement.
- **No GPU** -> CPU-only inference.
- zram (compressed swap) configured (~8 GB) — do **not** rely on it for model weights; keep the model
  resident in real RAM to avoid swap thrash during inference.

Implication: **RAM is not the binding constraint** — there's plenty of headroom. The binding
constraint is **CPU throughput** (tokens/sec). For an async "upload -> extract -> review" flow (not
interactive chat), slower CPU generation is acceptable: a paystub extraction emits only a few hundred
tokens of JSON, so seconds-to-tens-of-seconds latency is fine.

### 4.2 Model choice

**Selected model: `Qwen2.5-14B-Instruct` (GGUF, ~9 GB at Q4_K_M).** Chosen for accuracy on the
many-field numeric paystub case, which is the binding quality concern. It fits comfortably in the
~19 GB available, leaving room for context + the web server.

Considered alternatives (kept here for the swap path):

| Model | RAM (4-bit) | Notes |
|-------|-------------|-------|
| Gemma 3n E2B | ~2-3 GB | Fine for normalization/categorization/small-field; weakest on many-field numeric extraction. |
| Gemma 3n E4B | ~3-4 GB | Same family, meaningfully stronger; low-risk. |
| Qwen2.5-7B-Instruct | ~5 GB | Strong structured extractor; faster fallback if 14B CPU latency hurts. |
| **Qwen2.5-14B-Instruct** | **~9 GB** | **Selected.** Highest accuracy that still fits; slowest on CPU. |

CPU-latency note (no GPU): 14B on CPU generates slowly (single-digit tokens/sec is typical on a
many-core box). For this **batch, review-gated** flow that's acceptable — a paystub extraction emits a
few hundred tokens, so expect roughly tens of seconds per document, not interactive speed. If that
proves too slow in practice, **Qwen2.5-7B-Instruct** is the drop-in fallback (same prompt + grammar).
Keep the model swappable behind the llama.cpp OpenAI-compatible endpoint and benchmark 14B vs 7B on
real paystubs during shadow mode (section 14).

### 4.3 Fit notes

- **Good fit (any of the above):** biller normalization/dedup, category inference, natural-language
  bill entry, **small-field extraction** (credit statement -> balance / min payment / due date).
- **Risk area:** **accurate many-field numeric extraction** (full paystub). Grammar constraints
  guarantee valid JSON *structure*, not field *accuracy* — hence the deterministic cross-check in
  section 5.3 and human review.
- **Mitigations:** deterministic text extraction first (don't rely on vision for numbers),
  temperature 0, GBNF / JSON-schema-constrained decoding, decompose large extractions, per-field
  confidence, deterministic totals recomputation + cross-check, and human-in-the-loop review.
- **Multimodal note:** Gemma 3n can ingest images, so it *could* read scanned/image-only PDFs directly.
  Treat as a fallback only; small-model vision OCR of dense financial tables is error-prone.

---

## 5. Pipeline (per uploaded document)

1. **Upload** PDF in the Bills / Paycheck tab. Reuse the existing Blossom upload path
   (`accept="application/pdf"` already exists for attachments) so the source doc is stored and
   hash-linked to whatever record we create.
2. **Extract text** with a deterministic script (e.g. pdfplumber). Add an OCR fallback
   (e.g. Tesseract) for image-only / scanned PDFs.
3. **LLM normalize**: grammar-constrained call to llama.cpp -> JSON in the canonical extraction shape
   (section 7) + per-field confidence.
4. **Map -> domain model** (`Bill`, `PaycheckLogEntry`, or credit-account patch) and run
   **validation + anomaly checks** (sections 8-10).
5. **Propose, don't silently write**: surface a review card pre-filled into the existing
   `BillSheet` / `LogPaycheckSheet`, showing the source PDF and a per-field `from -> to` diff. Above a
   confidence threshold, offer one-tap accept; below it, require edits.
6. **Publish** via the existing flow, tagging the payload with audit metadata (section 11) and the
   Blossom `attachmentHash` of the source PDF.

### Paycheck data-model wrinkle

A paystub maps to a `PaycheckLogEntry` (with `earnings[]`, `taxes[]`, `preTaxDeductions[]`,
`postTaxDeductions[]`, `employerContributions[]` line items). These entries live **inside** the single
`fiatlife/salary` record's `paycheckLog[]`. So publishing is a **read-merge-write** into
`SalaryConfig`, not a fresh event — and `apps/web/server/src/salary_merge.rs` already handles exactly
that merge concern.

---

## 6. llama.cpp deployment notes (Start9)

- Run llama.cpp as a **sibling service** exposing its OpenAI-compatible `/v1/chat/completions`
  (or native `/completion`) endpoint on **localhost / the StartOS internal network only** — never
  exposed externally.
- The Rust server calls it via the `reqwest` client it already depends on.
- **Determinism:** temperature 0 (or near) for repeatable, debuggable extraction.
- **Structured output:** use llama.cpp **GBNF grammar / JSON-schema-constrained decoding** so the
  model can only emit JSON conforming to the target shape. This eliminates the "wrapped in prose" /
  invalid-JSON failure class, which is the #1 headache in PDF->struct pipelines.
- **Privacy win (worth stating):** statements and paystubs are highly sensitive and **never leave the
  box**. That's the main reason to keep this local rather than using a cloud API.

---

## 7. Canonical extraction payload

All parsers/LLM calls normalize into this shape before mapping to a domain model. (Carried over from
the old email-mapping spec; trimmed to the local-document case.)

```json
{
  "source": {
    "doc_hash": "blossom-sha256",
    "received_at_ms": 0,
    "filename": "statement.pdf"
  },
  "document": {
    "doc_type": "credit_statement | utility_invoice | paystub",
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

## 8. Entity matching rules

### 8.1 Credit account matching (0.0-1.0)

- +0.50 issuer name exact/fuzzy match (normalized)
- +0.30 account last4 match
- +0.10 sender/issuer-domain match allowlist
- +0.10 existing alias match (optional future field)

Decision: `>=0.85` auto-match; `0.70-0.84` tentative (require approval); `<0.70` no auto-update.
Multiple candidates tied within 0.05 -> require approval.

### 8.2 Bill matching (utility/invoice)

- +0.60 issuer/service name match (match against existing **billers**, see `Biller` model)
- +0.20 sender/issuer-domain match
- +0.20 recurring cadence consistency (historical due-window)

Decision: `>=0.85` update existing bill; `0.70-0.84` require approval; `<0.70` create candidate new
bill (approval recommended). Use biller matching to avoid duplicate billers (e.g. "CONED" / "Con
Edison" / "Consolidated Edison").

---

## 9. Field mapping rules

### 9.1 Credit statement -> credit account

- `fields.new_balance` -> `currentBalance`
- `fields.minimum_payment` -> `minimumPayment`
- `fields.due_date_iso` -> `dueDay` (day-of-month only, must be 1..31)
- Reject negative balance/min payment unless explicit credit/refund workflow.
- If due-date parse fails, keep existing `dueDay` and warn.

### 9.2 Utility invoice -> bill

If existing bill matched: update `amount`, update due anchor (`dueDay` / recurrence anchor), keep
category/subcategory unless classifier confidence > 0.9.

If no match and confidence high: create new bill with `name = issuer/service`, `amount = extracted`,
inferred category (default `UTILITIES`), `frequency = MONTHLY` default, `dueDay` from due date.

If confidence low: create proposal only; do not publish.

### 9.3 Paystub -> PaycheckLogEntry

The model extracts the **itemized lines** (`earnings`, `taxes`, `preTaxDeductions`,
`postTaxDeductions`, `employerContributions`) plus the **printed** `payDate`, `grossPay`, `netPay`.
Mapping then assigns UUIDs (the model must not invent `id`s), sets `autoGenerated = true` and
`attachmentHash` to the source PDF, and read-merge-writes into `fiatlife/salary` (use
`mergeSalaryConfigPreserveLogs` so logs aren't clobbered).

Two non-negotiable guardrails specific to paystubs:

1. **Current-period vs YTD trap.** Paystubs print *current period* and *year-to-date* columns
   side-by-side. We want **current period** values only. This must be explicit in the prompt, and the
   sum-cross-check below catches most YTD bleed-through.
2. **Deterministic totals + cross-check.** Do **not** trust the model's arithmetic. After extraction,
   run `recomputeEntryTotals()` (`apps/web/web/src/lib/salary.ts`) to derive `grossPay`, `totalTaxes`,
   `totalPreTaxDeductions`, `totalPostTaxDeductions`, and `netPay` from the line items. Then compare
   the recomputed `grossPay`/`netPay` against the **printed** gross/net the model reported:
   - within tolerance (e.g. <= $0.02 or a tiny relative epsilon) -> high-confidence, eligible for
     one-tap accept;
   - mismatch -> flag for review (likely a missed line, a misclassified line, or YTD bleed-through).

This turns the unreliable part (model arithmetic) into a deterministic computation and gives a free,
strong confidence signal.

### 9.4 Label normalization

Normalize extracted line labels toward FiatLife's known vocabulary so downstream logic
(`inferPayRatesFromLogs`, YTD merges, overtime detection) works:

- Earnings -> one of `EARNINGS_CATEGORIES`: `Regular`, `Overtime`, `Bonus`, `Commission`, `Holiday`,
  `PTO`, `Tips`, `Reimbursement`, `Other`. (Overtime detection keys on `/overtime|^ot\b/i`; "Regular"
  detection keys on `/^\s*regular/i` — preserve those so raise inference keeps working.)
- Taxes -> canonical labels where possible: `Federal income tax`, `State income tax`,
  `Social Security`, `Medicare`, `Local tax`. (Federal-withholding projection matches
  `/federal\s+income\s+tax/i`.)
- Pre/post-tax deductions and employer contributions -> keep the paystub's label but trimmed; these
  are free-text.

---

## 10. Anomaly checks before publish

Credit account:
- `new_balance` changed by > 5x previous AND > $5,000 absolute -> require approval.
- `minimum_payment > new_balance` while `new_balance > 0` -> validation warning/error.

Bill:
- Amount change > 3x trailing 3-cycle average -> require approval.
- Due day jumps > 10 days from historical pattern -> require approval.

Paystub:
- `grossPay` / `netPay` deviates sharply from prior logged paychecks -> require approval.
- Line items don't sum to gross/net within tolerance -> warning.

---

## 11. Write policy, audit, idempotency

### Write policy

- **Auto-accept** (one-tap, pre-checked) when: overall confidence >= 0.90 AND match score >= 0.85 AND
  required fields present AND validation passes.
- **Require review/edit** when: confidence in [0.70, 0.89], ambiguous match, proposed category change,
  or large variance from prior values.
- **Reject** when: confidence < 0.70, validation errors, or missing required fields.

(For v1, every proposal goes through the review UI regardless; auto-accept is a later optimization.)

### Audit metadata (embed in published payload)

- `source = "llm"`
- `model` (e.g. `gemma-3n-E2B-it`)
- `run_id`
- `extracted_at`
- `doc_hash` (Blossom sha256 of source)
- `confidence`
- `statement_period_start` / `statement_period_end` (if known)

Surface an "auto-filled from document" indicator in the UI and keep a previous-value snapshot for
one-tap revert.

### Idempotency / dedup

Idempotency key: `sha256(doc_hash + issuer_name + account_hint_last4 + period_end + new_balance)`
(adapt fields per doc type). If the same key was already applied, skip and mark `duplicate=true`.

---

## 12. Domain validation schemas (reference)

Carried over from the old OpenClaw schemas doc; useful as the server-side validation contract for
LLM output. `additionalProperties=false` everywhere; reject unknown keys.

### Credit account patch

```json
{
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

### Bill patch

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["id"],
  "properties": {
    "id": { "type": "string", "minLength": 1 },
    "name": { "type": "string", "minLength": 1, "maxLength": 120 },
    "amount": { "type": "number", "minimum": 0 },
    "frequency": {
      "type": "string",
      "enum": ["WEEKLY", "BIWEEKLY", "MONTHLY", "BIMONTHLY", "QUARTERLY", "SEMIANNUALLY", "ANNUALLY"]
    },
    "dueDay": { "type": "integer", "minimum": 1, "maximum": 31 },
    "recurrenceUnit": { "type": "string", "enum": ["DAY", "WEEK", "MONTH", "YEAR"] },
    "recurrenceIntervalCount": { "type": "integer", "minimum": 1, "maximum": 120 },
    "payFromBankAccountId": { "type": "string", "minLength": 1 },
    "payFromCreditAccountId": { "type": "string", "minLength": 1 }
  }
}
```

> Note: align `frequency` enum and field names with the live `Bill` model in
> `apps/web/web/src/lib/bill.ts` and `app/.../domain/model/Bill.kt` before implementation; the old
> schema used a slightly different enum set.

### Paystub extraction schema (LLM-facing, grammar-constrained)

This is what the **model emits** for a paystub. Dates are ISO strings (mapping converts to epoch ms);
the model must **not** emit `id`s; all amounts are **current-period**, not YTD. Use this as the GBNF /
JSON-schema constraint.

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.llm.paystub-extraction.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["payDate", "grossPay", "netPay", "earnings", "taxes", "confidence"],
  "properties": {
    "payDate": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}$" },
    "periodStart": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}$" },
    "periodEnd": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}$" },
    "currency": { "type": "string", "minLength": 3, "maxLength": 8 },
    "grossPay": { "type": "number", "minimum": 0 },
    "netPay": { "type": "number", "minimum": 0 },
    "earnings": { "type": "array", "items": { "$ref": "#/$defs/line" } },
    "taxes": { "type": "array", "items": { "$ref": "#/$defs/line" } },
    "preTaxDeductions": { "type": "array", "items": { "$ref": "#/$defs/line" } },
    "postTaxDeductions": { "type": "array", "items": { "$ref": "#/$defs/line" } },
    "employerContributions": { "type": "array", "items": { "$ref": "#/$defs/line" } },
    "notes": { "type": "string", "maxLength": 2000 },
    "confidence": {
      "type": "object",
      "additionalProperties": false,
      "required": ["overall"],
      "properties": {
        "overall": { "type": "number", "minimum": 0, "maximum": 1 },
        "payDate": { "type": "number", "minimum": 0, "maximum": 1 },
        "grossPay": { "type": "number", "minimum": 0, "maximum": 1 },
        "netPay": { "type": "number", "minimum": 0, "maximum": 1 }
      }
    }
  },
  "$defs": {
    "line": {
      "type": "object",
      "additionalProperties": false,
      "required": ["label", "amount"],
      "properties": {
        "label": { "type": "string", "minLength": 1, "maxLength": 80 },
        "amount": { "type": "number" },
        "hours": { "type": "number", "minimum": 0 }
      }
    }
  }
}
```

### PaycheckLogEntry domain schema (post-mapping validation)

Mirrors `app/.../domain/model/PaycheckLog.kt` and `apps/web/web/src/lib/salary.ts`. This is what the
**mapped** entry must validate against before it's merged into `SalaryConfig.paycheckLog` and
published. `id`s are assigned by the mapping layer (UUIDs); totals are recomputed via
`recomputeEntryTotals` (do not trust model-supplied totals).

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "fiatlife.paycheck-log-entry.v1",
  "type": "object",
  "additionalProperties": false,
  "required": ["id", "payDate", "grossPay", "netPay"],
  "properties": {
    "id": { "type": "string", "minLength": 1 },
    "payDate": { "type": "integer", "minimum": 0 },
    "grossPay": { "type": "number", "minimum": 0 },
    "netPay": { "type": "number" },
    "totalTaxes": { "type": "number", "minimum": 0 },
    "totalPreTaxDeductions": { "type": "number", "minimum": 0 },
    "totalPostTaxDeductions": { "type": "number", "minimum": 0 },
    "overtimeHours": { "type": "number", "minimum": 0 },
    "notes": { "type": "string", "maxLength": 2000 },
    "earnings": { "type": "array", "items": { "$ref": "#/$defs/lineItem" } },
    "taxes": { "type": "array", "items": { "$ref": "#/$defs/lineItem" } },
    "preTaxDeductions": { "type": "array", "items": { "$ref": "#/$defs/lineItem" } },
    "postTaxDeductions": { "type": "array", "items": { "$ref": "#/$defs/lineItem" } },
    "employerContributions": { "type": "array", "items": { "$ref": "#/$defs/lineItem" } },
    "attachmentHash": { "type": "string" },
    "attachmentLabel": { "type": "string" },
    "autoGenerated": { "type": "boolean" }
  },
  "$defs": {
    "lineItem": {
      "type": "object",
      "additionalProperties": false,
      "required": ["id", "label", "amount"],
      "properties": {
        "id": { "type": "string", "minLength": 1 },
        "label": { "type": "string", "maxLength": 80 },
        "amount": { "type": "number" },
        "hours": { "type": ["number", "null"], "minimum": 0 }
      }
    }
  }
}
```

> Field-name caveat: Android `PaycheckLineItem.hours` is nullable (`Double?`); both platforms ignore
> unknown JSON keys, so the entry is forward/backward compatible. `netPay` is intentionally not
> `minimum: 0` (reimbursement-heavy or corrected checks can theoretically net oddly, though normally
> positive); the anomaly checks in section 10 catch the suspicious cases.

### Paystub extraction GBNF grammar (llama.cpp)

Constrains decoding to the paystub-extraction schema above. Fixed key order (GBNF handles ordered keys
best); optional keys are wrapped in `( ... )?`. `confidence` is last and required, so every
comma-terminated field is always followed by a present field (no trailing-comma problem). Pass this
via the llama.cpp `grammar` parameter (or `--grammar-file`).

```gbnf
root        ::= "{" ws
                "\"payDate\":" ws date "," ws
                ( "\"periodStart\":" ws date "," ws )?
                ( "\"periodEnd\":" ws date "," ws )?
                ( "\"currency\":" ws string "," ws )?
                "\"grossPay\":" ws number "," ws
                "\"netPay\":" ws number "," ws
                "\"earnings\":" ws lines "," ws
                "\"taxes\":" ws lines "," ws
                ( "\"preTaxDeductions\":" ws lines "," ws )?
                ( "\"postTaxDeductions\":" ws lines "," ws )?
                ( "\"employerContributions\":" ws lines "," ws )?
                ( "\"notes\":" ws string "," ws )?
                "\"confidence\":" ws confidence ws
                "}"

lines       ::= "[" ws ( line ( ws "," ws line )* )? ws "]"
line        ::= "{" ws
                "\"label\":" ws string "," ws
                "\"amount\":" ws number
                ( ws "," ws "\"hours\":" ws number )? ws
                "}"

confidence  ::= "{" ws
                "\"overall\":" ws prob
                ( ws "," ws "\"payDate\":" ws prob )?
                ( ws "," ws "\"grossPay\":" ws prob )?
                ( ws "," ws "\"netPay\":" ws prob )?
                ws "}"

date        ::= "\"" digit digit digit digit "-" digit digit "-" digit digit "\""
number      ::= "-"? int ( "." digit+ )?
int         ::= "0" | [1-9] digit*
prob        ::= "0" ( "." digit+ )? | "1" ( "." "0"+ )?
string      ::= "\"" char* "\""
char        ::= [^"\\] | "\\" ( ["\\/bfnrt] | "u" hex hex hex hex )
hex         ::= [0-9a-fA-F]
digit       ::= [0-9]
ws          ::= [ \t\n]*
```

> Notes:
> - The grammar enforces *shape*, not *accuracy* or numeric ranges — keep the section 9.3 sum
>   cross-check and section 12 JSON-schema validation downstream.
> - `prob` deliberately only admits `0`, `0.x`, `1`, `1.0` to keep confidence in `[0,1]`.
> - If a future model build supports native JSON-schema-constrained decoding, prefer feeding the
>   section-12 extraction schema directly and drop this hand-written grammar.

### Paystub extraction prompt (Qwen2.5-Instruct)

Sent over the llama.cpp OpenAI-compatible `/v1/chat/completions` endpoint at **temperature 0**, with
the GBNF grammar attached. The user message carries the deterministically extracted paystub text
(pdfplumber, OCR fallback).

**System message:**

```text
You extract data from a single US pay stub and output ONLY a JSON object matching the required schema.

Hard rules:
- Use CURRENT PAY PERIOD values only. Ignore all year-to-date (YTD) columns/totals.
- Copy printed numbers exactly. Do NOT compute, sum, or reconcile totals yourself.
- Numbers are plain JSON numbers: no currency symbols, no thousands separators (1234.56, not "$1,234.56").
- Dates are ISO "YYYY-MM-DD". payDate is the check/pay date. Use periodStart/periodEnd only if clearly printed.
- grossPay and netPay are the printed current-period gross and net (take-home) pay.
- Put each money line under exactly one of: earnings, taxes, preTaxDeductions, postTaxDeductions, employerContributions.
- Normalize labels to these where they clearly match, else keep the stub's label (trimmed, <= 80 chars):
    earnings: Regular, Overtime, Bonus, Commission, Holiday, PTO, Tips, Reimbursement, Other
    taxes: Federal income tax, State income tax, Social Security, Medicare, Local tax
- Include "hours" on an earnings line only if the stub prints hours for it.
- Employer-paid items (e.g. employer 401k match, employer HSA) go in employerContributions, not deductions.
- Omit any optional field you cannot find. Do not invent values. Do not output ids.
- confidence.overall in [0,1]; add per-field confidence for payDate/grossPay/netPay when useful.
- Output JSON only. No prose, no markdown, no code fences.
```

**Few-shot (one example):**

User:

```text
PAY STUB
Employee: Jane Doe        Pay Date: 02/13/2026
Period: 01/31/2026 - 02/13/2026

EARNINGS        HOURS     CURRENT       YTD
Regular         80.00     2,000.00     8,000.00
Overtime         5.00       187.50       375.00

TAXES                     CURRENT       YTD
Federal Income Tax          262.13     1,048.52
Social Security             135.06       540.24
Medicare                     31.59       126.36
NY State Income Tax         103.40       413.60

DEDUCTIONS                CURRENT       YTD
401(k) Pre-Tax              130.94       523.76
Dental (post-tax)            12.50        50.00

EMPLOYER PAID             CURRENT       YTD
401(k) Match                 65.47       261.88

Current Gross: 2,187.50    Current Net: 1,511.88
YTD Gross: 8,375.00        YTD Net: 5,797.52
```

Assistant:

```json
{"payDate":"2026-02-13","periodStart":"2026-01-31","periodEnd":"2026-02-13","grossPay":2187.50,"netPay":1511.88,"earnings":[{"label":"Regular","amount":2000.00,"hours":80.00},{"label":"Overtime","amount":187.50,"hours":5.00}],"taxes":[{"label":"Federal income tax","amount":262.13},{"label":"Social Security","amount":135.06},{"label":"Medicare","amount":31.59},{"label":"State income tax","amount":103.40}],"preTaxDeductions":[{"label":"401(k) Pre-Tax","amount":130.94}],"postTaxDeductions":[{"label":"Dental","amount":12.50}],"employerContributions":[{"label":"401(k) Match","amount":65.47}],"confidence":{"overall":0.97,"payDate":0.99,"grossPay":0.98,"netPay":0.98}}
```

> The example deliberately includes YTD columns (to teach "current only") and a mappable label
> ("NY State Income Tax" -> "State income tax"). After the model returns, `recomputeEntryTotals` will
> derive grossPay (2000+187.50 = 2187.50) and netPay (2187.50 − 532.18 taxes − 130.94 pre − 12.50 post
> = 1511.88) and confirm they match the printed values within tolerance.

---

## 13. Other candidate use cases (later)

Medium value:
- Bill **statement line-item** extraction into `statementEntries` / itemization (beyond the total).
- **Auto-categorization** of bills left uncategorized (small, low-risk LLM task).
- **Biller normalization & dedup** (good small-model task).

Nice-to-have:
- **Natural-language entry**: "add my $14.99 Netflix billed on the 3rd" -> proposed bill (same
  pipeline minus the PDF step).
- **Local Q&A over your own data**: the server can decrypt kind-30078 records, so a local model can
  answer "how much did I spend on utilities last quarter?" with nothing leaving the box.
- **Anomaly/insight surfacing**: phrase explanations for the threshold checks in section 10.

---

## 14. Rollout plan

1. **Shadow mode**: upload -> extract -> propose into the review sheet, **no auto-publish**. Validate
   extraction quality on real documents and tune prompts/grammar.
2. **Paystubs + credit statements** with high-confidence one-tap accept; everything else stays
   manual-review.
3. **General bills**, with biller matching/dedup.
4. Only then consider an off-box / NIP-46 path if parsing ever needs to run somewhere other than the
   Start9 box (re-introduce the relevant signing design at that point).

---

## 15. Open questions / to decide

- Where does the PDF text-extraction + OCR script run? (Sidecar process vs in the Rust server vs a
  small Python service.)
- Exact llama.cpp grammar definitions per doc type (derive GBNF from the section 12 JSON schemas).
- Final auto-accept confidence thresholds (start conservative).
- Whether to do per-document-type prompts (recommended) vs one general prompt.
- Confirm `Qwen2.5-14B-Instruct` CPU latency is acceptable on real paystubs in shadow mode; if not,
  fall back to `Qwen2.5-7B-Instruct` (same prompt + grammar) — section 4.2.
- Sum-cross-check tolerance for paystubs (absolute vs relative epsilon) — section 9.3.
```