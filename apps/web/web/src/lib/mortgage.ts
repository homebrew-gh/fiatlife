import type { CreditAccount } from "./creditAccount";

export type MortgageScheduleRow = {
  paymentNumber: number;
  dateMs: number;
  payment: number;
  principal: number;
  interest: number;
  extraPrincipal: number;
  balance: number;
};

export type MortgageSummary = {
  loanAmount: number;
  monthlyPayment: number;
  monthlyTaxInsurance: number;
  estimatedMonthlyTotal: number;
  termMonths: number;
  totalInterest: number;
  totalPaid: number;
  payoffDateMs: number | null;
  paymentsRemaining: number;
  paymentsElapsed: number;
  principalPaid: number;
  interestPaid: number;
};

export type MortgageScheduleResult = {
  rows: MortgageScheduleRow[];
  summary: MortgageSummary;
};

export type MortgageScenarioInput = {
  homePrice: number;
  downPayment: number;
  annualRate: number;
  termYears: number;
  extraMonthlyPayment?: number;
  monthlyTaxInsurance?: number;
};

export type MortgageScenarioResult = MortgageScheduleResult & {
  label: string;
  homePrice: number;
  downPayment: number;
  downPaymentPercent: number;
  annualRate: number;
  termYears: number;
};

export function loanAmountFromScenario(input: MortgageScenarioInput): number {
  return Math.max(0, input.homePrice - input.downPayment);
}

export function calculateMonthlyPayment(
  principal: number,
  annualRate: number,
  termMonths: number,
): number {
  if (principal <= 0 || termMonths <= 0) return 0;
  if (annualRate <= 0) return principal / termMonths;
  const r = annualRate / 12;
  const factor = Math.pow(1 + r, termMonths);
  return (principal * r * factor) / (factor - 1);
}

function addMonths(date: Date, months: number): Date {
  const d = new Date(date);
  d.setMonth(d.getMonth() + months);
  return d;
}

function monthsBetween(start: Date, end: Date): number {
  return (
    (end.getFullYear() - start.getFullYear()) * 12 +
    (end.getMonth() - start.getMonth())
  );
}

export function buildAmortizationSchedule(input: {
  principal: number;
  annualRate: number;
  termMonths: number;
  startDateMs?: number | null;
  monthlyPayment?: number | null;
  extraMonthlyPayment?: number;
  monthlyTaxInsurance?: number;
  nowMs?: number;
}): MortgageScheduleResult {
  const principal = Math.max(0, input.principal);
  const termMonths = Math.max(0, Math.round(input.termMonths));
  const annualRate = Math.max(0, input.annualRate);
  const extra = Math.max(0, input.extraMonthlyPayment ?? 0);
  const taxIns = Math.max(0, input.monthlyTaxInsurance ?? 0);
  const nowMs = input.nowMs ?? Date.now();

  const computedPayment = calculateMonthlyPayment(principal, annualRate, termMonths);
  const monthlyPayment =
    input.monthlyPayment != null && input.monthlyPayment > 0
      ? input.monthlyPayment
      : computedPayment;

  const startDate = input.startDateMs
    ? new Date(input.startDateMs)
    : new Date(nowMs);
  startDate.setHours(0, 0, 0, 0);

  const rows: MortgageScheduleRow[] = [];
  let balance = principal;
  let totalInterest = 0;
  let paymentsElapsed = 0;

  if (input.startDateMs) {
    paymentsElapsed = Math.max(0, monthsBetween(startDate, new Date(nowMs)));
  }

  for (let n = 1; n <= termMonths && balance > 0.005; n += 1) {
    const monthlyRate = annualRate / 12;
    const interest = balance * monthlyRate;
    let principalPortion = monthlyPayment - interest;
    if (principalPortion < 0) principalPortion = 0;
    if (principalPortion > balance) principalPortion = balance;

    const extraPrincipal = Math.min(extra, Math.max(0, balance - principalPortion));
    const totalPrincipal = principalPortion + extraPrincipal;
    balance = Math.max(0, balance - totalPrincipal);
    totalInterest += interest;

    rows.push({
      paymentNumber: n,
      dateMs: addMonths(startDate, n - 1).getTime(),
      payment: principalPortion + interest + extraPrincipal,
      principal: principalPortion,
      interest,
      extraPrincipal,
      balance,
    });

    if (balance <= 0.005) break;
  }

  const lastRow = rows[rows.length - 1];
  const payoffDateMs = lastRow?.dateMs ?? null;
  const paymentsRemaining = Math.max(0, rows.length - paymentsElapsed);
  const elapsedRows = rows.slice(0, paymentsElapsed);
  const principalPaid = elapsedRows.reduce(
    (sum, r) => sum + r.principal + r.extraPrincipal,
    0,
  );
  const interestPaid = elapsedRows.reduce((sum, r) => sum + r.interest, 0);

  return {
    rows,
    summary: {
      loanAmount: principal,
      monthlyPayment,
      monthlyTaxInsurance: taxIns,
      estimatedMonthlyTotal: monthlyPayment + taxIns,
      termMonths,
      totalInterest,
      totalPaid: principal + totalInterest,
      payoffDateMs,
      paymentsRemaining,
      paymentsElapsed,
      principalPaid,
      interestPaid,
    },
  };
}

export function mortgageInputsFromAccount(
  account: CreditAccount,
): {
  principal: number;
  annualRate: number;
  termMonths: number;
  startDateMs: number | null;
  monthlyPayment: number | null;
} | null {
  if (account.type !== "MORTGAGE") return null;

  const principal =
    account.originalPrincipal > 0
      ? account.originalPrincipal
      : account.currentBalance;
  const termMonths = account.termMonths ?? 0;
  if (principal <= 0 || termMonths <= 0) return null;

  return {
    principal,
    annualRate: account.apr,
    termMonths,
    startDateMs: account.startDate ?? account.createdAt ?? null,
    monthlyPayment: account.monthlyPaymentAmount ?? null,
  };
}

export function scheduleForMortgageAccount(
  account: CreditAccount,
  nowMs = Date.now(),
): MortgageScheduleResult | null {
  const inputs = mortgageInputsFromAccount(account);
  if (!inputs) return null;

  return buildAmortizationSchedule({
    principal: inputs.principal,
    annualRate: inputs.annualRate,
    termMonths: inputs.termMonths,
    startDateMs: inputs.startDateMs,
    monthlyPayment: inputs.monthlyPayment,
    nowMs,
  });
}

export function evaluateMortgageScenario(
  label: string,
  input: MortgageScenarioInput,
): MortgageScenarioResult | null {
  const loanAmount = loanAmountFromScenario(input);
  const termMonths = Math.round(input.termYears * 12);
  if (loanAmount <= 0 || termMonths <= 0 || input.homePrice <= 0) return null;

  const schedule = buildAmortizationSchedule({
    principal: loanAmount,
    annualRate: input.annualRate / 100,
    termMonths,
    extraMonthlyPayment: input.extraMonthlyPayment,
    monthlyTaxInsurance: input.monthlyTaxInsurance,
  });

  return {
    ...schedule,
    label,
    homePrice: input.homePrice,
    downPayment: input.downPayment,
    downPaymentPercent:
      input.homePrice > 0 ? (input.downPayment / input.homePrice) * 100 : 0,
    annualRate: input.annualRate,
    termYears: input.termYears,
  };
}

export function formatMortgageDate(ms: number): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    year: "numeric",
  }).format(new Date(ms));
}

export function termYearsFromMonths(months: number | null | undefined): number {
  if (!months || months <= 0) return 30;
  return Math.round((months / 12) * 10) / 10;
}

export function termMonthsFromYears(years: number): number {
  return Math.max(1, Math.round(years * 12));
}
