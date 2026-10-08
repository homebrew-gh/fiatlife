# Transactions and Spending — plan

Status: **planning, no code yet.** Last updated: October 2026.

Bring bank and card transactions into FiatLife from SimpleFIN, show them in a new **Spending** tab
(budget + transactions), categorize them privately, and use them to fill in data the user types by
hand today (variable budget spend, bills marked paid, paycheck deposits).

**Scope:** `apps/web/server` (fetch, store, publish, AI relay), `apps/web/web` and `app/` (UI,
categorization, rules), `packages/start9` (AI endpoint detection, later).

**Related:** [LOCAL_LLM_INGESTION_PLAN.md](LOCAL_LLM_INGESTION_PLAN.md) (on-box llama.cpp for
documents) · [RELAY_SYNC_RELIABILITY.md](RELAY_SYNC_RELIABILITY.md) · [NIP.md](../NIP.md) (d-tags) ·
EnergyRadianceVitality `docs/architecture/AI_COACH_MAPLE_INTEGRATION.md` (the AI pipeline to port).

---

## 0. Recommendations (read this first)

1. **Four bottom tabs: Home · Spending · Bills · Accounts.** Paycheck and Goals stop being tabs and
   open from cards on Home. Net Worth and Debt merge into Accounts. Same grouping on the web.
2. **One SimpleFIN request does balances and transactions.** Drop `balances-only=1` and pass a date
   window instead, so transactions cost no extra quota. Never more than one fetch every few hours.
3. **The server fetches and publishes transactions; clients only read and annotate.** Only the
   Start9 server has SimpleFIN credentials. Android never talks to SimpleFIN or to the AI.
4. **One writer per record.** The server writes transaction records; clients write a separate
   edits record. A sync can then never overwrite a category the user picked.
5. **Categorize in layers, cheapest and most private first:** match against FiatLife's own data
   (bills, transfers, paychecks) → merchant rules learned from the user → AI only for merchants
   nothing else recognized.
6. **AI is optional, off by default, and ships last** (decided). It sends only cleaned-up merchant
   names, never amounts, dates, account names or balances. Its answers are suggestions until the
   user confirms them, and every confirmation becomes a rule, so AI calls shrink over time.
7. **The AI client is provider-agnostic.** One OpenAI-compatible client serves Maple Proxy (off-box,
   in Maple's encrypted enclave, billed to the user's Maple account) and a local llama.cpp model on
   the Start9 box (nothing leaves the box). Merchant categorization is a small task that a small
   local model handles well, so prefer local when it's installed.

---

## 1. Navigation

Six bottom tabs today (Home, Bills, Paycheck, Debt, Goals, Budget) is above the 3–5 Material
recommends, and there's no room for transactions.

| Tab | Contains | Today |
|-----|----------|-------|
| **Home** | Dashboard; Paycheck and Goals summary cards that open the full screens; "missing paycheck" chip | Home |
| **Spending** | Month summary, budget rows backed by transactions, Needs review queue, transaction list | Budget |
| **Bills** | Unchanged | Bills |
| **Accounts** | Net worth hero and breakdown, then debt accounts (Debt Planner from here), bank accounts, bitcoin | Debt + Net Worth |

- **Paycheck** and **Goals** become full screens with a back button (like Debt Planner today).
  Routes stay the same so deep links and dashboard chips keep working.
- **Accounts** absorbs today's Debt tab content below the net worth summary. Bank account editing
  stays in Settings for now; Accounts rows link there.
- **Web:** `AppShell.tsx` `TABS` becomes Home / Spending / Bills / Accounts. `/app/paycheck`,
  `/app/goals`, `/app/debt` and `/app/net-worth` keep working as routes; `/app/budget` redirects to
  `/app/spending`.
- **Android:** `Screen.bottomNavItems` becomes `Dashboard, Spending, Bills, Accounts`; `Salary`,
  `Goals` and `NetWorth` join `hideGlobalTopBar` with their own back buttons.

This ships first, independently of transactions (milestone T0).

---

## 2. Getting transactions from SimpleFIN

### 2.1 Limits that shape the design

From the SimpleFIN Bridge developer guide:

- **90 days max per request** (`end-date − start-date`). History available varies by bank.
- **About 24 requests a day**, quota replenished through the day. Going well over it first adds
  warnings to `errors`, then **disables the access token**.
- Overlap windows by **~5 days** so late-posting transactions aren't missed.
- `pending=1` includes pending transactions (where the bank supports it).

### 2.2 Fetch policy

- **First sync:** one request, `start-date = now − 90 days`.
- **Later syncs:** `start-date = last successful fetch − 10 days`. Merge by transaction id.
- **Throttle:** the server refuses to call SimpleFIN more than once every **4 hours** unless the
  user presses "Sync now" (still capped at, say, 12 calls/day). The existing balance refresh uses
  the same request, so this also caps balance fetches.
- **When:** on demand while the web session is unlocked, same as balances today (the server only
  holds the nsec while unlocked). Scheduled background fetch is out of scope (§10).
- **Pending:** include pending. A pending transaction usually reappears posted with a **new id**;
  drop a pending row once a posted row with the same account, amount and description (±3 days)
  shows up, and drop any pending row that is absent from a later fetch covering its date.

### 2.3 Server changes (`apps/web/server`)

- `simplefin.rs`: `fetch_accounts(access_url, window)` replaces `fetch_balances`. Parse each
  account's `transactions[]` (`id`, `posted`, `amount`, `description`, `payee`, `memo`,
  `transacted_at`, `pending`). Keep `BalancesSnapshot` as the balance half of the result.
- Keep an **encrypted local copy** of known transactions (encrypted to self, like
  `last_snapshot_nip44`) so merges don't need a relay round trip. Store it beside `state.json`
  rather than inside it, since it grows.
- After each fetch, publish the changed month records (§3) through the existing outbox.
- `fiatlife_tags.rs`: allow `fiatlife/txn/`, `fiatlife/txn-edits/`, `fiatlife/txn-rules`.
- Document the new d-tags in `NIP.md`.

---

## 3. Storage and sync

One relay event per transaction would mean thousands of events. Group them instead.

### 3.1 Records

| d-tag | Written by | Content |
|-------|-----------|---------|
| `fiatlife/txn/<YYYY-MM>/<part>` | Server only | Transactions posted that month, all accounts |
| `fiatlife/txn-edits/<YYYY-MM>` | Clients only | User and AI annotations for that month's transactions |
| `fiatlife/txn-rules` | Clients only | Merchant → category rules |

- **No account identifiers in d-tags.** d-tags are visible on relays; the month and the number of
  parts are the only things exposed.
- **Size:** NIP-44 plaintext is capped at 65,535 bytes. With short field names a transaction is
  ~200 bytes, so a part holds ~300. Split a month into parts `0`, `1`, … when it's bigger; publish a
  deletion for any parts a month no longer needs.

### 3.2 Transaction record (server-written)

```json
{
  "v": 1,
  "month": "2026-10",
  "part": 0,
  "fetchedAt": 1791500000000,
  "transactions": [
    {
      "id": "TRN-abc123",
      "account": "CON-1:ACT-9",
      "posted": 1791400000,
      "amount": -42.17,
      "description": "SQ *BLUE BOTTLE COFFEE OAKLAND CA",
      "payee": "Blue Bottle Coffee",
      "memo": "",
      "pending": false
    }
  ]
}
```

- `account` is the SimpleFIN key (`conn_id:account_id`) already used as `simplefinAccountKey` on
  bank and credit accounts, so clients can show the FiatLife account name.
- `amount` is SimpleFIN's sign: negative = money out of the account, positive = money in. On a
  credit card a purchase is negative too (balance owed goes up).
- Transaction ids are unique per account, so the client key is `account + "/" + id`.

### 3.3 Edits record (client-written)

```json
{
  "v": 1,
  "month": "2026-10",
  "edits": {
    "CON-1:ACT-9/TRN-abc123": {
      "category": "DINING",
      "source": "user",
      "note": "",
      "excluded": false,
      "billId": null,
      "updatedAt": 1791500000000
    }
  }
}
```

- `source`: `user` (picked or confirmed by the user) · `ai` (suggestion, not yet confirmed).
- `excluded`: hide from spending totals (transfer, refund pair, duplicate).
- `billId` / `paycheckId`: set when matched (§4.1).
- **Merge on write:** read the latest record, merge per entry by `updatedAt`, write. Two devices
  editing the same month only collide on the same transaction.
- Rule-based and match-based categories are **not stored**; they're computed when displaying. Only
  user choices and AI suggestions are stored.

### 3.4 Rules record (client-written)

```json
{
  "v": 1,
  "rules": {
    "blue bottle coffee": { "category": "DINING", "source": "user", "updatedAt": 1791500000000 }
  }
}
```

Keys are normalized merchant names (§4.2). Same per-entry merge as edits.

### 3.5 Clients

- **Web:** `lib/transactions.ts` (types, parse, normalize, categorize), `lib/transactionsData.tsx`
  (provider like `bankAccountsData`).
- **Android:** Room tables for transaction months, edits and rules (same `jsonData` pattern as
  `BitcoinWalletEntity`), repositories that sync the three prefixes, added to the sync lists in
  `MainActivity` and `MainAppViewModel`. Android reads transactions and writes edits and rules;
  it never writes `fiatlife/txn/`.
- Both platforms model every field, per the cross-platform rule, so a re-save never drops data.

---

## 4. Categorization

Categories reuse the budget's keys so totals land in the right budget row:

- **Variable:** `GROCERIES`, `DINING`, `TRANSPORTATION`, `ENTERTAINMENT`, `SHOPPING`,
  `PERSONAL_CARE`, `SAVINGS`, `MISC` (`VARIABLE_BUDGET_CATEGORIES`).
- **Bills:** each `BillGeneralCategory` (`AUTO`, `UTILITIES`, `HOME`, `HEALTH`, `CREDIT_LOANS`,
  `SUBSCRIPTION`, `PERSONAL`, `OTHER`).
- **New, not budgeted:** `INCOME`, `TRANSFER`, `UNCATEGORIZED`.

A transaction's category is the first of these that applies:

1. User edit in the edits record (`source: user`).
2. Deterministic match (§4.1).
3. Merchant rule (§4.2).
4. AI suggestion (§5), shown as "Suggested" until confirmed.
5. `UNCATEGORIZED` → shows in Needs review.

### 4.1 Deterministic matching (no AI)

Run on the client, in this order. All are suggestions in v1 (one tap to confirm in Needs review);
auto-apply above a confidence threshold is a later optimization.

- **Transfers between your own accounts.** An outflow in one linked account and an inflow of the
  same absolute amount in another within 3 days → both `TRANSFER`, excluded from spending.
- **Credit card payments.** An outflow from checking that pairs with an inflow on a linked
  revolving credit account (same amount, ±3 days) → `TRANSFER`. The card's purchases already count
  as spending, so the payment must not count again. It still offers to mark the card's bill paid
  (`billId` set). If the card is unlinked, match the bank description against the card's name or
  biller.
- **Bill payments.** An outflow whose merchant matches a bill's biller or name, amount within 10%
  (or exactly the statement amount for cards and loans), dated within ±5 days of the due date →
  that bill's category with `billId` set. Confirming records a `BillPayment` on the bill.
  Installment loans and mortgages count here, as bills, not as transfers.
- **Paychecks.** An inflow within $1 of a logged paycheck's net pay, ±3 days of its pay date →
  `INCOME` with `paycheckId`. A payroll-looking deposit with no logged paycheck can prompt "Log
  this paycheck?".

`BillPayment` gains an optional `transactionId` (both platforms) so a payment is never recorded
twice.

### 4.2 Merchant normalization and rules

Bank descriptions are noisy (`SQ *BLUE BOTTLE COFFEE OAKLAND CA`, `POS DEBIT 10/03 STARBUCKS
#1234`). One deterministic `normalizeMerchant(description, payee)` with **shared test vectors** on
both platforms:

- Prefer `payee` when SimpleFIN provides it.
- Lowercase; strip processor prefixes (`sq *`, `tst*`, `paypal *`, `pos debit`, `ach debit`, …),
  store numbers (`#1234`), dates, card suffixes, trailing city/state and long digit runs; collapse
  whitespace.

Rules come from the user: recategorizing a transaction offers **"Always use this for
<merchant>"** (default on). That writes a rule, and every past and future transaction from that
merchant follows it unless individually edited.

---

## 5. AI categorization (milestone T4 — after rules are working)

### 5.1 What's sent and what comes back

Only merchants with no edit, match or rule, deduplicated, at most 100 per request:

```json
{
  "task": "categorize_merchants",
  "categories": [{ "key": "GROCERIES", "label": "Groceries" }, "..."],
  "merchants": [
    { "name": "blue bottle coffee", "direction": "out" },
    { "name": "costco whse", "direction": "out" }
  ]
}
```

- **Never sent:** amounts, dates, account names or numbers, balances, notes, raw descriptions.
  `direction` (money in or out) is the only extra signal.
- **Returns:** `{ "results": [{ "name": "...", "category": "DINING", "confidence": 0.9 }] }`.
  Unknown category keys or names not in the request are dropped. One automatic retry on invalid
  JSON (same policy as ERV).
- Results are written to the edits record as `source: ai` and shown as **Suggested** in Needs
  review. Confirm one → it becomes `source: user` and (by default) a rule. "Confirm all" is
  available.

### 5.2 Pipeline (port from EnergyRadianceVitality)

Port, don't redesign: ERV already ships these pieces for Maple Proxy.

| Piece | ERV source | FiatLife use |
|-------|-----------|--------------|
| StartOS detection | `packages/start9/startos/ai.ts`, `main.ts` probe | Detect `maple-proxy` and a local llama.cpp package |
| Provider client | `ai_provider.rs` (OpenAI-compatible) | Same; providers `OFF` / `MAPLE` / `LOCAL` / `OPENAI_COMPAT` |
| Settings + sealed key | `state.rs` `ai`, `crypto.rs` nsec-derived seal | Same; key held in the proxy by default |
| Relay routes | `ai_routes.rs`, `ai_queue.rs` | `POST /api/ai/categorize` (non-streaming JSON), settings, test, models |
| Server-owned prompts | `ai_prompts.rs` + `PROMPT_VERSION` | One categorization prompt |
| Settings UI | `AiSettingsPanel.tsx` | Settings → AI (web only) |
| Context preview | `ContextPreviewModal.tsx` | Shows the exact merchant list before sending |

- **Web only.** The AI endpoints are only reachable inside the Start9 network, and Android doesn't
  talk to the server. Android sees results through the synced edits record.
- **Server checks:** unlocked session, body size cap, request must match the shape above (reject
  anything with digits that look like amounts or account numbers beyond store-number patterns),
  no logging of request bodies.
- **Shared with the document plan:** [LOCAL_LLM_INGESTION_PLAN.md](LOCAL_LLM_INGESTION_PLAN.md)
  needs the same client, settings and queue for llama.cpp. Build them once here; that plan reuses
  them.

### 5.3 Provider choice

| Provider | Where data goes | Cost | Fit |
|----------|-----------------|------|-----|
| Local llama.cpp | Nowhere; stays on the Start9 box | CPU time | Good: short task, small model (Gemma 3n E4B / Qwen2.5-7B) is enough |
| Maple Proxy | Off-box, encrypted to Maple's enclave | Maple account credits | Good; use when no local model is installed |
| Custom OpenAI-compatible | Wherever the user points it | Varies | Allowed, labelled "self-hosted" |

---

## 6. Spending tab

- **Header:** month picker; spent vs budgeted; left to spend.
- **Needs review** chip with a count (uncategorized + suggested + unconfirmed matches). Opens a
  queue: one transaction at a time with category chips, "Always use this for <merchant>", and
  Confirm / Skip. Suggested matches show "Mark *Electric* paid?".
- **Budget rows** (today's Budget screen): actuals come from transactions; tap a row → that
  category's transactions.
- **Transactions list:** grouped by day, search, filter by account and category, pending shown
  muted. Tap → detail sheet: category, rule toggle, note, exclude, linked bill or paycheck.
- **Empty state** when SimpleFIN isn't connected: explain the web setup, keep manual budget entry
  working.

---

## 7. Budget integration

- **Variable categories:** actual = this month's outflows in that category (excluded and
  `TRANSFER` rows left out; refunds, i.e. inflows in a spending category, reduce it) **plus**
  `manualSpent`. Once transactions are on, `manualSpent` is relabelled **"Cash and other spending"**
  for money that doesn't go through a linked account. Its monthly reset stays.
- **Bill categories:** keep the projected monthly amount and add **paid so far** from matched
  transactions. A credit card payment still marks the card's bill paid, but counts as a transfer,
  not spending, because the card's purchases are already counted in their own categories.
- **Income:** matched paycheck deposits can feed the dashboard's "logged take-home".
- No change to the `fiatlife/budget` record shape.

---

## 8. Privacy and safety

| Control | Where |
|---------|-------|
| Transactions encrypted (NIP-44 to self) like every other record; d-tags carry only the month | Server publish |
| SimpleFIN credentials and raw fetch stay on the Start9 server | Server |
| Quota guard so the access token is never disabled | Server throttle |
| AI off by default; disclosure text names where data goes (on-box vs Maple enclave) | Settings |
| AI sees cleaned merchant names + direction only; preview shows the exact list | Web + server check |
| No request bodies in server logs | `ai_routes.rs` |
| AI output is a suggestion until confirmed; never edits bills or paychecks on its own | Edits record `source: ai` |
| Matches (bill paid, paycheck) are one-tap confirms in v1 | Needs review |

---

## 9. Milestones

### T0 — Navigation

- [x] Android: `bottomNavItems` = Home, Spending, Bills, Accounts; Paycheck, Goals, Net Worth as back-button screens
- [x] Android: Accounts screen = net worth summary + today's Debt content
- [x] Android: Home cards for Paycheck and Goals (existing cards now push the screens)
- [x] Web: `AppShell` tabs; `/app/budget` → `/app/spending`, `/app/debt` → `/app/accounts`; Accounts route
- **Acceptance:** every screen reachable before is reachable in at most two taps; dashboard chips still deep-link.

### T1 — Fetch, store, show (read-only)

- [ ] Server: windowed fetch with transactions, merge, pending handling, throttle
- [ ] Server: encrypted local copy; publish `fiatlife/txn/<month>/<part>`; tag allowlist; `NIP.md`
- [ ] Web + Android: parse and sync month records; read-only transaction list in Spending
- [ ] Tests: 90-day window math, merge/dedupe, pending → posted, part splitting under 64 KB
- **Acceptance:** after "Sync now" on the web, the last 90 days appear on web and Android; a second sync within 4 hours makes no SimpleFIN call.

### T2 — Categories, rules, budget actuals

- [ ] `normalizeMerchant` on both platforms with shared test vectors
- [ ] Edits and rules records (both platforms), per-entry merge
- [ ] Detail sheet, "Always use this for <merchant>", Needs review queue
- [ ] Budget actuals from transactions; "Cash and other spending" relabel
- **Acceptance:** recategorizing one coffee shop transaction on Android recategorizes all of them, on the web too, and the Dining row total updates.

### T3 — Matching

- [ ] Transfers and credit card payments → `TRANSFER`, excluded
- [ ] Bill payment matches → confirm records `BillPayment` with `transactionId`
- [ ] Paycheck deposit matches; "Log this paycheck?" prompt
- **Acceptance:** paying the electric bill from checking shows "Mark Electric paid?"; confirming marks it paid on both platforms, and paying the Visa doesn't count as spending.

### T4 — AI categorization (web)

- [ ] Port ERV AI settings, detection, provider client, queue, routes (provider-agnostic)
- [ ] `categorize_merchants` prompt, validation, retry; preview of the merchant list
- [ ] Suggestions into edits (`source: ai`); confirm / confirm all
- **Acceptance:** with AI on, a fresh 90-day import leaves only a short Needs review list; the preview shows no amounts or account names; with AI off everything still works.

### T5 — Hardening

- [ ] Start9 release notes and instructions (SimpleFIN quota, AI providers)
- [ ] Manual test list: token disabled, bank with short history, huge month, proxy down, offline Android edits

---

## 10. Later (out of scope)

- Scheduled background fetch while locked (needs an operational key; same open question as ERV).
- Split transactions across categories.
- Subscription detection from recurring merchants → suggest new bills.
- Local Q&A over transactions ("what did I spend on dining in Q3?") with the on-box model.
- Transactions for unlinked/manual accounts (CSV import).
- **Financial planner / audit** (reuses the T4 AI pipeline; same shape as ERV's Coach review):
  - **Planner profile**, a synced record `fiatlife/settings/planner` the user writes: goals,
    motivations, outlook (e.g. bitcoin conviction, time horizon, what counts as risk, fiat cushion
    wanted) and "never suggest" items. Judge against the user's stated philosophy, not
    conventional allocation advice (no default 60/40); disagree openly, don't override.
  - **Context, built on the web:** aggregates only — monthly take-home, bills by category, debts
    (balance, APR, payment, payoff date), net worth breakdown incl. BTC, savings rate, goals and
    progress, spending by category once T2 lands. No account, merchant or biller names, no
    individual transactions. Shown in the preview before sending.
  - **Output:** streamed markdown with fixed sections (Summary · Against your goals · Cash flow ·
    Debt · Net worth and holdings · Risks · Suggested next steps · Data gaps), rendered as plain
    text, cached by context hash, labelled "not financial advice".
  - **Provider:** Maple (larger models) is the better fit for long-form reasoning; real numbers
    leave the box, so off by default and preview always on for this task.

---

## 11. Open decisions

| # | Decision | Options | Default if unanswered |
|---|----------|---------|-----------------------|
| 1 | Include pending transactions? | Yes (muted) · posted only | Yes |
| 2 | Minimum time between automatic fetches | 2 h · 4 h · 6 h | 4 h |
| 3 | Matches auto-apply or confirm? | Confirm in v1 · auto above threshold | Confirm in v1 |
| 4 | Rule toggle default | On · off | On |
| 5 | Preferred AI provider when both exist | Local · Maple | Local |
| 6 | Keep transactions how long on relays? | Forever · rolling N months | Forever (user can purge per month) |

---

## 12. Risks

| Risk | Mitigation |
|------|------------|
| Exceeding SimpleFIN quota disables the token | One combined request; 4-hour throttle; daily cap; surface `errors` warnings in Settings |
| Pending/posted duplicates inflate spending | Pending reconciliation rule (§2.2); pending excluded from budget totals until posted |
| Card payments counted twice | Transfer pairing; revolving-card payments are `TRANSFER` by rule |
| Sync overwrites user categories | Server and clients write different records |
| Month record exceeds 64 KB | Parts; compact fields; tested |
| Noisy merchant strings defeat rules | Shared normalization with test vectors; `payee` preferred |
| AI leaks more than intended | Fixed request shape, server-side check, preview, off by default |
| Nav change disorients users | Same routes; Home cards for Paycheck and Goals; T0 ships alone |
