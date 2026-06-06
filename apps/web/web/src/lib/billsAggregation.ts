/**
 * Bills list aggregation — mirrors Android `BillsViewModel` visibility and totals.
 */
import type { BankAccount } from "./bankAccount";
import type {
  Bill,
  BillGeneralCategory,
  BillWithSource,
} from "./bill";
import {
  dueAmountInMonth,
  dueAmountInYear,
  generalCategoryForBill,
  isCreditOrLoan,
  isPaidForCurrentCycle,
  isPastDue,
  lastDueDateMillis,
  nextDueDateMillis,
} from "./bill";
import type { CreditAccount } from "./creditAccount";

export type PaymentBreakdownRow = {
  id: string;
  name: string;
  isCredit: boolean;
  total: number;
};

const DAY_MS = 86_400_000;

function billDueSoonestComparator(now: number) {
  return (a: BillWithSource, b: BillWithSource) => {
    const aDue = nextDueDateMillis(a.bill, now) ?? Number.MAX_SAFE_INTEGER;
    const bDue = nextDueDateMillis(b.bill, now) ?? Number.MAX_SAFE_INTEGER;
    return aDue - bDue;
  };
}

/** Bills included in cost-basis totals (excludes cancelled, zero-balance linked debt). */
export function costBasisBills(
  items: BillWithSource[],
  creditAccounts: CreditAccount[],
): BillWithSource[] {
  const accountsById = new Map(creditAccounts.map((a) => [a.id, a]));
  return items.filter((item) => {
    if (item.bill.isCancelled) return false;
    if (generalCategoryForBill(item.bill) !== "CREDIT_LOANS") return true;
    const linkedId = item.bill.linkedCreditAccountId;
    if (linkedId) {
      const account = accountsById.get(linkedId);
      return account != null && account.currentBalance > 0;
    }
    const billName = item.bill.name.trim();
    const matched = creditAccounts.find(
      (acc) =>
        acc.currentBalance > 0 &&
        (acc.linkedBillId === item.bill.id ||
          acc.name.toLowerCase() === billName.toLowerCase()),
    );
    return matched != null;
  });
}

/** Bills shown in main list (hides paid utilities until next cycle). */
export function visibleBills(
  items: BillWithSource[],
  creditAccounts: CreditAccount[],
  now = Date.now(),
): BillWithSource[] {
  return costBasisBills(items, creditAccounts).filter((item) => {
    if (
      generalCategoryForBill(item.bill) === "UTILITIES" &&
      isPaidForCurrentCycle(item.bill, now)
    ) {
      return false;
    }
    return true;
  });
}

export function billsDueInNext7Days(
  items: BillWithSource[],
  now = Date.now(),
): BillWithSource[] {
  const sevenDaysMs = 7 * DAY_MS;
  const todayStart = new Date(now);
  todayStart.setHours(0, 0, 0, 0);
  const todayEnd = todayStart.getTime() + DAY_MS - 1;

  return items
    .filter((item) => {
      if (item.isCypherLog || isPaidForCurrentCycle(item.bill, now)) {
        return false;
      }
      if (isCreditOrLoan(item.bill) && isPaidForCurrentCycle(item.bill, now)) {
        return false;
      }
      if (isPastDue(item.bill, now)) return true;
      const nextDue = nextDueDateMillis(item.bill, now);
      const inWindow = nextDue != null && nextDue <= now + sevenDaysMs;
      const lastDue = lastDueDateMillis(item.bill, now);
      const dueToday = lastDue != null && lastDue >= todayStart.getTime() && lastDue <= todayEnd;
      return inWindow || dueToday;
    })
    .sort((a, b) => {
      const aCredit = isCreditOrLoan(a.bill) ? 0 : 1;
      const bCredit = isCreditOrLoan(b.bill) ? 0 : 1;
      if (aCredit !== bCredit) return aCredit - bCredit;
      const aDue = nextDueDateMillis(a.bill, now) ?? Number.MAX_SAFE_INTEGER;
      const bDue = nextDueDateMillis(b.bill, now) ?? Number.MAX_SAFE_INTEGER;
      return aDue - bDue;
    });
}

export function otherBillsByCategory(
  items: BillWithSource[],
  dueIn7Ids: Set<string>,
  now = Date.now(),
): Map<BillGeneralCategory, BillWithSource[]> {
  const map = new Map<BillGeneralCategory, BillWithSource[]>();
  const cmp = billDueSoonestComparator(now);
  for (const item of items) {
    if (dueIn7Ids.has(item.bill.id)) continue;
    const cat = generalCategoryForBill(item.bill);
    const list = map.get(cat) ?? [];
    list.push(item);
    map.set(cat, list);
  }
  for (const [cat, list] of map) {
    map.set(cat, [...list].sort(cmp));
  }
  return map;
}

export function pastDueAutopayBills(
  items: BillWithSource[],
  now = Date.now(),
): BillWithSource[] {
  return items.filter(
    (item) =>
      !item.isCypherLog &&
      Boolean(item.bill.autoPay) &&
      isPastDue(item.bill, now),
  );
}

export function computePaymentBreakdown(
  items: BillWithSource[],
  bankAccounts: BankAccount[],
  creditAccounts: CreditAccount[],
  monthAnchor: number,
  annual = false,
): {
  rows: PaymentBreakdownRow[];
  subtotalBanks: number;
  subtotalCredit: number;
} {
  const bankTotals = new Map<string, number>();
  const creditTotals = new Map<string, number>();

  for (const item of items) {
    const amount = annual
      ? dueAmountInYear(item.bill, monthAnchor)
      : dueAmountInMonth(item.bill, monthAnchor);
    if (item.bill.payFromBankAccountId) {
      const id = item.bill.payFromBankAccountId;
      bankTotals.set(id, (bankTotals.get(id) ?? 0) + amount);
    }
    if (item.bill.payFromCreditAccountId) {
      const id = item.bill.payFromCreditAccountId;
      creditTotals.set(id, (creditTotals.get(id) ?? 0) + amount);
    }
  }

  const rows: PaymentBreakdownRow[] = [];
  for (const acc of bankAccounts) {
    const total = bankTotals.get(acc.id) ?? 0;
    if (total > 0) rows.push({ id: acc.id, name: acc.name, isCredit: false, total });
  }
  for (const acc of creditAccounts) {
    const total = creditTotals.get(acc.id) ?? 0;
    if (total > 0) rows.push({ id: acc.id, name: acc.name, isCredit: true, total });
  }

  const subtotalBanks = rows.filter((r) => !r.isCredit).reduce((s, r) => s + r.total, 0);
  const subtotalCredit = rows.filter((r) => r.isCredit).reduce((s, r) => s + r.total, 0);
  return { rows, subtotalBanks, subtotalCredit };
}

export function categoryTotals(
  bills: Bill[],
  monthAnchor: number,
  annual = false,
): Map<BillGeneralCategory, number> {
  const map = new Map<BillGeneralCategory, number>();
  for (const bill of bills) {
    const cat = generalCategoryForBill(bill);
    const amount = annual
      ? dueAmountInYear(bill, monthAnchor)
      : dueAmountInMonth(bill, monthAnchor);
    map.set(cat, (map.get(cat) ?? 0) + amount);
  }
  return map;
}
