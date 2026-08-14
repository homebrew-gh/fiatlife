import {
  effectiveAmountDue,
  generalCategoryForBill,
  isPaidForCurrentCycle,
  isPastDue,
  monthlyEquivalent,
  nextDueDateMillis,
  type Bill,
  type BillWithSource,
} from "./bill";
import {
  effectiveAmountDue as accountAmountDue,
  effectiveMonthlyPayment,
  housingMonthlyTotal,
  type CreditAccount,
} from "./creditAccount";
import {
  goalIsComplete,
  goalProgressPercent,
  type FinancialGoal,
} from "./goal";
import {
  computeMonthlyTakeHome,
  missingPaydaysForYear,
  type MonthlyTakeHomeSource,
  type PaycheckCalculation,
  type SalaryConfig,
} from "./salary";

const ATTENTION_DUE_MS = 7 * 24 * 60 * 60 * 1000;
const DUE_SOON_LIST_MS = 14 * 24 * 60 * 60 * 1000;

export type DashboardState = {
  takeHomePay: number;
  monthlyBills: number;
  monthlyDisposable: number;
  billsComingDueCount: number;
  overdueBillCount: number;
  housingMonthly: number;
  mortgageAccountId: string | null;
  missingPaycheckCount: number;
  goalCount: number;
  primaryGoal: FinancialGoal | null;
  upcomingBills: Bill[];
  monthlyTakeHomeSource: MonthlyTakeHomeSource;
  monthlyLoggedTakeHome: number;
  monthlyProjectedRemainder: number;
  monthlyLoggedPaycheckCount: number;
  monthlyRemainingPaycheckCount: number;
  monthlyLoggedOvertimeHours: number;
  monthlyLoggedBonus: number;
  monthlyPerPaycheckEstimate: number;
  hasSalary: boolean;
  hasBills: boolean;
  hasData: boolean;
};

function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function isCreditOrLoan(bill: Bill): boolean {
  return generalCategoryForBill(bill) === "CREDIT_LOANS";
}

function linkedAccount(
  bill: Bill,
  creditAccounts: CreditAccount[],
): CreditAccount | undefined {
  if (bill.linkedCreditAccountId) {
    return creditAccounts.find((a) => a.id === bill.linkedCreditAccountId);
  }
  return creditAccounts.find(
    (a) =>
      a.linkedBillId === bill.id ||
      a.name.toLowerCase() === bill.name.toLowerCase(),
  );
}

function linkedCreditBalance(
  bill: Bill,
  creditAccounts: CreditAccount[],
): number {
  const account = linkedAccount(bill, creditAccounts);
  if (account) return account.currentBalance;
  return bill.creditCardDetails?.currentBalance ?? 0;
}

function billUnpaid(bill: Bill, now: number): boolean {
  if (isCreditOrLoan(bill)) return !isPaidForCurrentCycle(bill, now);
  return !bill.isPaid;
}

function billMonthlyAmount(
  bill: Bill,
  creditAccounts: CreditAccount[],
): number {
  const account = linkedAccount(bill, creditAccounts);
  if (account) return effectiveMonthlyPayment(account);
  return monthlyEquivalent(bill);
}

function isVisibleOnDashboard(bill: Bill): boolean {
  if (bill.isCancelled) return false;
  const cat = generalCategoryForBill(bill);
  if (cat === "UTILITIES" && bill.isPaid) return false;
  return true;
}

/** Incomplete goal with the nearest deadline, else the least complete. */
export function pickPrimaryGoal(
  goals: FinancialGoal[],
  now = Date.now(),
): FinancialGoal | null {
  const incomplete = goals.filter((g) => !goalIsComplete(g));
  if (incomplete.length === 0) return null;
  const withDates = incomplete.filter(
    (g) => g.targetDate != null && g.targetDate > 0,
  );
  if (withDates.length > 0) {
    return [...withDates].sort(
      (a, b) => (a.targetDate ?? now) - (b.targetDate ?? now),
    )[0];
  }
  return [...incomplete].sort(
    (a, b) => goalProgressPercent(a) - goalProgressPercent(b),
  )[0];
}

export function computeDashboardState(input: {
  salary: SalaryConfig | null;
  calculation: PaycheckCalculation | null;
  bills: BillWithSource[];
  goals: FinancialGoal[];
  creditAccounts?: CreditAccount[];
  monthAnchorMillis?: number;
  now?: number;
}): DashboardState {
  const now = input.now ?? Date.now();
  const monthAnchor = input.monthAnchorMillis ?? now;
  const creditAccounts = input.creditAccounts ?? [];

  const allBills = input.bills
    .map((b) => b.bill)
    .filter((b) => !b.isCancelled);

  const visibleBills = allBills.filter(isVisibleOnDashboard);

  const monthlyBills = allBills.reduce(
    (sum, b) => sum + billMonthlyAmount(b, creditAccounts),
    0,
  );

  const overdueBillCount = visibleBills.filter(
    (bill) =>
      !isCreditOrLoan(bill) &&
      billUnpaid(bill, now) &&
      isPastDue(bill, now),
  ).length;

  const billsComingDueCount = visibleBills.filter((bill) => {
    const nextDue = nextDueDateMillis(bill, now);
    if (nextDue == null) return false;
    if (isPastDue(bill, now)) return false;
    if (!billUnpaid(bill, now)) return false;
    if (nextDue > now + ATTENTION_DUE_MS) return false;
    if (isCreditOrLoan(bill)) {
      return linkedCreditBalance(bill, creditAccounts) > 0;
    }
    return true;
  }).length;

  const monthlyProjection = input.salary
    ? computeMonthlyTakeHome(input.salary, monthAnchor, now)
    : null;
  const monthlyTakeHome = monthlyProjection?.totalTakeHome ?? 0;
  const monthlyDisposable = monthlyTakeHome - monthlyBills;

  const upcomingBills = visibleBills
    .filter((bill) => {
      const nextDue = nextDueDateMillis(bill, now);
      const withinWindow =
        isPastDue(bill, now) ||
        (nextDue != null && nextDue <= now + DUE_SOON_LIST_MS);
      if (!withinWindow) return false;
      if (!billUnpaid(bill, now)) return false;
      if (isCreditOrLoan(bill)) {
        return linkedCreditBalance(bill, creditAccounts) > 0;
      }
      return true;
    })
    .sort((a, b) => {
      const aPast = isPastDue(a, now);
      const bPast = isPastDue(b, now);
      if (aPast !== bPast) return aPast ? -1 : 1;
      const aDue = isPastDue(a, now)
        ? startOfDay(nextDueDateMillis(a, now) ?? 0)
        : (nextDueDateMillis(a, now) ?? Number.MAX_SAFE_INTEGER);
      const bDue = isPastDue(b, now)
        ? startOfDay(nextDueDateMillis(b, now) ?? 0)
        : (nextDueDateMillis(b, now) ?? Number.MAX_SAFE_INTEGER);
      return aDue - bDue;
    })
    .slice(0, 5);

  const year = new Date(now).getFullYear();
  const missingPaycheckCount = input.salary
    ? missingPaydaysForYear(input.salary, year, now).length
    : 0;

  const mortgage = creditAccounts.find((a) => a.type === "MORTGAGE") ?? null;

  const hasSalary =
    input.salary != null &&
    (input.salary.hourlyRate > 0 || input.calculation != null);
  const hasBills = allBills.length > 0;

  return {
    takeHomePay: monthlyTakeHome,
    monthlyBills,
    monthlyDisposable,
    billsComingDueCount,
    overdueBillCount,
    housingMonthly: housingMonthlyTotal(creditAccounts),
    mortgageAccountId: mortgage?.id ?? null,
    missingPaycheckCount,
    goalCount: input.goals.length,
    primaryGoal: pickPrimaryGoal(input.goals, now),
    upcomingBills,
    monthlyTakeHomeSource: monthlyProjection?.source ?? "estimated",
    monthlyLoggedTakeHome: monthlyProjection?.loggedTakeHome ?? 0,
    monthlyProjectedRemainder: monthlyProjection?.projectedRemainder ?? 0,
    monthlyLoggedPaycheckCount: monthlyProjection?.loggedPaycheckCount ?? 0,
    monthlyRemainingPaycheckCount:
      monthlyProjection?.remainingPaycheckCount ?? 0,
    monthlyLoggedOvertimeHours: monthlyProjection?.loggedOvertimeHours ?? 0,
    monthlyLoggedBonus: monthlyProjection?.loggedBonusTotal ?? 0,
    monthlyPerPaycheckEstimate: monthlyProjection?.perPaycheckNet ?? 0,
    hasSalary,
    hasBills,
    hasData: hasSalary || hasBills || input.goals.length > 0,
  };
}

export function formatShortDate(ms: number): string {
  return new Date(ms).toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
}

export function billDueLabel(bill: Bill, now = Date.now()): string {
  const due = nextDueDateMillis(bill, now);
  if (due == null) return "";
  const text = formatShortDate(due);
  return isPastDue(bill, now) ? `${text} (Overdue)` : text;
}

export function billDisplayAmount(
  bill: Bill,
  creditAccounts: CreditAccount[] = [],
): number {
  const account = linkedAccount(bill, creditAccounts);
  if (account) return accountAmountDue(account);
  return effectiveAmountDue(bill);
}
