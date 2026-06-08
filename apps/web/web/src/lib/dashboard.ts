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
  calculateAnnual,
  calculatePaycheck,
  computeMonthlyTakeHome,
  summarizeYtd,
  type MonthlyTakeHomeSource,
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

  const monthlyProjection = input.salary
    ? computeMonthlyTakeHome(input.salary, monthAnchor, now)
    : null;
  const monthlyTakeHome = monthlyProjection?.totalTakeHome ?? 0;
  const monthlyGross = monthlyProjection?.totalGross ?? 0;
  const monthlyTaxes = monthlyProjection?.totalTaxes ?? 0;
  const monthlyDeductions = monthlyProjection?.totalDeductions ?? 0;
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
    effectiveTaxRate:
      monthlyGross > 0
        ? monthlyTaxes / monthlyGross
        : (calc?.effectiveTaxRate ?? 0),
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

export function billDisplayAmount(bill: Bill): number {
  return effectiveAmountDue(bill);
}
