# Credit & Loans Tab — Implementation Plan

## Implementation status (Phases 1–4 complete)

- **Tab name**: Implemented as **"Debt"** in bottom nav (route `debt`, detail `debt_detail/{accountId}`).
- **Phase 1**: Done — `CreditAccount` / `CreditAccountType`, `CreditAccountEntity`, `CreditAccountDao`, DB v3 + migration, `CreditAccountRepository` with Nostr sync (`fiatlife/credit/`). Sync wired in `MainActivity.syncFromRelay()`.
- **Phase 2**: Done — `Screen.Debt` and `Screen.DebtDetail`, Debt in bottom nav, `DebtScreen` (summary card: total debt, monthly payment, credit available/utilization when applicable; account list; FAB), `DebtViewModel` (state + sync on connect).
- **Phase 3**: Done — Add/Edit via `CreditAccountDialog` (type, name, institution, last 4, APR, balance, due day; revolving: limit + min payment type/value; amortizing: original principal, term months, monthly payment; notes). Save/delete via repository (Nostr sync in repository).
- **Phase 4**: Done — `DebtDetailScreen` (overview: balance, limit if revolving, APR, due day, monthly/min payment, principal/term if amortizing; Edit and Delete). `DebtDetailViewModel` loads by id and supports save/delete.
- **Phase 5** (link to Bills) and **Phase 6** (statement uploads) are not yet implemented.

---

## Overview

A new bottom navigation tab **"Credit"** (or **"Credit & Loans"**) dedicated to tracking all lines of credit and loans: credit cards, mortgages, car loans, student loans, personal loans, HELOCs, 401k/IRA loans, etc. It provides:

- **Totals**: Total credit available, total utilized, utilization %, total debt, total monthly payment
- **Per-account**: Balance/outstanding, APR, payment due, payment schedule where applicable
- **Link to Bills**: Monthly payment can be represented as a bill; tapping that bill opens the linked credit/loan account

---

## 1. Data Model

### 1.1 Account type (enum)

```text
CreditAccountType:
  CREDIT_CARD      — Revolving (limit, balance, min payment)
  MORTGAGE         — Amortizing (principal, term, fixed/ARM)
  CAR_LOAN         — Amortizing, fixed payment
  STUDENT_LOAN     — Amortizing (federal/private, term, payment)
  PERSONAL_LOAN    — Amortizing or simple
  HELOC            — Line of credit (draw limit, current draw, rate)
  RETIREMENT_LOAN  — 401k/IRA (term, payment, special rules)
  OTHER            — Catch-all
```

### 1.2 Core entity: `CreditAccount` (domain model)

**Common fields (all types):**

| Field | Type | Notes |
|-------|------|--------|
| id | String | UUID |
| name | String | e.g. "Chase Sapphire", "House mortgage" |
| type | CreditAccountType | |
| institution | String? | Bank/lender name |
| accountNumberLast4 | String? | Optional, last 4 digits |
| apr | Double | Annual rate (e.g. 0.1999) |
| currentBalance | Double | Outstanding amount owed |
| dueDay | Int | Day of month payment is due (1–31) |
| linkedBillId | String? | Id of Bill that represents the monthly payment (for Bills tab + deep link) |
| notes | String | |
| createdAt / updatedAt | Long | |
| statementEntries | List\<StatementEntry\> | Same as Bill: hash, label, addedAt — statements uploaded to Blossom for this account. |
| attachmentHashes | List\<String\> | Legacy/optional: list of Blossom hashes if not using statementEntries. |

**Revolving (credit card, HELOC):**

| Field | Type | Notes |
|-------|------|--------|
| creditLimit | Double | Total line available |
| minimumPaymentType | Enum | FIXED / PERCENT_OF_BALANCE / FULL_BALANCE (reuse from Bill) |
| minimumPaymentValue | Double | $ or % |

**Amortizing (mortgage, car, student, personal, retirement):**

| Field | Type | Notes |
|-------|------|--------|
| originalPrincipal | Double | Original loan amount |
| termMonths | Int? | Loan term |
| monthlyPaymentAmount | Double? | Fixed payment (if known) |
| startDate | Long? | First disbursement |
| endDate | Long? | Final payment (optional) |

**Type-specific (optional, can phase later):**

- Mortgage: escrow amount, fixed vs ARM
- HELOC: draw limit vs separate “credit limit” semantics
- Retirement: employer plan name, repayment rules

**Payment schedule (optional but valuable):**

- Store either: **amortization snapshot** (list of remaining principal by period) or **next N payments** (date + principal + interest).
- Alternative: compute on the fly from balance, APR, term, and payment amount (standard amortization formula).

Recommendation: start with **currentBalance, apr, dueDay, monthlyPaymentAmount** (or min payment for revolving). Add **amortization/schedule** in a later phase (e.g. “Payment schedule” section that shows next 12 months or full schedule).

### 1.3 Link to Bills

- **CreditAccount.linkedBillId** → `Bill.id`
- **Bill** (optional): add **linkedCreditAccountId** → `CreditAccount.id` for reverse lookup and navigation.

When `linkedBillId` is set:

- The Bill’s amount (or effective amount for credit cards) should reflect the **monthly payment** for that credit account (editable on the Bill or synced from Credit account, TBD).
- Tapping the Bill (from Bills list or Bill detail) navigates to **Credit tab → that account’s detail** (or selects it in the list and opens detail).

---

## 2. Persistence & Sync

- **Room**: New table `credit_accounts` (same pattern as bills: e.g. `id`, `jsonData`, `type` string, `updatedAt`), or normalized columns. Prefer **one row per account**, JSON for flexible type-specific fields, to avoid schema churn.
- **Nostr**: Same pattern as bills — encrypt and publish as kind 30078 (or dedicated kind), keyed by account id; subscribe and merge on sync; support delete/tombstone.
- **Repository**: `CreditAccountRepository` with `getAll()`, `getById()`, `save()`, `delete()`, `syncFromNostr()`.

---

## 3. UI Structure

### 3.1 Bottom navigation

- Add **Credit** (or **Credit & Loans**) as a new `Screen` in `Screen.kt`.
- Add to `bottomNavItems` (e.g. between Bills and Goals, or after Goals).
- Icon: e.g. `Icons.Filled.CreditCard` or `Icons.Filled.AccountBalance`.

### 3.2 Credit tab — main list screen

- **Summary card (top)**  
  - Total credit available (sum of `creditLimit` for revolving; optional for non-revolving).  
  - Total credit utilized (sum of `currentBalance` for revolving).  
  - Utilization % (utilized / available).  
  - Total debt (sum of `currentBalance` for all accounts).  
  - Total monthly payment (sum of min payment or monthly payment for each account).

- **Account list**  
  - Grouped or flat list of accounts (e.g. by type: Credit cards, then Loans).  
  - Each row: icon by type, name, balance, APR, “$X/mo” or “Min $X”, optional “Linked” badge if `linkedBillId != null`.  
  - Tap row → **CreditAccountDetailScreen** (same pattern as BillDetail).

- **FAB** → Add new credit/loan account (opens add flow).

### 3.3 Credit account detail screen

- **Route**: e.g. `credit_detail/{accountId}`.
- **Header**: Name, type chip, institution (if any).
- **Overview**: Current balance, credit limit (if revolving), APR, next payment due (dueDay), monthly/min payment.
- **Link to Bill**: If `linkedBillId` set, show “Monthly payment tracked as: [Bill name]” with navigation to that Bill (or to Bills tab with highlight). If not set, “Link to bill” action to pick a bill or create one.
- **Statements**: Section “Statements” (same pattern as Bill detail): list of attached statements (PDF/image) stored on Blossom; “Attach statement” when adding/editing to upload to Blossom and add a `StatementEntry` to this account. Lets users keep per-account statements with the line of credit/loan.
- **Payment schedule** (phase 2): Section “Upcoming payments” or “Amortization” (next N payments or full schedule).
- **Payment history** (optional): List of recorded payments (could be inferred from linked Bill’s payment history, or stored on CreditAccount).
- **Actions**: Edit, Delete.

### 3.4 Add/Edit credit account

- **Dialog or full screen**: Form driven by **type**.
  - Common: name, type, institution, APR, current balance, due day, notes.
  - Revolving: credit limit, minimum payment rule (reuse existing min payment type/value).
  - Amortizing: original principal, term months, monthly payment (optional), start date.
- **Link to bill**: Optional step “Link to an existing bill” (picker of bills) or “Create a bill for this payment” (creates a Bill and sets `linkedBillId` / `linkedCreditAccountId`).

### 3.5 Bills tab ↔ Credit tab integration

- **Bill list / detail**:  
  - If `bill.linkedCreditAccountId != null` (or we resolve by `CreditAccount.linkedBillId == bill.id`), show a chip or subtitle “Credit: [Account name]” and make it tappable → navigate to Credit tab and open that account (e.g. `credit_detail/{accountId}`).
- **BillDialog (add/edit)**:  
  - Optional field “Link to credit/loan account” (dropdown of credit accounts). When selected, set `linkedCreditAccountId` on Bill and `linkedBillId` on CreditAccount (both sides updated).
- **Credit account detail**:  
  - “Monthly payment in Bills: [Bill name]” → tap navigates to Bill detail.

Navigation implementation: use the same `NavController`; from Bills tab pass a route that includes `accountId` (e.g. `credit_detail/xyz`). If user is on Bills and taps a linked bill, navigate to `credit_detail/{id}`; the Credit screen might need to be the start of a stack so that “Credit” tab is visible and the detail is open (e.g. navigate to Credit tab with a route that includes the selected account).

---

## 4. Implementation Phases

### Phase 1 — Foundation (no new tab yet, or minimal tab)

- [x] Domain: `CreditAccountType`, `CreditAccount` (common + revolving + amortizing fields), `CreditAccountRepository` interface.
- [x] Local: `CreditAccountEntity`, `CreditAccountDao`, DB migration (add table), repository impl (CRUD + JSON serialize/deserialize).
- [x] Nostr: Publish/subscribe/delete for credit accounts (same pattern as bills); repository `syncFromNostr()`.

### Phase 2 — Credit tab and list

- [x] Nav: Add `Screen.Debt` and `Screen.DebtDetail`, add Debt to bottom nav, add `DebtScreen` and `DebtDetailScreen` composables.
- [x] DebtScreen: Summary card (totals: available, utilized, utilization %, total debt, total monthly payment), list of accounts (from repository), FAB to add.
- [x] DebtViewModel: State (list, summary), load from repository.

### Phase 3 — Add/Edit account

- [x] Add/Edit dialog or screen: Form for type, name, APR, balance, due day; for revolving add limit + min payment rule; for amortizing add principal, term, monthly payment.
- [x] Save/update/delete in repository and sync.

### Phase 4 — Detail screen and payment schedule (basic)

- [x] DebtDetailScreen: Overview (balance, limit, APR, due day, monthly/min payment), edit/delete.
- [ ] Optional: “Next payment” or “Next 3 payments” (date + amount) computed from balance, APR, and payment amount (simple amortization or min payment).

### Phase 5 — Link to Bills

- [ ] Add `linkedBillId` to `CreditAccount` and `linkedCreditAccountId` to `Bill` (both in domain and persistence).
- [ ] Bill list/detail: show “Credit: [name]” when linked; tap → navigate to Credit tab + open that account detail.
- [ ] Credit detail: show “Tracked in Bills as: [bill name]”; tap → navigate to Bill detail.
- [ ] BillDialog: optional “Link to credit/loan” picker; on save, update both Bill and CreditAccount.
- [ ] When creating a credit account with “Create a bill for this payment”, create the Bill and set both link fields.

### Phase 6 — Statement uploads (Blossom) per account

- [ ] Add `statementEntries` (and optionally `attachmentHashes`) to `CreditAccount`; persist and sync like other fields.
- [ ] Credit account detail: “Statements” section listing attached statements (label, date); “View” opens file from Blossom (same flow as Bill statements).
- [ ] Add/Edit or detail: “Attach statement (PDF/Image)” — pick file, upload to Blossom via existing Blossom client, append `StatementEntry` to account, save and sync. Reuse the same Blossom upload path used for bills.

### Phase 7 — Polish and extra types

- [ ] Payment history on credit account (or rely on linked bill’s payment history and show “See payment history in Bills”).
- [ ] HELOC, retirement loan: specific fields if needed.
- [ ] Notifications: e.g. “Credit payment due in 3 days” (could reuse bill reminder for linked bill, or add a separate reminder for accounts without a linked bill).

---

## 5. Migration from current Bills credit cards

- Existing bills with `category == CREDIT_CARD` and `creditCardDetails != null` can stay as-is in Bills.
- Option A: **No migration** — new credit card tracking lives only in Credit tab; user can add the same card in Credit and link the existing bill.
- Option B: **One-time migration** — for each Bill with creditCardDetails, create a CreditAccount (type CREDIT_CARD) from it and set `linkedBillId = bill.id`, and optionally set `linkedCreditAccountId` on the Bill. Then the Credit tab becomes the source of truth for balance/APR; the Bill remains the “payment reminder” and amount due can be synced from the credit account’s minimum due.

Recommendation: **Phase 1–5 first**, then decide migration (or keep both: Bills for “what’s due this month” and Credit for “all my debt and utilization”).

---

## 6. File / module layout (suggested)

```text
domain/model/
  CreditAccount.kt       — CreditAccountType, CreditAccount (and any type-specific data classes)
data/local/entity/
  CreditAccountEntity.kt
data/local/dao/
  CreditAccountDao.kt
data/repository/
  CreditAccountRepository.kt
ui/screens/credit/
  CreditScreen.kt        — List + summary
  CreditDetailScreen.kt  — Single account detail
  CreditAccountDialog.kt — Add/Edit (or CreditAccountForm.kt)
ui/viewmodel/
  CreditViewModel.kt
  CreditDetailViewModel.kt
navigation/
  Screen.kt              — Add Credit, CreditDetail
  NavGraph.kt            — Register routes and composables
```

---

## 7. Summary

| Area | Approach |
|------|----------|
| **Data** | Single `CreditAccount` model with type enum and optional revolving/amortizing fields; stored as JSON row in Room; synced via Nostr like bills. |
| **Totals** | Sum credit limit (revolving), sum balance (utilized + total debt), sum monthly/min payment. |
| **Bills link** | `CreditAccount.linkedBillId` and `Bill.linkedCreditAccountId`; both sides updated when linking; navigation from Bill → Credit account and from Credit account → Bill. |
| **Statements** | Per-account statements uploaded to Blossom (same as Bills); `statementEntries` on `CreditAccount`; Phase 6. |
| **Future agent** | An AI agent could parse statements and publish updated credit/loan data so the app shows fresh data when the user opens it. Signing options (NIP-46 bunker vs NIP-26 delegation), Blossom, and implementation are covered in **[AGENT_SIGNING_AND_CREDIT_LOANS.md](AGENT_SIGNING_AND_CREDIT_LOANS.md)**. |
| **Phasing** | 1) Data + persistence + sync; 2) Tab + list + summary; 3) Add/edit; 4) Detail + basic schedule; 5) Bill linking; 6) Statement uploads (Blossom); 7) Polish and extra types. |

This structure keeps the scope clear, reuses existing patterns (Bills, Nostr, Room), and gives you a path to implement incrementally. If you want to start coding, Phase 1 (data model + repository + DB + sync) is the right first step, then Phase 2 (tab + list + summary).

---

## 8. Statement uploads to Blossom (per account)

Each credit/loan account can have **statements** attached in the same way bills do: the user picks a file (PDF or image), the app uploads it to the configured Blossom server (BUD-01), and stores a reference on the account (`statementEntries`: hash, label, addedAt). The detail screen shows a “Statements” section and “Attach statement”; viewing downloads from Blossom and opens the file. This gives a single place for all statements tied to that line of credit/loan and keeps them in the user’s own Blossom storage. Implementation is Phase 6 above; no new infra beyond what Bills already use.

---

## 9. Future: AI agent (credit/loans only)

An **AI agent** could one day parse statements (e.g. from email or Blossom), extract balance/APR/payments, and publish updated credit/loan events to the relay so that the next time the user opens FiatLife they see updated data. The agent cannot hold the user’s main key, so signing must be done via **delegation** (e.g. NIP-26 sub-key from cypherlog) or **remote signing** (e.g. NIP-46 bunker). Which option to use, how Blossom uploads by the agent could work, and implementation notes are covered in a separate doc: **[AGENT_SIGNING_AND_CREDIT_LOANS.md](AGENT_SIGNING_AND_CREDIT_LOANS.md)**.
