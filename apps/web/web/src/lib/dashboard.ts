import {
  effectiveAmountDue,
  generalCategoryForBill,
  isPastDue,
  monthlyEquivalent,
  nextDueDateMillis,
  type Bill,
  type BillGeneralCategory,
  type BillWithSource,
} from "./bill";
import type { FinancialGoal } from "./goal";
import {
  PERIODS_PER_YEAR,
  calculateAnnual,
  calculatePaycheck,
  countPaychecksInRange,
  summarizeYtd,
  type PaycheckCalculation,
  type SalaryConfig,
} from "./salary";

export type DashboardState = {
  takeHomePay: number;
  grossPay: number;
  totalTaxes: number;
  totalDeductions: number;
  effectiveTaxRate: number;
  monthlyBills: number;
  monthlyDisposable: number;
  billCount: number;
  billsComingDueCount: number;
  overdueBillCount: number;
  billCategoryTotals: Partial<Record<BillGeneralCategory, number>>;
  goalCount: number;
  goalsProgress: number;
  totalSaved: number;
  totalGoalTarget: number;
  topGoals: FinancialGoal[];
  upcomingBills: Bill[];
  ytdNetPay: number;
  ytdSource: "logged" | "estimated" | "none";
  hasSalary: boolean;
  hasBills: boolean;
  hasData: boolean;
};

function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function monthBounds(monthAnchorMillis: number): { start: number; end: number } {
  const start = new Date(monthAnchorMillis);
  start.setDate(1);
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setMonth(end.getMonth() + 1);
  end.setMilliseconds(-1);
  return { start: start.getTime(), end: end.getTime() };
}

export function paycheckCountInMonth(
  salary: SalaryConfig | null,
  monthAnchorMillis = Date.now(),
): number {
  if (!salary) return 0;
  if (salary.payFrequency === "SEMIMONTHLY") return 2;
  if (salary.payFrequency === "MONTHLY") return 1;

  const anchor = salary.firstPaydayOfYearMillis;
  if (!anchor) {
    return PERIODS_PER_YEAR[salary.payFrequency] / 12;
  }

  const { start, end } = monthBounds(monthAnchorMillis);
  return Math.max(
    1,
    countPaychecksInRange(anchor, salary.payFrequency, start, end),
  );
}

function isCreditOrLoan(bill: Bill): boolean {
  return generalCategoryForBill(bill) === "CREDIT_LOANS";
}

function linkedCreditBalance(bill: Bill): number {
  return bill.creditCardDetails?.currentBalance ?? 0;
}

function isVisibleOnDashboard(bill: Bill): boolean {
  if (bill.isCancelled) return false;
  const cat = generalCategoryForBill(bill);
  if (cat === "UTILITIES" && bill.isPaid) return false;
  return true;
}

function isUnpaid(bill: Bill): boolean {
  return !bill.isPaid;
}

export function computeDashboardState(input: {
  salary: SalaryConfig | null;
  calculation: PaycheckCalculation | null;
  bills: BillWithSource[];
  goals: FinancialGoal[];
  monthAnchorMillis?: number;
  now?: number;
}): DashboardState {
  const now = input.now ?? Date.now();
  const monthAnchor = input.monthAnchorMillis ?? now;
  const calc =
    input.calculation ??
    (input.salary ? calculatePaycheck(input.salary) : null);

  const allBills = input.bills
    .map((b) => b.bill)
    .filter((b) => !b.isCancelled);

  const visibleBills = allBills.filter(isVisibleOnDashboard);

  const monthlyBills = allBills.reduce(
    (sum, b) => sum + monthlyEquivalent(b),
    0,
  );

  const billCategoryTotals: Partial<Record<BillGeneralCategory, number>> = {};
  for (const bill of allBills) {
    const cat = generalCategoryForBill(bill);
    billCategoryTotals[cat] =
      (billCategoryTotals[cat] ?? 0) + monthlyEquivalent(bill);
  }

  const sevenDaysMs = 7 * 24 * 60 * 60 * 1000;
  const threeMonthsFromNow = new Date(now);
  threeMonthsFromNow.setMonth(threeMonthsFromNow.getMonth() + 3);

  const overdueBillCount = visibleBills.filter(
    (bill) =>
      !isCreditOrLoan(bill) &&
      isUnpaid(bill) &&
      isPastDue(bill, now),
  ).length;

  const billsComingDueCount = visibleBills.filter((bill) => {
    const nextDue = nextDueDateMillis(bill, now);
    if (nextDue == null) return false;
    if (isPastDue(bill, now)) return false;
    if (!isUnpaid(bill)) return false;
    if (nextDue > now + sevenDaysMs) return false;
    if (isCreditOrLoan(bill)) {
      return linkedCreditBalance(bill) > 0;
    }
    return true;
  }).length;

  const multiplier = paycheckCountInMonth(input.salary, monthAnchor);
  const monthlyTakeHome = (calc?.netPay ?? 0) * multiplier;
  const monthlyGross = (calc?.grossPay ?? 0) * multiplier;
  const monthlyTaxes = (calc?.totalTaxes ?? 0) * multiplier;
  const monthlyDeductions =
    ((calc?.totalPreTaxDeductions ?? 0) +
      (calc?.totalPostTaxDeductions ?? 0)) *
    multiplier;
  const monthlyDisposable = monthlyTakeHome - monthlyBills;

  const totalSaved = input.goals.reduce((s, g) => s + g.currentAmount, 0);
  const totalGoalTarget = input.goals.reduce((s, g) => s + g.targetAmount, 0);
  const goalsProgress =
    totalGoalTarget > 0 ? (totalSaved / totalGoalTarget) * 100 : 0;

  const topGoals = [...input.goals]
    .sort(
      (a, b) =>
        b.currentAmount / Math.max(b.targetAmount, 1) -
        a.currentAmount / Math.max(a.targetAmount, 1),
    )
    .slice(0, 3);

  const upcomingBills = visibleBills
    .filter((bill) => {
      const nextDue = nextDueDateMillis(bill, now);
      const withinWindow =
        isPastDue(bill, now) ||
        (nextDue != null && nextDue <= threeMonthsFromNow.getTime());
      if (!withinWindow) return false;
      if (!isUnpaid(bill)) return false;
      if (isCreditOrLoan(bill)) {
        return linkedCreditBalance(bill) > 0;
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
  let ytdNetPay = 0;
  let ytdSource: DashboardState["ytdSource"] = "none";
  if (input.salary && calc) {
    const annual = calculateAnnual(input.salary, 0);
    const ytd = summarizeYtd(input.salary, calc, annual, year, now);
    ytdNetPay = ytd.netPay;
    ytdSource = ytd.source;
  }

  const hasSalary = input.salary != null && (input.salary.hourlyRate > 0 || calc != null);
  const hasBills = allBills.length > 0;

  return {
    takeHomePay: monthlyTakeHome,
    grossPay: monthlyGross,
    totalTaxes: monthlyTaxes,
    totalDeductions: monthlyDeductions,
    effectiveTaxRate: calc?.effectiveTaxRate ?? 0,
    monthlyBills,
    monthlyDisposable,
    billCount: visibleBills.length,
    billsComingDueCount,
    overdueBillCount,
    billCategoryTotals,
    goalCount: input.goals.length,
    goalsProgress,
    totalSaved,
    totalGoalTarget,
    topGoals,
    upcomingBills,
    ytdNetPay,
    ytdSource,
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

export function billDisplayAmount(bill: Bill): number {
  return effectiveAmountDue(bill);
}
