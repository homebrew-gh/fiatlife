import {
  currentMonthlyPmi,
  defaultCreditAccount,
  effectiveMonthlyPayment,
  escrowedMonthlyAmount,
  monthlyHomeInsurance,
  monthlyPropertyTax,
  principalAndInterestPayment,
  type CreditAccount,
} from "./creditAccount";

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
  /** Total PMI paid over the life of the loan until it auto-drops. */
  totalPmiPaid: number;
  /** Payment number at which PMI is removed (80% LTV), or null if never charged / never drops. */
  pmiDropMonth: number | null;
  pmiDropDateMs: number | null;
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
  /** Annual property tax as a percentage of the home price. */
  propertyTaxRate?: number;
  /** Annual homeowner's insurance premium in dollars. */
  annualHomeInsurance?: number;
  /** Monthly HOA / condo dues in dollars. */
  monthlyHoa?: number;
  /**
   * Annual PMI rate as a percentage of the loan amount. Applied while the
   * down payment is below 20% of the home price.
   */
  pmiRate?: number;
  /** Estimated closing costs as a percentage of the home price. */
  closingCostPercent?: number;
  /** Gross monthly household income, used for lender affordability ratios. */
  monthlyIncome?: number;
  /** Other recurring monthly debt payments (cars, student loans, cards). */
  monthlyDebts?: number;
  /** Estimated monthly utilities (electric, gas, water, internet). */
  monthlyUtilities?: number;
  /** Whether to qualify on gross income (lender) or take-home pay (budget). */
  affordabilityMode?: AffordabilityMode;
  /** Custom ratio thresholds; defaults depend on affordabilityMode. */
  affordabilityThresholds?: AffordabilityThresholds;
};

export type AffordabilityMode = "lender" | "takehome";

/** Percent-of-income limits used to rate comfortable / stretched / risky. */
export type AffordabilityThresholds = {
  /** Max housing cost as % of income (PITI; + utilities in take-home mode). */
  comfortableHousingMax: number;
  stretchedHousingMax: number;
  /** Max housing (+ utilities) plus other debts as % of income. */
  comfortableTotalMax: number;
  stretchedTotalMax: number;
};

export const LENDER_AFFORDABILITY_THRESHOLDS: AffordabilityThresholds = {
  comfortableHousingMax: 28,
  stretchedHousingMax: 31,
  comfortableTotalMax: 36,
  stretchedTotalMax: 43,
};

/** Defaults for take-home budgeting (housing + utilities vs net pay). */
export const TAKEHOME_AFFORDABILITY_THRESHOLDS: AffordabilityThresholds = {
  comfortableHousingMax: 50,
  stretchedHousingMax: 60,
  comfortableTotalMax: 60,
  stretchedTotalMax: 75,
};

export function defaultAffordabilityThresholds(
  mode: AffordabilityMode,
): AffordabilityThresholds {
  return mode === "takehome"
    ? { ...TAKEHOME_AFFORDABILITY_THRESHOLDS }
    : { ...LENDER_AFFORDABILITY_THRESHOLDS };
}

/** Monthly breakdown of the non-principal-and-interest housing costs. */
export type MortgageCostBreakdown = {
  monthlyPropertyTax: number;
  monthlyHomeInsurance: number;
  monthlyHoa: number;
  monthlyPmi: number;
  /** Sum of the escrow / carrying costs above (excludes P&I). */
  monthlyExtraTotal: number;
};

export type AffordabilityRating = "comfortable" | "stretched" | "risky";

/** Upfront cash and debt-to-income assessment for a scenario. */
export type MortgageAffordability = {
  closingCosts: number;
  /** Down payment + closing costs = total cash needed at signing. */
  cashToClose: number;
  mode: AffordabilityMode;
  thresholds: AffordabilityThresholds;
  monthlyIncome: number;
  monthlyDebts: number;
  monthlyUtilities: number;
  /** PITI (+ PMI); excludes utilities. */
  monthlyHousingPayment: number;
  /** PITI (+ PMI) plus utilities — the numerator for the housing ratio. */
  monthlyHousingWithUtilities: number;
  /** Front-end ratio: housing (+ utilities in take-home mode) ÷ income, as %. */
  housingDti: number | null;
  /** Back-end ratio: (housing + utilities + other debts) ÷ income, as %. */
  totalDti: number | null;
  /** Income left after housing, utilities, and other debts. */
  monthlyIncomeAfterHousing: number | null;
  rating: AffordabilityRating | null;
};

export type MortgageScenarioResult = MortgageScheduleResult & {
  label: string;
  homePrice: number;
  downPayment: number;
  downPaymentPercent: number;
  annualRate: number;
  termYears: number;
  breakdown: MortgageCostBreakdown;
  affordability: MortgageAffordability;
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
  /** Constant monthly escrow (property tax + insurance + HOA), excludes PMI. */
  monthlyTaxInsurance?: number;
  /** Monthly PMI premium, charged until the balance drops to pmiDropBalance. */
  monthlyPmi?: number;
  /** Balance at/below which PMI is removed (typically 80% of home value). */
  pmiDropBalance?: number;
  nowMs?: number;
}): MortgageScheduleResult {
  const principal = Math.max(0, input.principal);
  const termMonths = Math.max(0, Math.round(input.termMonths));
  const annualRate = Math.max(0, input.annualRate);
  const extra = Math.max(0, input.extraMonthlyPayment ?? 0);
  const taxIns = Math.max(0, input.monthlyTaxInsurance ?? 0);
  const monthlyPmi = Math.max(0, input.monthlyPmi ?? 0);
  const pmiDropBalance = Math.max(0, input.pmiDropBalance ?? 0);
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

  // PMI applies while the loan balance is above the drop threshold (20% equity).
  // When no threshold is supplied but a premium is, PMI runs the whole term.
  const initialPmiActive =
    monthlyPmi > 0 && (pmiDropBalance <= 0 || principal > pmiDropBalance);
  let totalPmiPaid = 0;
  let pmiMonthsCharged = 0;
  let pmiDropMonth: number | null = null;
  let pmiDropDateMs: number | null = null;

  if (input.startDateMs) {
    paymentsElapsed = Math.max(0, monthsBetween(startDate, new Date(nowMs)));
  }

  for (let n = 1; n <= termMonths && balance > 0.005; n += 1) {
    const monthlyRate = annualRate / 12;
    const interest = balance * monthlyRate;
    let principalPortion = monthlyPayment - interest;
    if (principalPortion < 0) principalPortion = 0;
    if (principalPortion > balance) principalPortion = balance;

    // PMI for this month is based on the balance at the start of the month.
    const pmiActiveThisMonth =
      monthlyPmi > 0 && (pmiDropBalance <= 0 || balance > pmiDropBalance);
    if (pmiActiveThisMonth) {
      totalPmiPaid += monthlyPmi;
      pmiMonthsCharged += 1;
    } else if (pmiDropMonth == null && pmiMonthsCharged > 0) {
      pmiDropMonth = n;
      pmiDropDateMs = addMonths(startDate, n - 1).getTime();
    }

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
      estimatedMonthlyTotal:
        monthlyPayment + taxIns + (initialPmiActive ? monthlyPmi : 0),
      termMonths,
      totalInterest,
      totalPaid: principal + totalInterest,
      payoffDateMs,
      paymentsRemaining,
      paymentsElapsed,
      principalPaid,
      interestPaid,
      totalPmiPaid,
      pmiDropMonth,
      pmiDropDateMs,
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

  const constantEscrow =
    (account.propertyTaxEscrowed !== false
      ? monthlyPropertyTax(account)
      : 0) +
    (account.homeInsuranceEscrowed !== false
      ? monthlyHomeInsurance(account)
      : 0) +
    (account.hoaEscrowed ? Math.max(0, account.monthlyHoa ?? 0) : 0);
  const pmi =
    account.pmiEscrowed !== false ? Math.max(0, account.monthlyPmi ?? 0) : 0;

  return buildAmortizationSchedule({
    principal: inputs.principal,
    annualRate: inputs.annualRate,
    termMonths: inputs.termMonths,
    startDateMs: inputs.startDateMs,
    monthlyPayment: inputs.monthlyPayment,
    extraMonthlyPayment: account.extraMonthlyPrincipal ?? 0,
    monthlyTaxInsurance: constantEscrow,
    monthlyPmi: pmi,
    pmiDropBalance:
      (account.homePrice ?? 0) > 0 ? (account.homePrice ?? 0) * 0.8 : 0,
    nowMs,
  });
}

export function computeMortgageCostBreakdown(
  input: MortgageScenarioInput,
): MortgageCostBreakdown {
  const loanAmount = loanAmountFromScenario(input);
  const downPaymentPercent =
    input.homePrice > 0 ? (input.downPayment / input.homePrice) * 100 : 0;

  const monthlyPropertyTax = Math.max(
    0,
    (input.homePrice * ((input.propertyTaxRate ?? 0) / 100)) / 12,
  );
  const monthlyHomeInsurance = Math.max(0, (input.annualHomeInsurance ?? 0) / 12);
  const monthlyHoa = Math.max(0, input.monthlyHoa ?? 0);
  // PMI is typically required until the borrower reaches 20% equity.
  const monthlyPmi =
    downPaymentPercent < 20
      ? Math.max(0, (loanAmount * ((input.pmiRate ?? 0) / 100)) / 12)
      : 0;

  return {
    monthlyPropertyTax,
    monthlyHomeInsurance,
    monthlyHoa,
    monthlyPmi,
    monthlyExtraTotal:
      monthlyPropertyTax + monthlyHomeInsurance + monthlyHoa + monthlyPmi,
  };
}

export function rateAffordability(
  housingDti: number | null,
  totalDti: number | null,
  thresholds: AffordabilityThresholds,
): AffordabilityRating | null {
  if (housingDti == null || totalDti == null) return null;
  if (
    housingDti > thresholds.stretchedHousingMax ||
    totalDti > thresholds.stretchedTotalMax
  ) {
    return "risky";
  }
  if (
    housingDti <= thresholds.comfortableHousingMax &&
    totalDti <= thresholds.comfortableTotalMax
  ) {
    return "comfortable";
  }
  return "stretched";
}

export function computeAffordability(
  input: MortgageScenarioInput,
  monthlyHousingPayment: number,
): MortgageAffordability {
  const closingCosts = Math.max(
    0,
    input.homePrice * ((input.closingCostPercent ?? 0) / 100),
  );
  const cashToClose = Math.max(0, input.downPayment) + closingCosts;
  const mode = input.affordabilityMode ?? "lender";
  const thresholds =
    input.affordabilityThresholds ?? defaultAffordabilityThresholds(mode);
  const monthlyIncome = Math.max(0, input.monthlyIncome ?? 0);
  const monthlyDebts = Math.max(0, input.monthlyDebts ?? 0);
  const monthlyUtilities =
    mode === "takehome" ? Math.max(0, input.monthlyUtilities ?? 0) : 0;
  const monthlyHousingWithUtilities = monthlyHousingPayment + monthlyUtilities;

  const hasIncome = monthlyIncome > 0;
  const housingDti = hasIncome
    ? (monthlyHousingWithUtilities / monthlyIncome) * 100
    : null;
  const totalDti = hasIncome
    ? ((monthlyHousingWithUtilities + monthlyDebts) / monthlyIncome) * 100
    : null;
  const monthlyIncomeAfterHousing = hasIncome
    ? monthlyIncome - monthlyHousingWithUtilities - monthlyDebts
    : null;

  return {
    closingCosts,
    cashToClose,
    mode,
    thresholds,
    monthlyIncome,
    monthlyDebts,
    monthlyUtilities,
    monthlyHousingPayment,
    monthlyHousingWithUtilities,
    housingDti,
    totalDti,
    monthlyIncomeAfterHousing,
    rating: rateAffordability(housingDti, totalDti, thresholds),
  };
}

export function evaluateMortgageScenario(
  label: string,
  input: MortgageScenarioInput,
): MortgageScenarioResult | null {
  const loanAmount = loanAmountFromScenario(input);
  const termMonths = Math.round(input.termYears * 12);
  if (loanAmount <= 0 || termMonths <= 0 || input.homePrice <= 0) return null;

  const breakdown = computeMortgageCostBreakdown(input);
  // Constant escrow excludes PMI so the schedule can drop PMI at 80% LTV.
  const constantEscrow =
    breakdown.monthlyPropertyTax +
    breakdown.monthlyHomeInsurance +
    breakdown.monthlyHoa;

  const schedule = buildAmortizationSchedule({
    principal: loanAmount,
    annualRate: input.annualRate / 100,
    termMonths,
    extraMonthlyPayment: input.extraMonthlyPayment,
    monthlyTaxInsurance: constantEscrow,
    monthlyPmi: breakdown.monthlyPmi,
    pmiDropBalance: breakdown.monthlyPmi > 0 ? input.homePrice * 0.8 : 0,
  });

  const affordability = computeAffordability(
    input,
    schedule.summary.estimatedMonthlyTotal,
  );

  return {
    ...schedule,
    label,
    homePrice: input.homePrice,
    downPayment: input.downPayment,
    downPaymentPercent:
      input.homePrice > 0 ? (input.downPayment / input.homePrice) * 100 : 0,
    annualRate: input.annualRate,
    termYears: input.termYears,
    breakdown,
    affordability,
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

export type MortgagePaymentSnapshot = {
  principal: number;
  interest: number;
  extraPrincipal: number;
  escrow: number;
  pmi: number;
  remainingBalance: number;
  principalPaid: number;
  interestPaid: number;
  paymentsRemaining: number;
  paymentsElapsed: number;
  payoffDateMs: number | null;
  monthlyPi: number;
  servicerDraft: number;
};

export function currentMortgagePaymentSnapshot(
  account: CreditAccount,
  nowMs = Date.now(),
): MortgagePaymentSnapshot | null {
  if (account.type !== "MORTGAGE") return null;
  const pi = principalAndInterestPayment(account);
  if (pi <= 0 && account.currentBalance <= 0) return null;
  const balance = Math.max(0, account.currentBalance);
  const interest = account.apr > 0 ? balance * (account.apr / 12) : 0;
  let principal = Math.max(0, pi - interest);
  if (principal > balance) principal = balance;
  const extra = Math.min(
    Math.max(0, account.extraMonthlyPrincipal ?? 0),
    Math.max(0, balance - principal),
  );
  const pmi =
    account.pmiEscrowed !== false ? currentMonthlyPmi(account) : 0;
  const escrow = Math.max(0, escrowedMonthlyAmount(account) - pmi);
  const schedule = scheduleForMortgageAccount(account, nowMs);
  return {
    principal,
    interest,
    extraPrincipal: extra,
    escrow,
    pmi,
    remainingBalance: balance,
    principalPaid: schedule?.summary.principalPaid ?? 0,
    interestPaid: schedule?.summary.interestPaid ?? 0,
    paymentsRemaining: schedule?.summary.paymentsRemaining ?? 0,
    paymentsElapsed: schedule?.summary.paymentsElapsed ?? 0,
    payoffDateMs: schedule?.summary.payoffDateMs ?? null,
    monthlyPi: pi,
    servicerDraft: effectiveMonthlyPayment(account),
  };
}

export function creditAccountFromScenario(
  result: MortgageScenarioResult,
  extras?: { name?: string; dueDay?: number; startDateMs?: number },
): Omit<CreditAccount, "id" | "createdAt" | "updatedAt"> {
  const now = extras?.startDateMs ?? Date.now();
  return defaultCreditAccount({
    name: extras?.name?.trim() || "Mortgage",
    type: "MORTGAGE",
    apr: result.annualRate / 100,
    standardApr: result.annualRate / 100,
    currentBalance: result.summary.loanAmount,
    dueDay: extras?.dueDay ?? 1,
    originalPrincipal: result.summary.loanAmount,
    termMonths: termMonthsFromYears(result.termYears),
    monthlyPaymentAmount: result.summary.monthlyPayment,
    startDate: now,
    homePrice: result.homePrice,
    annualPropertyTax: result.breakdown.monthlyPropertyTax * 12,
    annualHomeInsurance: result.breakdown.monthlyHomeInsurance * 12,
    monthlyHoa: result.breakdown.monthlyHoa,
    monthlyPmi: result.breakdown.monthlyPmi,
    extraMonthlyPrincipal: result.rows[0]?.extraPrincipal ?? 0,
    propertyTaxEscrowed: true,
    homeInsuranceEscrowed: true,
    hoaEscrowed: false,
    pmiEscrowed: result.breakdown.monthlyPmi > 0,
  });
}

export function suggestedEmergencyFundTarget(
  housingMonthly: number,
  months: number,
): number {
  return Math.max(0, housingMonthly) * months;
}

export function suggestedMaintenanceAnnual(homePrice: number): number {
  return Math.max(0, homePrice) * 0.01;
}
