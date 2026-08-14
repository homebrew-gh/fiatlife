import {
  effectiveApr,
  effectiveMonthlyPayment,
  monthsUntilPromotionEnds,
  type CreditAccount,
} from "./creditAccount";
import { summarizeDebtPayoff } from "./debtPayoff";

const MAX_MONTHS = 1200;

export type PayoffStrategy = "AVALANCHE" | "SNOWBALL";

export const STRATEGY_LABELS: Record<PayoffStrategy, string> = {
  AVALANCHE: "Avalanche",
  SNOWBALL: "Snowball",
};

export const STRATEGY_DESCRIPTIONS: Record<PayoffStrategy, string> = {
  AVALANCHE: "Highest interest rate first — saves the most money.",
  SNOWBALL: "Smallest balance first — quickest early wins.",
};

export type AccountPlanResult = {
  accountId: string;
  name: string;
  /** 1-based order in which this account is fully paid off. */
  order: number;
  /** Months from now until this account hits zero. */
  payoffMonths: number;
  /** Estimated payoff date in epoch millis. */
  payoffDateMs: number | null;
  /** Total interest paid on this account under the plan. */
  totalInterest: number;
  startingBalance: number;
};

export type DebtPlan = {
  strategy: PayoffStrategy;
  /** Extra dollars per month applied on top of all minimum payments. */
  extraMonthly: number;
  /** Total committed budget per month (minimums + per-account extras + extra). */
  monthlyBudget: number;
  /** Whether all debts are repaid within the simulation horizon. */
  feasible: boolean;
  /** Months until the last debt is paid off. */
  months: number;
  /** Date the final debt is cleared. */
  debtFreeDateMs: number | null;
  /** Total interest paid across all accounts under the plan. */
  totalInterest: number;
  /** Interest paid if only minimums were paid with no rollover/extra. */
  baselineInterest: number;
  /** Interest saved versus the minimums-only baseline. */
  interestSaved: number;
  /** Months saved versus the minimums-only baseline. */
  monthsSaved: number;
  /** Per-account payoff order and dates. */
  accounts: AccountPlanResult[];
  /** Total remaining balance at the end of each month (index 0 = today's total). */
  timeline: number[];
};

type SimState = {
  account: CreditAccount;
  balance: number;
  minPayment: number;
  /** Fixed extra the user committed to this specific account each month. */
  accountExtra: number;
  interestPaid: number;
  payoffMonth: number | null;
};

function strategyOrder(
  states: SimState[],
  strategy: PayoffStrategy,
  asOfMs: number,
): SimState[] {
  const active = states.filter((s) => s.balance > 0.005);
  return active.sort((a, b) => {
    if (strategy === "AVALANCHE") {
      const rateDiff =
        effectiveApr(b.account, asOfMs) - effectiveApr(a.account, asOfMs);
      if (rateDiff !== 0) return rateDiff;
      return a.balance - b.balance;
    }
    if (a.balance !== b.balance) return a.balance - b.balance;
    return effectiveApr(b.account, asOfMs) - effectiveApr(a.account, asOfMs);
  });
}

/**
 * Simulate a debt-payoff plan using the snowball/avalanche rollover method.
 * The total monthly budget (sum of minimums + extra) is held constant; as each
 * account is cleared, its freed payment rolls into the next target account.
 */
export function buildDebtPlan(
  accounts: CreditAccount[],
  strategy: PayoffStrategy,
  extraMonthly: number,
  perAccountExtra: Record<string, number> = {},
  nowMs = Date.now(),
): DebtPlan {
  const extra = Math.max(0, extraMonthly);
  const states: SimState[] = accounts
    .filter((a) => a.currentBalance > 0.005)
    .map((a) => ({
      account: a,
      balance: a.currentBalance,
      minPayment: Math.max(0, effectiveMonthlyPayment(a)),
      accountExtra: Math.max(0, perAccountExtra[a.id] ?? 0),
      interestPaid: 0,
      payoffMonth: null,
    }));

  const minimumsTotal = states.reduce((sum, s) => sum + s.minPayment, 0);
  const accountExtrasTotal = states.reduce((sum, s) => sum + s.accountExtra, 0);
  const monthlyBudget = minimumsTotal + accountExtrasTotal + extra;

  const baseline = summarizeDebtPayoff(accounts, nowMs);

  const timeline: number[] = [
    states.reduce((sum, s) => sum + s.balance, 0),
  ];
  let month = 0;

  if (states.length > 0 && monthlyBudget > 0) {
    while (states.some((s) => s.balance > 0.005) && month < MAX_MONTHS) {
      month += 1;
      const monthStart = addMonths(nowMs, month - 1);

      for (const s of states) {
        if (s.balance <= 0.005) continue;
        const interest = s.balance * (effectiveApr(s.account, monthStart) / 12);
        s.balance += interest;
        s.interestPaid += interest;
      }

      let available = monthlyBudget;

      // First pass: minimum + this account's committed extra.
      for (const s of states) {
        if (s.balance <= 0.005 || available <= 0) continue;
        const committed = s.minPayment + s.accountExtra;
        const pay = Math.min(committed, s.balance, available);
        s.balance -= pay;
        available -= pay;
      }

      // Second pass: throw the remainder at the strategy target(s).
      for (const s of strategyOrder(states, strategy, monthStart)) {
        if (available <= 0.005) break;
        const pay = Math.min(available, s.balance);
        s.balance -= pay;
        available -= pay;
      }

      // Record any accounts cleared this month.
      for (const s of states) {
        if (s.payoffMonth == null && s.balance <= 0.005) {
          s.balance = 0;
          s.payoffMonth = month;
        }
      }

      timeline.push(states.reduce((sum, s) => sum + Math.max(0, s.balance), 0));
    }
  }

  const feasible = states.every((s) => s.payoffMonth != null);
  const months = feasible ? month : Infinity;
  const totalInterest = states.reduce((sum, s) => sum + s.interestPaid, 0);

  const accountResults: AccountPlanResult[] = states
    .map((s) => {
      const payoffDateMs =
        s.payoffMonth != null ? addMonths(nowMs, s.payoffMonth) : null;
      return {
        accountId: s.account.id,
        name: s.account.name,
        order: s.payoffMonth ?? Number.MAX_SAFE_INTEGER,
        payoffMonths: s.payoffMonth ?? Infinity,
        payoffDateMs,
        totalInterest: s.interestPaid,
        startingBalance: s.account.currentBalance,
      };
    })
    .sort((a, b) => a.payoffMonths - b.payoffMonths)
    .map((r, idx) => ({ ...r, order: idx + 1 }));

  const debtFreeDateMs = feasible ? addMonths(nowMs, month) : null;
  const interestSaved = feasible
    ? Math.max(0, baseline.totalInterest - totalInterest)
    : 0;
  const monthsSaved =
    feasible && baseline.allFeasible
      ? Math.max(0, baseline.longestMonths - month)
      : 0;

  return {
    strategy,
    extraMonthly: extra,
    monthlyBudget,
    feasible,
    months,
    debtFreeDateMs,
    totalInterest,
    baselineInterest: baseline.totalInterest,
    interestSaved,
    monthsSaved,
    accounts: accountResults,
    timeline,
  };
}

function addMonths(nowMs: number, months: number): number {
  const d = new Date(nowMs);
  d.setMonth(d.getMonth() + months);
  return d.getTime();
}

export type PromoExpiryWarning = {
  accountId: string;
  name: string;
  monthsUntilExpiry: number;
  payoffMonths: number;
  deferredInterest: boolean;
};

/** Accounts whose planned payoff is after the promotional APR ends. */
export function promoExpiryWarnings(
  accounts: CreditAccount[],
  plan: DebtPlan,
  asOf = Date.now(),
): PromoExpiryWarning[] {
  const byId = new Map(plan.accounts.map((a) => [a.accountId, a]));
  const warnings: PromoExpiryWarning[] = [];
  for (const account of accounts) {
    const monthsLeft = monthsUntilPromotionEnds(account, asOf);
    if (monthsLeft == null || monthsLeft <= 0) continue;
    const result = byId.get(account.id);
    if (!result || !Number.isFinite(result.payoffMonths)) continue;
    if (result.payoffMonths > monthsLeft) {
      warnings.push({
        accountId: account.id,
        name: account.name,
        monthsUntilExpiry: monthsLeft,
        payoffMonths: result.payoffMonths,
        deferredInterest: Boolean(account.deferredInterest),
      });
    }
  }
  return warnings;
}
