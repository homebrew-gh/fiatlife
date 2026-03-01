# Credit Card Bill Logic – Deep Dive

This document describes how credit card (and revolving/amortizing loan) **bills** work in FiatLife: where they come from, how dates and “due” state are computed, when things get updated, and where the main pitfalls are. Use it to implement or change behavior without ad‑hoc fixes.

---

## 1. Two Sides: Debt (Credit Account) vs. Bill

- **Credit account** (Debt tab): `CreditAccount` – name, type, `currentBalance`, `dueDay`, `minimumPaymentType`, `minimumPaymentValue`, `linkedBillId`, etc. Stored in Room + Nostr `fiatlife/credit/{id}`. **Source of truth for balance and due day.**
- **Bill** (Bills tab): `Bill` – can represent the **monthly payment** for that account when `linkedCreditAccountId == account.id`. Has `dueDay`, `paymentHistory`, `isPaid`, `lastPaidDate`, optional `creditCardDetails`. Stored in Room + Nostr `fiatlife/bill/{id}`.

The **link** is:

- `CreditAccount.linkedBillId` → bill id  
- `Bill.linkedCreditAccountId` → account id  

When both are set, the bill is “the credit card’s bill” for the Bills tab and dashboard.

---

## 2. When Does a Credit Card Bill Exist?

**CreditAccountRepository** is the only place that **creates** or **removes** the linked bill for an account.

### 2.1 When balance > 0

- **ensureBillForAccount(account)** is called from **saveCreditAccount()** after persisting the account.
- **ensurePrimaryBillForAccount(account)**:
  - If `account.linkedBillId` is set and that bill exists in DB → **do nothing** (no mutation on balance-only updates).
  - Else look for an existing bill by:
    - `linkedCreditAccountId == account.id`, or
    - Legacy: same name + subcategory, no `linkedCreditAccountId`.
  - If found → link it (set `linkedCreditAccountId`, set `account.linkedBillId`), persist, return.
  - If not found → **createAndLinkBill(account, subcategory)**.

**createAndLinkBill** builds a **new** bill with:

- `name = account.name`
- `amount = account.effectiveMonthlyPayment()` (minimum due at creation time)
- `frequency = MONTHLY`, `isRecurring = true`
- `dueDay = account.dueDay`
- `renewalDateMillis = null`, `initialPurchaseDateMillis = null` → **date comes only from `dueDay` (day of month)**
- `linkedCreditAccountId = account.id`
- **No `creditCardDetails`** – so the bill does **not** carry its own balance or min‑payment rule.

So: **one bill per positive-balance account**, created once and then left as-is (no re-creation on balance change). The bill is not updated with the account’s current balance or min‑payment settings after creation.

### 2.2 When balance == 0

- **ensurePrimaryBillForAccount** deletes any bill whose `linkedCreditAccountId == account.id` (and the primary linked bill by id if different).
- Clears `account.linkedBillId` and persists the account.
- Result: **no bill** for that account on the Bills tab until balance goes positive again (then a new bill can be created or an existing one re-linked).

### 2.3 Sync and backfill

- **syncFromNostr()** (credit accounts): after loading accounts from relay, calls **backfillMissingLinkedBills()** and **dedupeLinkedBillsForAccounts()**.
- **backfillMissingLinkedBills()**: for each account with `currentBalance > 0` and no valid linked bill, calls **createAndLinkBill**.
- **dedupeLinkedBillsForAccounts()**: if more than one bill has the same `linkedCreditAccountId`, keeps the one with latest `updatedAt`, deletes the rest, and updates the account’s `linkedBillId`.

---

## 3. Where Does “Balance” and “Amount Due” Come From?

- **CreditAccount**: `currentBalance`, `minimumDue()` (from `minimumPaymentType` / `minimumPaymentValue`). Source of truth.
- **Bill**:
  - **effectiveAmountDue()** = `creditCardDetails?.minimumDue(creditCardDetails.currentBalance) ?: amount`.
  - Repo-created bills have **no** `creditCardDetails`, so **effectiveAmountDue() = bill.amount** (set once at creation). That value is **not** updated when the user changes balance on the Debt tab.
- **UI (Bills tab / Bill detail)**:
  - ViewModels load **credit accounts** and pass **linked account’s `currentBalance`** into the UI as `linkedAccountBalance`.
  - Display uses: `linkedAccountBalance ?: bill.creditCardDetails?.currentBalance ?: 0.0` for “total balance” and for payment dialog.
  - So on the **Bills tab**, “amount due” and “balance” can come from the **account**, but **filtering and logic elsewhere** (e.g. Dashboard) often use only the **bill** (e.g. `bill.effectiveAmountDue()`). That’s why you can see “minimum due” or “upcoming” for a card that actually has **zero balance** on the Debt tab: the bill still has a non‑zero `amount` and no `creditCardDetails`, so `effectiveAmountDue()` is still that old amount.

**Takeaway:** For “hide when balance is 0” and “don’t show minimum when balance is 0”, any logic that doesn’t have access to the **linked CreditAccount** will be wrong if it only uses the bill. The Bills tab is correct because it filters by `account.currentBalance > 0` (for linked bills). The Dashboard uses only bills, so it can show zero‑balance cards as having a due amount unless you either (a) pass account balance into dashboard logic, or (b) keep the bill’s `creditCardDetails` / amount in sync with the account (currently not done).

---

## 4. Date and Cycle Logic (Bill.kt)

All credit/loan **date** logic uses only the bill’s **dueDay** (day of month). No `renewalDateMillis` / `initialPurchaseDateMillis` for repo-created bills.

### 4.1 Core helpers

- **dueDateForMonth(anchorMillis)**  
  Returns the **start-of-day** (00:00:00) of the month of `anchorMillis` with `DAY_OF_MONTH = dueDay` (clamped to max day of that month).  
  Example: due day 12, anchor in Feb → Feb 12 00:00:00.

- **endOfDayMillis(dayStart)**  
  `dayStart + 86_400_000 - 1` (last millisecond of that calendar day).

- **shiftCreditLoanCycle(baseDue, months)**  
  Same calendar day, month += `months`. Used to get “previous cycle” or “next cycle” due date.

### 4.2 nextDueDateMillis() (credit/loan)

- **creditLoanUpcomingDue(now)**:
  - `currentMonthDue = dueDateForMonth(now)` (this month’s due date).
  - If `currentMonthDue > now` → return `currentMonthDue` (due date is still in the future this month).
  - Else → return `shiftCreditLoanCycle(currentMonthDue, 1)` (next month’s due date).

So: **next due** is always “this month’s due day if it’s still in the future, else next month’s due day”. No dependency on payment history for the *value* of the next due date.

### 4.3 lastDueDateMillis() (credit/loan)

- `currentDue = dueDateForMonth(now)`.
- If **now > endOfDay(currentDue)** and there is **no** qualifying payment for the **current** cycle → return `currentDue` (we’re past this month’s due and didn’t pay; “last due” is this month).
- Else → `previous = shiftCreditLoanCycle(currentDue, -1)`. If `previous <= now` return `previous`, else `null`.

Used for “what is the due date we’re considering as missed?” when showing overdue.

### 4.4 “Cycle” for payment status (credit/loan)

- **creditLoanDueForStatus(now)**  
  Used to decide *which* cycle we’re in for paid/unpaid/overdue:
  - `currentMonthDue = dueDateForMonth(now)`.
  - If `now <= endOfDayMillis(currentMonthDue)` → we’re still “in” the current month’s cycle → return `currentMonthDue`.
  - Else → we’re past that day → treat as “next” cycle → return `shiftCreditLoanCycle(currentMonthDue, 1)`.

So: **before/on due day** = current cycle; **after due day** = next cycle.

### 4.5 hasQualifyingPaymentForCycle(cycleDue)

- `previousCycleDue = shiftCreditLoanCycle(cycleDue, -1)`.
- Look for a **payment** in **paymentHistory** with:
  - `date > previousCycleDue` and `date <= endOfDayMillis(cycleDue)`,
  - `amount >= qualifyingPaymentMinimum()` (for credit/loan that’s **0.01** – any payment counts).
- If none found, **legacy**: if `lastPaidDate` is set, `isPaid`, and `lastPaidDate` is in `(previousCycleDue, endOfDay(cycleDue)]`, count as paid for that cycle.

So: “Did we record at least one (tiny) payment between the previous cycle’s due and this cycle’s due (inclusive)?”

### 4.6 isPastDue() (credit/loan)

- If **paymentHistory.isEmpty() && lastPaidDate == null** → **return false**. Never treat as overdue if we’ve never recorded a payment (avoids “overdue” for cards added after the due date or before the app existed).
- **now <= endOfDay(currentDue)** → not overdue.
- **hasQualifyingPaymentForCycle(currentDue)** → not overdue.
- Else check for a payment **after** `endOfDay(currentDue)` and **<= now** (paid late in this cycle). If yes → not overdue. If no → **overdue**.

So: overdue = “current due date has passed, no qualifying payment for that cycle, and no late payment in the window after the due date.”

### 4.7 isPaidForCurrentCycle() (credit/loan)

- **cycleDue = creditLoanDueForStatus(now)** (which cycle we’re in).
- Return **hasQualifyingPaymentForCycle(cycleDue)**.

So: “Have we recorded a payment for the cycle we’re currently in?” (current vs next is decided by whether now is before/after this month’s due day.)

### 4.8 dueAmountInMonth(monthAnchor) / dueOccurrencesInMonth

- For credit/loan, amount and occurrences are driven by **effectiveAmountDue()** and recurrence (monthly = 1 occurrence per month in the month’s window). Used for “monthly total” and category totals.

---

## 5. When Things Get Updated

### 5.1 User adds or edits a credit account (Debt tab)

- **saveCreditAccount(account)**:
  1. Persist account (and publish to relay).
  2. **ensureBillForAccount(account)** → ensure one linked bill when balance > 0, none when balance = 0 (see §2).
  3. **inferCreditPaymentFromBalanceDrop(previous, current)** (see §5.3).

No update to the bill’s `creditCardDetails` or `amount` when only the account’s balance or due day changes; the bill is only created, linked, or deleted.

### 5.2 User records a payment from the Bills tab (credit/loan)

- **recordCreditLoanPayment(item, amount, newBalance)** → **recordPaymentInternal**:
  1. Build **BillPayment(date = now, amount)**.
  2. Update bill: `paymentHistory += payment`, `isPaid = true`, `lastPaidDate = payment.date`. If the bill has `creditCardDetails`, set `creditCardDetails.currentBalance = newBalance ?: (current - amount)`.
  3. **repository.saveBill(updatedBill)**.
  4. If **bill.linkedCreditAccountId** is set: load that **CreditAccount**, set **currentBalance = newBalance ?: (acc.currentBalance - amount)**, **saveCreditAccount(acc)**.

So: **payment is written on both bill and account**. Saving the account again runs **ensureBillForAccount** and **inferCreditPaymentFromBalanceDrop**; normally nothing changes for the bill except that it already has the new payment in history.

### 5.3 Balance drop inferred from account (no payment dialog)

- **inferCreditPaymentFromBalanceDrop(previous, current)** (in CreditAccountRepository):
  - Called from **saveCreditAccount** after **ensureBillForAccount**.
  - If `previous == null` or not revolving/amortizing, or `current.currentBalance >= previous.currentBalance`, return.
  - Delta = `previous.currentBalance - current.currentBalance`. If delta <= 0, return.
  - Load linked bill; if no linked bill, return.
  - If there’s already a payment in **paymentHistory** within ~90 seconds with same amount, return (avoid duplicate).
  - Else add **BillPayment(date = now, amount = delta)** to the bill, set `isPaid = true`, `lastPaidDate = now`, **saveBill**.

So: if the user **only** changes the balance on the Debt tab (e.g. statement update), we infer a payment on the linked bill so that “paid this cycle” and overdue logic stay in sync.

---

## 6. Backfill and Sync

### 6.1 BillRepository.backfillLegacyCreditLoanPayments()

- Runs after **syncFromNostr** (and once in BillsViewModel init).
- For each **credit/loan** bill that has **isPaid && lastPaidDate != null** but **no** payment in **paymentHistory** within 60 seconds of `lastPaidDate`:
  - Append one **BillPayment(date = lastPaidDate, amount = bill.amount or effectiveAmountDue())**.
  - Upsert the bill (DB only; no relay publish in this backfill).

So: old bills that were “marked paid” only via `isPaid`/`lastPaidDate` get a synthetic payment so **hasQualifyingPaymentForCycle** and **isPastDue** work correctly.

### 6.2 Credit account sync

- **syncFromNostr** loads accounts from relay, then **backfillMissingLinkedBills()** and **dedupeLinkedBillsForAccounts()** so every positive-balance account has exactly one linked bill.

---

## 7. Who Uses What (Bills tab vs Dashboard)

### 7.1 Bills tab (BillsViewModel)

- **Visibility**: Merges native bills + CypherLog. For **CREDIT_LOANS**, keeps a bill only if:
  - It has **linkedCreditAccountId** and that **account.currentBalance > 0**, or
  - Legacy: no link but an account with same name and balance > 0 exists.
- So: **zero-balance cards are hidden** because of account balance, not bill.effectiveAmountDue().
- **Payment dialog**: Uses **linkedAccountBalance** (from state.creditAccounts) for total balance and options; updates both bill (paymentHistory, isPaid, lastPaidDate) and account (currentBalance) on save.

### 7.2 Dashboard (DashboardViewModel)

- **visibleBills**: All bills (native + CypherLog) minus cancelled and (for utilities) paid-for-current-cycle. **No** per-bill access to credit account balance.
- **Overdue count**: Excludes **CREDIT_LOANS** (so credit cards don’t add to “# overdue”).
- **Upcoming bills**: For **credit/loan**, filter is `effectiveAmountDue() > 0` and (past due OR next due ≤ 3 months). So:
  - **effectiveAmountDue()** is **bill.amount** when the bill has no `creditCardDetails` → a zero-balance account can still have a bill with non‑zero `amount` and appear in “Upcoming” and show a minimum due.
- **Display**: Uses **bill.nextDueDateMillis()** / **bill.lastDueDateMillis()** and **bill.isPastDue()**; no account balance.

So: **Dashboard can show credit cards that have zero balance** and **past-looking dates** if (1) the bill’s `amount` is non‑zero and/or (2) **isPastDue** was true before the “no payment history ⇒ not overdue” fix. After the fix, cards with no payment history show **next** due date and not overdue; but “minimum when balance 0” remains unless Dashboard (or the bill) gets balance from the account.

---

## 8. Summary Table

| What | Where it lives | When it’s set/updated |
|------|----------------|------------------------|
| Balance | CreditAccount.currentBalance | Debt tab save; or when recording payment from Bills (and then inferCreditPaymentFromBalanceDrop when balance drops from Debt tab). |
| Due day | CreditAccount.dueDay, Bill.dueDay | Set on account; bill gets it at creation only (createAndLinkBill). |
| Next due date | Computed: Bill.nextDueDateMillis() | From dueDay only: this month’s due day if still in future, else next month’s. |
| Last due date | Computed: Bill.lastDueDateMillis() | From dueDay + payment status for current cycle. |
| Overdue? | Computed: Bill.isPastDue() | No payment history ⇒ false. Else: past current due + no qualifying payment for cycle + no late payment. |
| Paid this cycle? | Computed: Bill.isPaidForCurrentCycle() | hasQualifyingPaymentForCycle(creditLoanDueForStatus(now)). |
| Amount due (bill) | Bill.effectiveAmountDue() | creditCardDetails?.minimumDue(balance) ?: amount. Repo-created bills: no creditCardDetails ⇒ always **amount** (never updated from account). |
| Payment history | Bill.paymentHistory | Recorded when user pays from Bills; or inferred when balance drops on account; or backfilled from isPaid/lastPaidDate. |
| Linked bill exists? | CreditAccount.linkedBillId, Bill.linkedCreditAccountId | Created/linked when balance > 0 (ensurePrimaryBillForAccount); deleted/unlinked when balance = 0. |

---

## 9. Recommended Consistency Fixes (for future work)

1. **Single source of truth for “amount due” and “has balance” for linked bills**  
   Either:
   - **Option A**: When saving a CreditAccount, update the linked bill’s **creditCardDetails** (or a dedicated field) with current balance and min-payment params from the account, so **effectiveAmountDue()** and “balance” are correct everywhere that only sees the bill; or  
   - **Option B**: Any place that filters or displays “upcoming” / “amount due” for credit/loan bills (e.g. Dashboard) should resolve **linkedCreditAccountId** to the account and use **account.currentBalance** and **account.minimumDue()** instead of **bill.effectiveAmountDue()**.

2. **Hide zero-balance credit cards everywhere**  
   Use the same rule as the Bills tab: if `linkedCreditAccountId` is set and the resolved account has `currentBalance <= 0`, exclude from “upcoming” and from any “minimum due” display.

3. **Date-only logic**  
   Keep using **dueDay** as the only anchor for credit/loan bills (no renewal/initial dates), and keep **nextDueDateMillis** / **lastDueDateMillis** / **creditLoanDueForStatus** / **hasQualifyingPaymentForCycle** as defined above so “current cycle” and “overdue” stay consistent with payment history and the “no history ⇒ not overdue” rule.

This doc should be enough to implement or refactor the full credit card bill flow without “janky” one-off fixes. If you later write a detailed spec for a full rewrite, this is the behavior to preserve or explicitly change.
