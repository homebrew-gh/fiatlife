import {
  effectiveApr,
  effectiveMonthlyPayment,
  isRevolvingType,
  type CreditAccount,
} from "./creditAccount";

/** Hard cap on simulated months (100 years) — beyond this we call it "won't pay off". */
const MAX_MONTHS = 1200;

export type PayoffProjection = {
  /** Whether the balance is fully repaid within MAX_MONTHS at the given payment. */
  feasible: boolean;
  /** Months until paid off (0 if already zero). Only meaningful when feasible. */
  months: number;
  /** Total interest paid over the life of the payoff. */
  totalInterest: number;
  /** Estimated payoff date in epoch millis, or null when not feasible / already paid. */
  payoffDateMs: number | null;
  /** Interest accruing in the first month at the current balance. */
  monthlyInterest: number;
};

/** Interest that accrues this month at the current balance. */
export function monthlyInterest(account: CreditAccount): number {
  const apr = effectiveApr(account);
  if (account.currentBalance <= 0 || apr <= 0) return 0;
  return account.currentBalance * (apr / 12);
}

/**
 * Simulate paying off a balance with a fixed monthly payment.
 * Models the intuitive "at $X/mo you'll be done in Y" — the payment is held
 * constant rather than recomputed (which matches the figure shown to the user).
 */
export function projectPayoff(input: {
  balance: number;
  annualRate: number;
  monthlyPayment: number;
  nowMs?: number;
}): PayoffProjection {
  const balance0 = Math.max(0, input.balance);
  const monthlyRate = Math.max(0, input.annualRate) / 12;
  const payment = Math.max(0, input.monthlyPayment);
  const nowMs = input.nowMs ?? Date.now();
  const startInterest = balance0 * monthlyRate;

  if (balance0 <= 0) {
    return {
      feasible: true,
      months: 0,
      totalInterest: 0,
      payoffDateMs: null,
      monthlyInterest: 0,
    };
  }

  // Payment can't keep up with interest → balance never shrinks.
  if (payment <= startInterest + 1e-9 && monthlyRate > 0) {
    return {
      feasible: false,
      months: Infinity,
      totalInterest: Infinity,
      payoffDateMs: null,
      monthlyInterest: startInterest,
    };
  }
  if (payment <= 0) {
    return {
      feasible: false,
      months: Infinity,
      totalInterest: 0,
      payoffDateMs: null,
      monthlyInterest: startInterest,
    };
  }

  let balance = balance0;
  let totalInterest = 0;
  let months = 0;
  while (balance > 0.005 && months < MAX_MONTHS) {
    const interest = balance * monthlyRate;
    const applied = Math.min(payment, balance + interest);
    balance = balance + interest - applied;
    totalInterest += interest;
    months += 1;
    if (balance < 0) balance = 0;
  }

  if (balance > 0.005) {
    return {
      feasible: false,
      months: Infinity,
      totalInterest: Infinity,
      payoffDateMs: null,
      monthlyInterest: startInterest,
    };
  }

  const payoff = new Date(nowMs);
  payoff.setMonth(payoff.getMonth() + months);
  return {
    feasible: true,
    months,
    totalInterest,
    payoffDateMs: payoff.getTime(),
    monthlyInterest: startInterest,
  };
}

/** Project payoff for an account using its current effective monthly payment. */
export function projectAccountPayoff(
  account: CreditAccount,
  extraPayment = 0,
  nowMs = Date.now(),
): PayoffProjection {
  const balance0 = Math.max(0, account.currentBalance);
  const payment =
    effectiveMonthlyPayment(account) + Math.max(0, extraPayment);
  const initialRate = effectiveApr(account, nowMs) / 12;
  const startInterest = balance0 * initialRate;
  if (balance0 <= 0) {
    return {
      feasible: true,
      months: 0,
      totalInterest: 0,
      payoffDateMs: null,
      monthlyInterest: 0,
    };
  }
  if (payment <= 0) {
    return {
      feasible: false,
      months: Infinity,
      totalInterest: Infinity,
      payoffDateMs: null,
      monthlyInterest: startInterest,
    };
  }

  let balance = balance0;
  let totalInterest = 0;
  let months = 0;
  const cursor = new Date(nowMs);
  while (balance > 0.005 && months < MAX_MONTHS) {
    const monthlyRate = effectiveApr(account, cursor.getTime()) / 12;
    const interest = balance * monthlyRate;
    if (payment <= interest + 1e-9 && monthlyRate > 0) {
      return {
        feasible: false,
        months: Infinity,
        totalInterest: Infinity,
        payoffDateMs: null,
        monthlyInterest: startInterest,
      };
    }
    balance = balance + interest - Math.min(payment, balance + interest);
    totalInterest += interest;
    months += 1;
    cursor.setMonth(cursor.getMonth() + 1);
  }
  if (balance > 0.005) {
    return {
      feasible: false,
      months: Infinity,
      totalInterest: Infinity,
      payoffDateMs: null,
      monthlyInterest: startInterest,
    };
  }
  return {
    feasible: true,
    months,
    totalInterest,
    payoffDateMs: cursor.getTime(),
    monthlyInterest: startInterest,
  };
}

/**
 * Whether a revolving account is at risk of the "minimum payment trap":
 * the current payment barely beats interest, so payoff is impossible or very slow.
 */
export function isMinimumPaymentTrap(account: CreditAccount): boolean {
  if (!isRevolvingType(account.type)) return false;
  if (account.currentBalance <= 0 || effectiveApr(account) <= 0) return false;
  const proj = projectAccountPayoff(account);
  return !proj.feasible || proj.months > 360; // > 30 years
}

export type DebtPayoffSummary = {
  /** Latest payoff date across all interest-bearing accounts, or null. */
  debtFreeDateMs: number | null;
  /** Longest payoff horizon in months across feasible accounts. */
  longestMonths: number;
  /** Total projected interest across all accounts at current payments. */
  totalInterest: number;
  /** Sum of this month's interest across all accounts. */
  monthlyInterest: number;
  /** Count of accounts that won't pay off at the current payment. */
  infeasibleCount: number;
  /** True when every account with a balance is on track to be paid off. */
  allFeasible: boolean;
  /** Whether any account carries interest (drives whether to show this at all). */
  hasInterestBearingDebt: boolean;
};

export function summarizeDebtPayoff(
  accounts: CreditAccount[],
  nowMs = Date.now(),
): DebtPayoffSummary {
  let debtFreeDateMs: number | null = null;
  let longestMonths = 0;
  let totalInterest = 0;
  let monthlyInt = 0;
  let infeasibleCount = 0;
  let hasInterestBearingDebt = false;

  for (const account of accounts) {
    if (account.currentBalance <= 0) continue;
    const interest = monthlyInterest(account);
    if (interest > 0) hasInterestBearingDebt = true;
    monthlyInt += interest;

    const proj = projectAccountPayoff(account, 0, nowMs);
    if (!proj.feasible) {
      infeasibleCount += 1;
      continue;
    }
    totalInterest += proj.totalInterest;
    if (proj.months > longestMonths) longestMonths = proj.months;
    if (proj.payoffDateMs != null) {
      if (debtFreeDateMs == null || proj.payoffDateMs > debtFreeDateMs) {
        debtFreeDateMs = proj.payoffDateMs;
      }
    }
  }

  return {
    debtFreeDateMs,
    longestMonths,
    totalInterest,
    monthlyInterest: monthlyInt,
    infeasibleCount,
    allFeasible: infeasibleCount === 0,
    hasInterestBearingDebt,
  };
}

/** Format a month count as "3 yr 2 mo" / "8 mo" / "1 yr". */
export function formatMonths(months: number): string {
  if (!Number.isFinite(months) || months <= 0) return "0 mo";
  const years = Math.floor(months / 12);
  const rem = months % 12;
  if (years === 0) return `${rem} mo`;
  if (rem === 0) return `${years} yr`;
  return `${years} yr ${rem} mo`;
}

/** Format an epoch-millis payoff date as "Mar 2031". */
export function formatPayoffDate(ms: number): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    year: "numeric",
  }).format(new Date(ms));
}
