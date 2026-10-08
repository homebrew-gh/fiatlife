import {
  calculateFederalTax,
  estimateStateTaxRate,
  federalMarginalRate,
  federalStandardDeduction,
  FICA,
  overtimeDeduction,
  socialSecurityWageBase,
  yearOf,
  type FilingStatus,
} from "./tax";

export const SALARY_D_TAG = "fiatlife/salary";

export type PayFrequency = "WEEKLY" | "BIWEEKLY" | "SEMIMONTHLY" | "MONTHLY";

export const PAY_FREQUENCY_LABELS: Record<PayFrequency, string> = {
  WEEKLY: "Weekly",
  BIWEEKLY: "Biweekly",
  SEMIMONTHLY: "Semimonthly",
  MONTHLY: "Monthly",
};

export const PERIODS_PER_YEAR: Record<PayFrequency, number> = {
  WEEKLY: 52,
  BIWEEKLY: 26,
  SEMIMONTHLY: 24,
  MONTHLY: 12,
};

/** Same names as Android's `DeductionCategory`, so records round-trip. */
export type DeductionCategory =
  | "MEDICAL_INSURANCE"
  | "DENTAL_INSURANCE"
  | "VISION_INSURANCE"
  | "HSA"
  | "FSA"
  | "TRADITIONAL_401K"
  | "ROTH_401K"
  | "LIFE_INSURANCE"
  | "AD_AND_D"
  | "CRITICAL_ILLNESS"
  | "DISABILITY_INSURANCE"
  | "LEGAL_PLAN"
  | "UNION_DUES"
  | "PARKING_TRANSIT"
  | "OTHER";

export const PRE_TAX_DEDUCTION_CATEGORIES: {
  id: DeductionCategory;
  label: string;
}[] = [
  { id: "TRADITIONAL_401K", label: "Traditional 401(k)/403(b)" },
  { id: "MEDICAL_INSURANCE", label: "Medical insurance" },
  { id: "DENTAL_INSURANCE", label: "Dental insurance" },
  { id: "VISION_INSURANCE", label: "Vision insurance" },
  { id: "HSA", label: "HSA" },
  { id: "FSA", label: "FSA" },
  { id: "PARKING_TRANSIT", label: "Parking/transit" },
  { id: "OTHER", label: "Other" },
];

/** Section 125 / HSA / transit deductions also skip Social Security and Medicare. */
const FICA_EXEMPT_CATEGORIES = new Set<DeductionCategory>([
  "MEDICAL_INSURANCE",
  "DENTAL_INSURANCE",
  "VISION_INSURANCE",
  "HSA",
  "FSA",
  "PARKING_TRANSIT",
]);

export type Deduction = {
  id: string;
  name: string;
  amount: number;
  type?: "PRE_TAX" | "POST_TAX";
  category?: DeductionCategory;
  isPercentage: boolean;
  isEnabled: boolean;
};

/** Stored category, or a guess from the name for deductions saved without one. */
export function deductionCategory(d: Pick<Deduction, "name" | "category">): DeductionCategory {
  if (d.category && d.category !== "OTHER") return d.category;
  const name = d.name.toLowerCase();
  if (/roth/.test(name)) return "ROTH_401K";
  if (/401|403|457|\btsp\b|retire|pension/.test(name)) return "TRADITIONAL_401K";
  if (/\bhsa\b|health\s*sav/.test(name)) return "HSA";
  if (/\bfsa\b|flex/.test(name)) return "FSA";
  if (/dental/.test(name)) return "DENTAL_INSURANCE";
  if (/vision/.test(name)) return "VISION_INSURANCE";
  if (/medical|health|\bmed\b/.test(name)) return "MEDICAL_INSURANCE";
  if (/parking|transit|commut/.test(name)) return "PARKING_TRANSIT";
  return d.category ?? "OTHER";
}

export function isFicaExemptDeduction(d: Pick<Deduction, "name" | "category">): boolean {
  return FICA_EXEMPT_CATEGORIES.has(deductionCategory(d));
}

export type TaxLineKind =
  | "federal"
  | "state"
  | "local"
  | "socialSecurity"
  | "medicare"
  | "other";

/** Classify a paystub tax line by its label ("FITW", "Fed OASDI/EE", "Medicare"…). */
export function classifyTaxLine(label: string): TaxLineKind {
  const l = label.toLowerCase();
  if (/medicare|\bmed\b|med\/ee|\bmedi\b|\bmwt\b/.test(l)) return "medicare";
  if (/social\s*sec|oasdi|\bss\b|\bsoc\s*sec/.test(l)) return "socialSecurity";
  if (/local|city|county|school|\beit\b|\blst\b|municipal|borough|township/.test(l)) {
    return "local";
  }
  if (/fed|\bfitw?\b|\bfwt\b/.test(l)) return "federal";
  if (/state|\bsitw?\b|\bswt\b/.test(l)) return "state";
  return "other";
}

export type DirectDeposit = {
  id: string;
  accountName: string;
  bankName: string;
  accountType?: string;
  amount: number;
  isPercentage: boolean;
  isRemainder: boolean;
  sortOrder: number;
};

export type TaxOverrides = {
  federalAdditionalWithholding?: number;
  stateAdditionalWithholding?: number;
  isExemptFromFederal?: boolean;
  isExemptFromState?: boolean;
  isExemptFromLocal?: boolean;
  customFederalTaxRate?: number | null;
  customStateTaxRate?: number | null;
  customCountyTaxRate?: number | null;
  customSocialSecurityRate?: number | null;
  customMedicareRate?: number | null;
};

export type PayType = "HOURLY" | "SALARY";

export const PAY_TYPE_LABELS: Record<PayType, string> = {
  HOURLY: "Hourly",
  SALARY: "Salaried",
};

/** A single editable money line on a logged paystub (earning, tax, or deduction). */
export type PaycheckLineItem = {
  id: string;
  label: string;
  amount: number;
  /** Hours, only meaningful for earnings lines like Regular/Overtime. */
  hours?: number;
};

/** Suggested earnings categories shown when adding an earnings line. */
export const EARNINGS_CATEGORIES = [
  "Regular",
  "Overtime",
  "Bonus",
  "Commission",
  "Holiday",
  "PTO",
  "Tips",
  "Reimbursement",
  "Other",
] as const;

/**
 * An effective-dated pay-rate change (raise). The rate in effect for any given
 * date is the most recent change on or before that date, falling back to the
 * base config rate.
 */
export type PayRateChange = {
  id: string;
  effectiveDate: number;
  payType?: PayType;
  hourlyRate?: number;
  annualSalary?: number;
  standardHoursPerPeriod?: number;
  note?: string;
};

/** Web extension — Android ignores unknown JSON keys. */
export type PaycheckLogEntry = {
  id: string;
  payDate: number;
  grossPay: number;
  netPay: number;
  totalTaxes?: number;
  totalPreTaxDeductions?: number;
  totalPostTaxDeductions?: number;
  overtimeHours?: number;
  notes?: string;
  /** Itemized breakdown captured from the actual paystub. */
  earnings?: PaycheckLineItem[];
  taxes?: PaycheckLineItem[];
  preTaxDeductions?: PaycheckLineItem[];
  postTaxDeductions?: PaycheckLineItem[];
  /** Employer-side contributions (401k match, HSA) — tracked, not part of net. */
  employerContributions?: PaycheckLineItem[];
  /** Blossom hash + label of an attached paystub image/PDF. */
  attachmentHash?: string;
  attachmentLabel?: string;
  /** True when created via bulk "Generate missing" from calculator estimates. */
  autoGenerated?: boolean;
};

export type SalaryConfig = {
  id: string;
  name: string;
  payType: PayType;
  hourlyRate: number;
  /** Annual salary when payType is SALARY. */
  annualSalary?: number;
  standardHoursPerPeriod: number;
  overtimeHours: number;
  overtimeMultiplier: number;
  payFrequency: PayFrequency;
  filingStatus: FilingStatus;
  state: string;
  county: string;
  allowances?: number;
  preTaxDeductions: Deduction[];
  postTaxDeductions: Deduction[];
  directDeposits: DirectDeposit[];
  taxOverrides: TaxOverrides;
  firstPaydayOfYearMillis?: number | null;
  /** Effective-dated raises; rate in effect = latest change on/before a date. */
  payRateHistory?: PayRateChange[];
  paycheckLog?: PaycheckLogEntry[];
  updatedAt: number;
};

/** The pay rate in effect at a point in time, resolved from base + history. */
export type EffectiveRate = {
  payType: PayType;
  hourlyRate: number;
  annualSalary: number;
  standardHoursPerPeriod: number;
};

export type DeductionLine = {
  name: string;
  amount: number;
  category?: string;
};

export type DepositAllocation = {
  deposit: DirectDeposit;
  calculatedAmount: number;
};

export type PaycheckCalculation = {
  grossPay: number;
  regularPay: number;
  overtimePay: number;
  totalPreTaxDeductions: number;
  preTaxDeductionBreakdown: DeductionLine[];
  federalTax: number;
  federalMarginalRate: number;
  /** Bracket-based federal withholding ÷ taxable wages (what a custom rate replaces). */
  federalEffectiveRate: number;
  stateTax: number;
  stateTaxRate: number;
  countyTax: number;
  countyTaxRate: number;
  socialSecurity: number;
  socialSecurityRate: number;
  medicare: number;
  medicareRate: number;
  totalTaxes: number;
  totalPostTaxDeductions: number;
  postTaxDeductionBreakdown: DeductionLine[];
  netPay: number;
  depositAllocations: DepositAllocation[];
  annualizedGross: number;
  annualizedNet: number;
  effectiveTaxRate: number;
};

export function calculateDepositAllocations(
  deposits: DirectDeposit[],
  netPay: number,
): DepositAllocation[] {
  if (deposits.length === 0) return [];

  const sorted = [...deposits].sort((a, b) => a.sortOrder - b.sortOrder);
  let remaining = netPay;
  const allocations: DepositAllocation[] = [];
  const remainderDeposit = sorted.find((d) => d.isRemainder);
  const fixedDeposits = sorted.filter((d) => !d.isRemainder);

  for (const deposit of fixedDeposits) {
    const amount = deposit.isPercentage
      ? netPay * (deposit.amount / 100)
      : deposit.amount;
    const allocated = Math.max(0, Math.min(amount, remaining));
    remaining -= allocated;
    allocations.push({ deposit, calculatedAmount: allocated });
  }

  if (remainderDeposit) {
    allocations.push({
      deposit: remainderDeposit,
      calculatedAmount: Math.max(0, remaining),
    });
  }

  return allocations.sort((a, b) => a.deposit.sortOrder - b.deposit.sortOrder);
}

export type AnnualProjection = {
  annualRegularPay: number;
  annualOvertimePay: number;
  annualGrossPay: number;
  annualPreTaxDeductions: number;
  annualTotalTaxes: number;
  annualFederalTax: number;
  annualStateTax: number;
  annualCountyTax: number;
  annualSocialSecurity: number;
  annualMedicare: number;
  preTaxDeductionBreakdown: DeductionLine[];
  postTaxDeductionBreakdown: DeductionLine[];
  annualPostTaxDeductions: number;
  annualNetPay: number;
  effectiveTaxRate: number;
  marginalFederalRate: number;
  overtimeHoursUsed: number;
  perPaycheckNet: number;
};

export type YtdBreakdownLine = { label: string; amount: number; hours?: number };

export type AnnualExtrapolationOptions = {
  /** When true (default), average YTD OT hours per logged paycheck × remaining pay periods. */
  projectOvertimeForward?: boolean;
  asOf?: number;
};

/** Full-year projection from logged YTD actuals + latest pay rate for remaining periods. */
export type AnnualExtrapolation = {
  source: "logged" | "estimated";
  basedOnPaychecks: number;
  scheduledPaychecksInYear: number;
  remainingPaychecksProjected: number;
  /** Past paydays with no log, included in remainingPaychecksProjected. */
  unloggedPastPaychecks: number;
  averageOvertimeHoursPerPaycheck: number;
  projectOvertimeForward: boolean;
  /** Overtime rate ÷ regular rate (from paystubs when they itemize hours). */
  overtimeMultiplier: number;
  annualGrossPay: number;
  annualNetPay: number;
  annualTotalTaxes: number;
  annualTotalDeductions: number;
  annualPreTaxDeductions: number;
  annualPostTaxDeductions: number;
  overtimeHours: number;
  perPaycheckNet: number;
  perPaycheckGross: number;
  earnings: YtdBreakdownLine[];
  taxes: YtdBreakdownLine[];
  preTaxDeductions: YtdBreakdownLine[];
  postTaxDeductions: YtdBreakdownLine[];
  employerContributions: YtdBreakdownLine[];
  effectiveTaxRate: number;
};

/** Rough federal Form 1040 estimate from projected annual wages and withholding. */
export type FederalTaxReturnProjection = {
  annualGross: number;
  annualPreTaxDeductions: number;
  adjustedGrossIncome: number;
  standardDeduction: number;
  /** "No tax on overtime" deduction (2025–2028). */
  overtimeDeduction: number;
  federalTaxableIncome: number;
  estimatedFederalTaxOwed: number;
  federalWithheld: number;
  /** Positive = estimated refund; negative = estimated balance due. */
  refundOrBalance: number;
  /** Logged paystubs with taxes but no line recognizable as federal income tax. */
  paychecksMissingFederalLine: number;
};

export type YtdSummary = {
  year: number;
  source: "logged" | "estimated";
  paycheckCount: number;
  scheduledPaychecksYtd: number;
  scheduledPaychecksInYear: number;
  grossPay: number;
  netPay: number;
  totalTaxes: number;
  totalDeductions: number;
  totalPreTaxDeductions: number;
  totalPostTaxDeductions: number;
  overtimeHours: number;
  earnings: YtdBreakdownLine[];
  taxes: YtdBreakdownLine[];
  preTaxDeductions: YtdBreakdownLine[];
  postTaxDeductions: YtdBreakdownLine[];
  employerContributions: YtdBreakdownLine[];
  annualExtrapolation: AnnualExtrapolation;
  annualNetTarget: number;
  progressPercent: number;
  remainingPaychecks: number;
  /** Net we'd expect for the paychecks received so far (from extrapolation pace). */
  expectedNetToDate: number;
  /** Logged net minus expected net at current pace. */
  netVariance: number;
  /** Projected full-year tax withholding. */
  projectedAnnualTaxes: number;
};

/** Merge line items across entries, summing amounts/hours grouped by label. */
function mergeLines(groups: PaycheckLineItem[][]): YtdBreakdownLine[] {
  const map = new Map<string, YtdBreakdownLine>();
  for (const group of groups) {
    for (const item of group) {
      const label = item.label || "Other";
      const existing = map.get(label);
      if (existing) {
        existing.amount += item.amount;
        if (item.hours) existing.hours = (existing.hours ?? 0) + item.hours;
      } else {
        map.set(label, {
          label,
          amount: item.amount,
          hours: item.hours,
        });
      }
    }
  }
  return [...map.values()].filter((l) => l.amount !== 0 || (l.hours ?? 0) !== 0);
}

/** True when tax location / status still needs user input for accurate withholding. */
export function isTaxSetupIncomplete(
  config: Pick<SalaryConfig, "state">,
): boolean {
  return !config.state?.trim();
}

export function defaultSalaryConfig(): SalaryConfig {
  return {
    id: "",
    name: "My Salary",
    payType: "HOURLY",
    hourlyRate: 0,
    annualSalary: 0,
    standardHoursPerPeriod: 80,
    overtimeHours: 0,
    overtimeMultiplier: 1.0,
    payFrequency: "BIWEEKLY",
    filingStatus: "SINGLE",
    state: "",
    county: "",
    preTaxDeductions: [],
    postTaxDeductions: [],
    directDeposits: [],
    taxOverrides: {},
    payRateHistory: [],
    paycheckLog: [],
    updatedAt: 0,
  };
}

/** Resolve the pay rate in effect at `whenMs` from base config + raise history. */
export function effectiveRateAt(
  config: SalaryConfig,
  whenMs: number,
): EffectiveRate {
  const base: EffectiveRate = {
    payType: config.payType ?? "HOURLY",
    hourlyRate: config.hourlyRate,
    annualSalary: config.annualSalary ?? 0,
    standardHoursPerPeriod: config.standardHoursPerPeriod,
  };
  const applicable = (config.payRateHistory ?? [])
    .filter((c) => c.effectiveDate <= whenMs)
    .sort((a, b) => b.effectiveDate - a.effectiveDate)[0];
  if (!applicable) return base;
  return {
    payType: applicable.payType ?? base.payType,
    hourlyRate: applicable.hourlyRate ?? base.hourlyRate,
    annualSalary: applicable.annualSalary ?? base.annualSalary,
    standardHoursPerPeriod:
      applicable.standardHoursPerPeriod ?? base.standardHoursPerPeriod,
  };
}

/** Regular (base) gross for one pay period at a given effective rate. */
export function periodRegularGross(
  rate: EffectiveRate,
  frequency: PayFrequency,
): number {
  if (rate.payType === "SALARY") {
    return rate.annualSalary / PERIODS_PER_YEAR[frequency];
  }
  return rate.hourlyRate * rate.standardHoursPerPeriod;
}

function deductionAmount(
  d: Deduction,
  grossPay: number,
): number {
  return d.isPercentage ? grossPay * (d.amount / 100) : d.amount;
}

function calcDeductionLines(
  deductions: Deduction[],
  grossPay: number,
): { lines: DeductionLine[]; total: number } {
  const enabled = deductions.filter((d) => d.isEnabled);
  const lines = enabled.map((d) => ({
    name: d.name,
    amount: deductionAmount(d, grossPay),
    category: d.category,
  }));
  return { lines, total: lines.reduce((s, l) => s + l.amount, 0) };
}

function ficaExemptPreTax(deductions: Deduction[], grossPay: number): number {
  return deductions
    .filter((d) => d.isEnabled && isFicaExemptDeduction(d))
    .reduce((s, d) => s + deductionAmount(d, grossPay), 0);
}

type AnnualWithholding = {
  federal: number;
  state: number;
  county: number;
  socialSecurity: number;
  medicare: number;
  total: number;
  federalTaxable: number;
  federalMarginalRate: number;
  /** Federal withholding (before extra per-paycheck amounts) ÷ taxable wages. */
  federalEffectiveRate: number;
  stateRate: number;
  countyRate: number;
  socialSecurityRate: number;
  medicareRate: number;
};

/**
 * Annual withholding for a year of wages. Income taxes apply to gross minus all
 * pre-tax deductions; Social Security and Medicare apply to gross minus only the
 * FICA-exempt ones (a traditional 401(k) still owes FICA).
 */
function annualWithholding(
  config: SalaryConfig,
  input: {
    annualGross: number;
    annualPreTax: number;
    annualFicaExempt: number;
    /** Paychecks in the year, for per-paycheck extra withholding. */
    periods: number;
    year: number;
  },
): AnnualWithholding {
  const overrides = config.taxOverrides ?? {};
  const { annualGross, year, periods } = input;
  const annualTaxable = Math.max(0, annualGross - input.annualPreTax);
  const federalTaxable = Math.max(
    0,
    annualTaxable - federalStandardDeduction(config.filingStatus, year),
  );
  const customFederal = overrides.customFederalTaxRate;
  const bracketFederal = calculateFederalTax(federalTaxable, config.filingStatus, year);
  const baseFederal =
    customFederal != null ? annualTaxable * customFederal : bracketFederal;
  const federal = overrides.isExemptFromFederal
    ? 0
    : baseFederal + (overrides.federalAdditionalWithholding ?? 0) * periods;

  const stateRate = overrides.isExemptFromState
    ? 0
    : (overrides.customStateTaxRate ?? estimateStateTaxRate(config.state));
  const state = overrides.isExemptFromState
    ? 0
    : annualTaxable * stateRate + (overrides.stateAdditionalWithholding ?? 0) * periods;

  const countyRate = overrides.isExemptFromLocal
    ? 0
    : (overrides.customCountyTaxRate ?? 0);
  const county = annualTaxable * countyRate;

  const ficaWages = Math.max(0, annualGross - input.annualFicaExempt);
  const socialSecurityRate =
    overrides.customSocialSecurityRate ?? FICA.SOCIAL_SECURITY_RATE;
  const socialSecurity =
    Math.min(ficaWages, socialSecurityWageBase(year)) * socialSecurityRate;
  const medicareRate = overrides.customMedicareRate ?? FICA.MEDICARE_RATE;
  const threshold = FICA.ADDITIONAL_MEDICARE_WITHHOLDING_THRESHOLD;
  const medicare =
    ficaWages * medicareRate +
    (overrides.customMedicareRate == null && ficaWages > threshold
      ? (ficaWages - threshold) * FICA.ADDITIONAL_MEDICARE_RATE
      : 0);

  return {
    federal,
    state,
    county,
    socialSecurity,
    medicare,
    total: federal + state + county + socialSecurity + medicare,
    federalTaxable,
    federalMarginalRate:
      customFederal ?? federalMarginalRate(federalTaxable, config.filingStatus, year),
    federalEffectiveRate: annualTaxable > 0 ? bracketFederal / annualTaxable : 0,
    stateRate,
    countyRate,
    socialSecurityRate,
    medicareRate,
  };
}

export function calculatePaycheck(
  config: SalaryConfig,
  asOf = Date.now(),
): PaycheckCalculation {
  const rate = effectiveRateAt(config, asOf);
  const regularPay = periodRegularGross(rate, config.payFrequency);
  const overtimePay =
    rate.hourlyRate * config.overtimeMultiplier * config.overtimeHours;
  const grossPay = regularPay + overtimePay;
  const periodsPerYear = PERIODS_PER_YEAR[config.payFrequency];

  const pre = calcDeductionLines(config.preTaxDeductions, grossPay);
  const annualGross = grossPay * periodsPerYear;
  const taxes = annualWithholding(config, {
    annualGross,
    annualPreTax: pre.total * periodsPerYear,
    annualFicaExempt: ficaExemptPreTax(config.preTaxDeductions, grossPay) * periodsPerYear,
    periods: periodsPerYear,
    year: yearOf(asOf),
  });
  const federalTax = taxes.federal / periodsPerYear;
  const stateTax = taxes.state / periodsPerYear;
  const countyTax = taxes.county / periodsPerYear;
  const socialSecurity = taxes.socialSecurity / periodsPerYear;
  const medicare = taxes.medicare / periodsPerYear;
  const totalTaxes = taxes.total / periodsPerYear;

  const post = calcDeductionLines(config.postTaxDeductions, grossPay);
  const netPay = grossPay - pre.total - totalTaxes - post.total;
  const depositAllocations = calculateDepositAllocations(
    config.directDeposits,
    netPay,
  );

  return {
    grossPay,
    regularPay,
    overtimePay,
    totalPreTaxDeductions: pre.total,
    preTaxDeductionBreakdown: pre.lines,
    federalTax,
    federalMarginalRate: taxes.federalMarginalRate,
    federalEffectiveRate: taxes.federalEffectiveRate,
    stateTax,
    stateTaxRate: taxes.stateRate,
    countyTax,
    countyTaxRate: taxes.countyRate,
    socialSecurity,
    socialSecurityRate: taxes.socialSecurityRate,
    medicare,
    medicareRate: taxes.medicareRate,
    totalTaxes,
    totalPostTaxDeductions: post.total,
    postTaxDeductionBreakdown: post.lines,
    netPay,
    depositAllocations,
    annualizedGross: annualGross,
    annualizedNet: netPay * periodsPerYear,
    effectiveTaxRate: grossPay > 0 ? totalTaxes / grossPay : 0,
  };
}

/** Sum of base/regular gross across a year's paydays, honoring mid-year raises. */
function annualRegularPayForYear(config: SalaryConfig, year: number): number {
  const periodsPerYear = PERIODS_PER_YEAR[config.payFrequency];
  const anchor = config.firstPaydayOfYearMillis;
  const paydays = anchor
    ? enumeratePaydays(anchor, config.payFrequency, yearStart(year), yearEnd(year))
    : [];
  if (paydays.length === 0) {
    const rate = effectiveRateAt(config, Date.now());
    return periodRegularGross(rate, config.payFrequency) * periodsPerYear;
  }
  return paydays.reduce(
    (sum, d) =>
      sum + periodRegularGross(effectiveRateAt(config, d), config.payFrequency),
    0,
  );
}

export function calculateAnnual(
  config: SalaryConfig,
  annualOvertimeHours: number,
  year = new Date().getFullYear(),
): AnnualProjection {
  const periodsPerYear = scheduledPaychecksInYear(config, year);
  const annualRegularPay = annualRegularPayForYear(config, year);
  const latestRate = effectiveRateAt(config, Date.now());
  const annualOvertimePay =
    latestRate.hourlyRate * config.overtimeMultiplier * annualOvertimeHours;
  const annualGross = annualRegularPay + annualOvertimePay;
  const perPeriodGross = annualGross / periodsPerYear;

  const annualize = (deductions: Deduction[]) =>
    deductions
      .filter((d) => d.isEnabled)
      .map((d) => ({
        name: d.name,
        amount: deductionAmount(d, perPeriodGross) * periodsPerYear,
        category: d.category,
      }));
  const preTaxBreakdown = annualize(config.preTaxDeductions);
  const annualPreTax = preTaxBreakdown.reduce((s, l) => s + l.amount, 0);

  const taxes = annualWithholding(config, {
    annualGross,
    annualPreTax,
    annualFicaExempt: ficaExemptPreTax(config.preTaxDeductions, perPeriodGross) * periodsPerYear,
    periods: periodsPerYear,
    year,
  });

  const postTaxBreakdown = annualize(config.postTaxDeductions);
  const annualPostTax = postTaxBreakdown.reduce((s, l) => s + l.amount, 0);

  const annualNet = annualGross - annualPreTax - taxes.total - annualPostTax;

  return {
    annualRegularPay,
    annualOvertimePay,
    annualGrossPay: annualGross,
    annualPreTaxDeductions: annualPreTax,
    annualTotalTaxes: taxes.total,
    annualFederalTax: taxes.federal,
    annualStateTax: taxes.state,
    annualCountyTax: taxes.county,
    annualSocialSecurity: taxes.socialSecurity,
    annualMedicare: taxes.medicare,
    preTaxDeductionBreakdown: preTaxBreakdown,
    postTaxDeductionBreakdown: postTaxBreakdown,
    annualPostTaxDeductions: annualPostTax,
    annualNetPay: annualNet,
    effectiveTaxRate: annualGross > 0 ? taxes.total / annualGross : 0,
    marginalFederalRate: taxes.federalMarginalRate,
    overtimeHoursUsed: annualOvertimeHours,
    perPaycheckNet: annualNet / periodsPerYear,
  };
}

function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function yearStart(year: number): number {
  return new Date(year, 0, 1).getTime();
}

function yearEnd(year: number): number {
  return new Date(year, 11, 31, 23, 59, 59, 999).getTime();
}

/** Count paychecks in [rangeStart, rangeEnd] using first payday anchor. */
export function countPaychecksInRange(
  firstPaydayMillis: number,
  frequency: PayFrequency,
  rangeStart: number,
  rangeEnd: number,
): number {
  if (frequency === "SEMIMONTHLY") {
    let count = 0;
    const start = new Date(rangeStart);
    const end = new Date(rangeEnd);
    const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    while (cursor <= end) {
      const y = cursor.getFullYear();
      const m = cursor.getMonth();
      const mid = new Date(y, m, 15).getTime();
      const monthEnd = new Date(y, m + 1, 0).getTime();
      if (mid >= rangeStart && mid <= rangeEnd) count++;
      if (monthEnd >= rangeStart && monthEnd <= rangeEnd) count++;
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return count;
  }
  if (frequency === "MONTHLY") {
    let count = 0;
    const cursor = new Date(new Date(rangeStart).getFullYear(), new Date(rangeStart).getMonth(), 1);
    const end = new Date(rangeEnd);
    while (cursor <= end) {
      const payday = new Date(
        cursor.getFullYear(),
        cursor.getMonth(),
        Math.min(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0).getDate(), 15),
      ).getTime();
      if (payday >= rangeStart && payday <= rangeEnd) count++;
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return Math.max(count, 0);
  }

  return enumeratePaydays(firstPaydayMillis, frequency, rangeStart, rangeEnd).length;
}

/** Calendar-day step (keeps local midnight across daylight-saving changes). */
function addDays(ms: number, days: number): number {
  const d = new Date(ms);
  d.setDate(d.getDate() + days);
  return d.getTime();
}

/** Enumerate payday timestamps within [rangeStart, rangeEnd] (inclusive). */
export function enumeratePaydays(
  firstPaydayMillis: number,
  frequency: PayFrequency,
  rangeStart: number,
  rangeEnd: number,
): number[] {
  const days: number[] = [];
  if (frequency === "SEMIMONTHLY") {
    const start = new Date(rangeStart);
    const end = new Date(rangeEnd);
    const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    while (cursor <= end) {
      const y = cursor.getFullYear();
      const m = cursor.getMonth();
      const mid = new Date(y, m, 15).getTime();
      const monthEnd = new Date(y, m + 1, 0).getTime();
      if (mid >= rangeStart && mid <= rangeEnd) days.push(mid);
      if (monthEnd >= rangeStart && monthEnd <= rangeEnd) days.push(monthEnd);
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return days.sort((a, b) => a - b);
  }
  if (frequency === "MONTHLY") {
    const cursor = new Date(
      new Date(rangeStart).getFullYear(),
      new Date(rangeStart).getMonth(),
      1,
    );
    const end = new Date(rangeEnd);
    while (cursor <= end) {
      const payday = new Date(
        cursor.getFullYear(),
        cursor.getMonth(),
        Math.min(
          new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0).getDate(),
          15,
        ),
      ).getTime();
      if (payday >= rangeStart && payday <= rangeEnd) days.push(payday);
      cursor.setMonth(cursor.getMonth() + 1);
    }
    return days.sort((a, b) => a - b);
  }

  const stepDays = frequency === "WEEKLY" ? 7 : 14;
  let payday = startOfDay(firstPaydayMillis);
  for (let i = 0; i < 500 && payday <= rangeEnd; i++) {
    if (payday >= rangeStart) days.push(payday);
    payday = addDays(payday, stepDays);
  }
  return days;
}

export function scheduledPaychecksYtd(
  config: SalaryConfig,
  year: number,
  asOf = Date.now(),
): number {
  const anchor = config.firstPaydayOfYearMillis;
  const end = Math.min(asOf, yearEnd(year));
  const start = yearStart(year);

  if (!anchor) {
    const periods = PERIODS_PER_YEAR[config.payFrequency];
    const elapsed = (end - start) / (yearEnd(year) - start + 1);
    return Math.max(1, Math.floor(elapsed * periods));
  }

  return Math.max(
    0,
    countPaychecksInRange(anchor, config.payFrequency, start, end),
  );
}

export function scheduledPaychecksInYear(
  config: SalaryConfig,
  year: number,
): number {
  const anchor = config.firstPaydayOfYearMillis;
  const counted = anchor
    ? countPaychecksInRange(anchor, config.payFrequency, yearStart(year), yearEnd(year))
    : 0;
  return counted > 0 ? counted : PERIODS_PER_YEAR[config.payFrequency];
}

/**
 * Paychecks still to be received in [year]: scheduled paydays after [asOf] plus
 * past paydays that were never logged (they still arrive, just not in the log).
 */
export function remainingPaychecksForYear(
  config: SalaryConfig,
  year: number,
  asOf = Date.now(),
): { future: number; unlogged: number } {
  const anchor = canDetectMissingPaychecks(config, year)
    ? resolvePaydayAnchor(config, year, asOf)
    : null;
  if (anchor != null) {
    const from = Math.max(yearStart(year), addDays(startOfDay(asOf), 1));
    const future =
      from > yearEnd(year)
        ? 0
        : enumeratePaydays(anchor, config.payFrequency, from, yearEnd(year)).length;
    return { future, unlogged: missingPaydaysForYear(config, year, asOf).length };
  }
  const scheduledYtd = scheduledPaychecksYtd(config, year, asOf);
  return {
    future: Math.max(0, scheduledPaychecksInYear(config, year) - scheduledYtd),
    unlogged: Math.max(0, scheduledYtd - logsForYear(config, year).length),
  };
}

export function logsForYear(
  config: SalaryConfig,
  year: number,
): PaycheckLogEntry[] {
  return (config.paycheckLog ?? [])
    .filter((e) => new Date(e.payDate).getFullYear() === year)
    .sort((a, b) => b.payDate - a.payDate);
}

export const INFERRED_RAISE_ID_PREFIX = "inferred-";

export type InferredStartingPay = {
  effectiveDate: number;
  regularGrossPerPaycheck: number;
  payType: PayType;
  hourlyRate: number;
  annualSalary: number;
  standardHoursPerPeriod: number;
};

export type InferredPayRateRaise = PayRateChange & {
  percentIncrease: number;
  perPaycheckIncrease: number;
  previousRegularGross: number;
  newRegularGross: number;
};

export type InferredPayRates = {
  startingRate: InferredStartingPay | null;
  raises: InferredPayRateRaise[];
};

type ParsedRegularPay = {
  regularGross: number;
  standardHours: number;
};

function regularGrossFromLog(
  entry: PaycheckLogEntry,
  config: SalaryConfig,
): ParsedRegularPay | null {
  if (entry.grossPay <= 0) return null;

  let regularGross = entry.grossPay;
  let standardHours = config.standardHoursPerPeriod;

  if (entry.earnings?.length) {
    const regular = entry.earnings.find((l) => /^\s*regular/i.test(l.label));
    const overtime = entry.earnings.find((l) =>
      /overtime|^ot\b/i.test(l.label),
    );
    if (regular) {
      regularGross = regular.amount;
      if (regular.hours != null && regular.hours > 0) {
        standardHours = regular.hours;
      }
    } else if (overtime) {
      regularGross = Math.max(0, entry.grossPay - overtime.amount);
    }
  } else if (entry.overtimeHours != null && entry.overtimeHours > 0) {
    const rate = effectiveRateAt(config, entry.payDate);
    const overtimePay =
      rate.hourlyRate * config.overtimeMultiplier * entry.overtimeHours;
    regularGross = Math.max(0, entry.grossPay - overtimePay);
  }

  if (regularGross <= 0) return null;
  return { regularGross, standardHours };
}

function impliedRateFromRegularGross(
  regularGross: number,
  payType: PayType,
  frequency: PayFrequency,
  standardHours: number,
): Pick<EffectiveRate, "hourlyRate" | "annualSalary"> {
  if (payType === "SALARY") {
    return {
      hourlyRate: 0,
      annualSalary: regularGross * PERIODS_PER_YEAR[frequency],
    };
  }
  return {
    hourlyRate: standardHours > 0 ? regularGross / standardHours : 0,
    annualSalary: 0,
  };
}

/** Detect starting pay and raises from logged paycheck regular earnings for a year. */
export function inferPayRatesFromLogs(
  config: SalaryConfig,
  year: number,
): InferredPayRates {
  const logs = [...logsForYear(config, year)].sort(
    (a, b) => a.payDate - b.payDate,
  );
  if (logs.length === 0) {
    return { startingRate: null, raises: [] };
  }

  let previousRegular: number | null = null;
  let startingRate: InferredStartingPay | null = null;
  const raises: InferredPayRateRaise[] = [];

  for (const log of logs) {
    const parsed = regularGrossFromLog(log, config);
    if (!parsed) continue;

    const { regularGross, standardHours } = parsed;
    const payType = config.payType;
    const implied = impliedRateFromRegularGross(
      regularGross,
      payType,
      config.payFrequency,
      standardHours,
    );

    if (previousRegular == null) {
      startingRate = {
        effectiveDate: log.payDate,
        regularGrossPerPaycheck: regularGross,
        payType,
        hourlyRate: implied.hourlyRate,
        annualSalary: implied.annualSalary,
        standardHoursPerPeriod: standardHours,
      };
      previousRegular = regularGross;
      continue;
    }

    if (regularGross > previousRegular + 0.01) {
      const perPaycheckIncrease = regularGross - previousRegular;
      const percentIncrease =
        previousRegular > 0
          ? (perPaycheckIncrease / previousRegular) * 100
          : 0;
      raises.push({
        id: `${INFERRED_RAISE_ID_PREFIX}${log.payDate}`,
        effectiveDate: log.payDate,
        payType,
        hourlyRate: implied.hourlyRate,
        annualSalary: implied.annualSalary,
        standardHoursPerPeriod: standardHours,
        note: `+${percentIncrease.toFixed(1)}% (+${perPaycheckIncrease.toFixed(2)}/paycheck)`,
        percentIncrease,
        perPaycheckIncrease,
        previousRegularGross: previousRegular,
        newRegularGross: regularGross,
      });
      previousRegular = regularGross;
    }
  }

  return { startingRate, raises };
}

function payRateYearsInLog(log: PaycheckLogEntry[]): number[] {
  return [
    ...new Set(log.map((e) => new Date(e.payDate).getFullYear())),
  ];
}

/** Merge inferred raises from logs into config; preserves manually added raises. */
export function applyInferredPayRatesToConfig(
  config: SalaryConfig,
  year: number,
): SalaryConfig {
  const yearLogs = logsForYear(config, year);
  const manualRaises = (config.payRateHistory ?? []).filter(
    (h) => !h.id.startsWith(INFERRED_RAISE_ID_PREFIX),
  );
  const otherInferred = (config.payRateHistory ?? []).filter(
    (h) =>
      h.id.startsWith(INFERRED_RAISE_ID_PREFIX) &&
      new Date(h.effectiveDate).getFullYear() !== year,
  );

  if (yearLogs.length === 0) {
    const cleaned = [...manualRaises, ...otherInferred];
    if (cleaned.length === (config.payRateHistory?.length ?? 0)) {
      return config;
    }
    return normalizeSalaryConfig({ ...config, payRateHistory: cleaned });
  }

  const inferred = inferPayRatesFromLogs(config, year);
  const inferredRaises: PayRateChange[] = inferred.raises.map(
    ({
      percentIncrease: _p,
      perPaycheckIncrease: _d,
      previousRegularGross: _pr,
      newRegularGross: _nr,
      ...change
    }) => change,
  );

  let next: SalaryConfig = {
    ...config,
    payRateHistory: [...manualRaises, ...otherInferred, ...inferredRaises],
  };

  if (inferred.startingRate) {
    next = {
      ...next,
      payType: inferred.startingRate.payType,
      hourlyRate: inferred.startingRate.hourlyRate,
      annualSalary: inferred.startingRate.annualSalary,
      standardHoursPerPeriod: inferred.startingRate.standardHoursPerPeriod,
    };
  }

  return normalizeSalaryConfig(next);
}

/** Apply inferred pay rates for every year present in the paycheck log. */
export function applyInferredPayRatesForAllLogYears(
  config: SalaryConfig,
): SalaryConfig {
  const logYears = payRateYearsInLog(config.paycheckLog ?? []);
  const inferredYears = (config.payRateHistory ?? [])
    .filter((h) => h.id.startsWith(INFERRED_RAISE_ID_PREFIX))
    .map((h) => new Date(h.effectiveDate).getFullYear());
  const years = new Set([...logYears, ...inferredYears]);
  let next = config;
  for (const year of years) {
    next = applyInferredPayRatesToConfig(next, year);
  }
  return next;
}

function payPeriodStepDays(frequency: PayFrequency): number | null {
  if (frequency === "WEEKLY") return 7;
  if (frequency === "BIWEEKLY") return 14;
  return null;
}

/**
 * Best anchor for scheduled payday enumeration. For weekly/biweekly pay, prefers an
 * anchor that matches logged paycheck dates (including when first payday was set
 * after logging or set to a nearby but incorrect date).
 */
export function resolvePaydayAnchor(
  config: SalaryConfig,
  year: number,
  asOf = Date.now(),
): number | null {
  const freq = config.payFrequency;
  if (freq !== "WEEKLY" && freq !== "BIWEEKLY") {
    return config.firstPaydayOfYearMillis ?? yearStart(year);
  }

  const loggedDays = logsForYear(config, year)
    .map((e) => startOfDay(e.payDate))
    .filter((d) => d <= startOfDay(asOf));

  if (loggedDays.length === 0) {
    return config.firstPaydayOfYearMillis ?? null;
  }

  const stepDays = payPeriodStepDays(freq)!;
  const rangeEnd = Math.min(asOf, yearEnd(year));
  const rangeStart = yearStart(year);
  const configured =
    config.firstPaydayOfYearMillis != null
      ? startOfDay(config.firstPaydayOfYearMillis)
      : null;

  const candidates = new Set<number>();
  if (configured != null) candidates.add(configured);
  for (const logged of loggedDays) {
    candidates.add(logged);
    for (let k = 0; k < 30; k++) {
      candidates.add(addDays(logged, -k * stepDays));
    }
  }

  let bestAnchor: number | null = null;
  let bestScore = -1;
  let bestPreferConfigured = false;

  for (const anchor of candidates) {
    const scheduled = new Set(
      enumeratePaydays(anchor, freq, rangeStart, rangeEnd),
    );
    const matched = loggedDays.filter((d) => scheduled.has(d)).length;
    const preferConfigured = configured != null && anchor === configured;
    if (
      matched > bestScore ||
      (matched === bestScore && preferConfigured && !bestPreferConfigured)
    ) {
      bestScore = matched;
      bestAnchor = anchor;
      bestPreferConfigured = preferConfigured;
    }
  }

  return bestAnchor;
}

export function canDetectMissingPaychecks(
  config: SalaryConfig,
  year?: number,
): boolean {
  if (config.payFrequency === "WEEKLY" || config.payFrequency === "BIWEEKLY") {
    if (config.firstPaydayOfYearMillis != null) return true;
    if (year != null && logsForYear(config, year).length > 0) return true;
    return false;
  }
  return true;
}

/** Scheduled paydays in [year] through [asOf] that have no matching log entry. */
export function missingPaydaysForYear(
  config: SalaryConfig,
  year: number,
  asOf = Date.now(),
): number[] {
  if (!canDetectMissingPaychecks(config, year)) return [];
  const anchor = resolvePaydayAnchor(config, year, asOf);
  if (anchor == null) return [];
  const scheduled = enumeratePaydays(
    anchor,
    config.payFrequency,
    yearStart(year),
    Math.min(asOf, yearEnd(year)),
  );
  if (scheduled.length === 0) return [];
  const loggedDays = new Set(
    logsForYear(config, year).map((e) => startOfDay(e.payDate)),
  );
  return scheduled.filter((day) => !loggedDays.has(day));
}

/**
 * Paychecks in a typical conservative month (e.g. two for biweekly — not
 * occasional three-paycheck months).
 */
export function conservativePaychecksPerMonth(
  frequency: PayFrequency,
): number {
  return Math.max(1, Math.floor(PERIODS_PER_YEAR[frequency] / 12));
}

export type ConservativeMonthlyTakeHome = {
  /** Base net per paycheck (no overtime). */
  perPaycheckNet: number;
  paychecksPerMonth: number;
  /** perPaycheckNet × paychecksPerMonth */
  monthlyTakeHome: number;
};

/**
 * Conservative monthly take-home for affordability budgeting: base-rate net pay
 * after taxes and deductions, times paychecks in a typical month (two for
 * biweekly/semi-monthly). Excludes overtime, bonuses, and logged paycheck variance.
 */
export function computeConservativeMonthlyTakeHome(
  config: SalaryConfig,
  asOf = Date.now(),
): ConservativeMonthlyTakeHome {
  const perPaycheckNet = calculatePaycheck(
    { ...config, overtimeHours: 0 },
    asOf,
  ).netPay;
  const paychecksPerMonth = conservativePaychecksPerMonth(config.payFrequency);
  return {
    perPaycheckNet,
    paychecksPerMonth,
    monthlyTakeHome: perPaycheckNet * paychecksPerMonth,
  };
}

export type MonthlyTakeHomeSource = "logged" | "estimated" | "mixed";

export type MonthlyTakeHomeProjection = {
  totalTakeHome: number;
  totalGross: number;
  totalTaxes: number;
  totalDeductions: number;
  loggedTakeHome: number;
  loggedGross: number;
  loggedTaxes: number;
  loggedDeductions: number;
  projectedRemainder: number;
  projectedGross: number;
  projectedTaxes: number;
  projectedDeductions: number;
  perPaycheckNet: number;
  loggedPaycheckCount: number;
  remainingPaycheckCount: number;
  scheduledPaychecksInMonth: number;
  loggedOvertimeHours: number;
  loggedBonusTotal: number;
  source: MonthlyTakeHomeSource;
};

export function monthBoundsFromAnchor(monthAnchorMillis: number): {
  start: number;
  end: number;
} {
  const start = new Date(monthAnchorMillis);
  start.setDate(1);
  start.setHours(0, 0, 0, 0);
  const end = new Date(start);
  end.setMonth(end.getMonth() + 1);
  end.setMilliseconds(-1);
  return { start: start.getTime(), end: end.getTime() };
}

/** Paychecks scheduled in the calendar month containing [monthAnchorMillis]. */
export function paycheckCountInMonth(
  config: SalaryConfig | null,
  monthAnchorMillis = Date.now(),
): number {
  if (!config) return 0;
  const scheduled = scheduledPaydaysInMonth(config, monthAnchorMillis);
  if (scheduled.length > 0) return scheduled.length;
  if (config.payFrequency === "SEMIMONTHLY") return 2;
  if (config.payFrequency === "MONTHLY") return 1;
  if (!config.firstPaydayOfYearMillis) {
    return PERIODS_PER_YEAR[config.payFrequency] / 12;
  }
  const { start, end } = monthBoundsFromAnchor(monthAnchorMillis);
  return Math.max(
    1,
    countPaychecksInRange(
      config.firstPaydayOfYearMillis,
      config.payFrequency,
      start,
      end,
    ),
  );
}

function logsInMonth(
  config: SalaryConfig,
  monthAnchorMillis: number,
): PaycheckLogEntry[] {
  const { start, end } = monthBoundsFromAnchor(monthAnchorMillis);
  return (config.paycheckLog ?? [])
    .filter((e) => e.payDate >= start && e.payDate <= end)
    .sort((a, b) => a.payDate - b.payDate);
}

/** Scheduled pay dates in the calendar month containing [monthAnchorMillis]. */
export function scheduledPaydaysInMonth(
  config: SalaryConfig,
  monthAnchorMillis: number,
): number[] {
  const { start, end } = monthBoundsFromAnchor(monthAnchorMillis);
  const year = new Date(monthAnchorMillis).getFullYear();
  const anchor =
    resolvePaydayAnchor(config, year, end) ??
    config.firstPaydayOfYearMillis ??
    (config.payFrequency === "WEEKLY" ||
    config.payFrequency === "BIWEEKLY"
      ? null
      : yearStart(year));
  if (anchor == null) return [];
  return enumeratePaydays(anchor, config.payFrequency, start, end);
}

function bonusFromEntry(entry: PaycheckLogEntry): number {
  return (entry.earnings ?? [])
    .filter((line) => /bonus/i.test(line.label))
    .reduce((sum, line) => sum + line.amount, 0);
}

/**
 * Monthly take-home: logged paychecks this month plus base-rate estimates for
 * remaining scheduled pay dates (no overtime projected forward).
 */
export function computeMonthlyTakeHome(
  config: SalaryConfig,
  monthAnchorMillis = Date.now(),
  asOf = Date.now(),
): MonthlyTakeHomeProjection {
  const logs = logsInMonth(config, monthAnchorMillis);
  const loggedTakeHome = logs.reduce((s, e) => s + e.netPay, 0);
  const loggedGross = logs.reduce((s, e) => s + e.grossPay, 0);
  const loggedTaxes = logs.reduce((s, e) => s + (e.totalTaxes ?? 0), 0);
  const loggedPreTax = logs.reduce(
    (s, e) => s + (e.totalPreTaxDeductions ?? 0),
    0,
  );
  const loggedPostTax = logs.reduce(
    (s, e) => s + (e.totalPostTaxDeductions ?? 0),
    0,
  );
  const loggedDeductions = loggedPreTax + loggedPostTax;
  const loggedOvertimeHours = logs.reduce(
    (s, e) => s + entryOvertimeHours(e),
    0,
  );
  const loggedBonusTotal = logs.reduce((s, e) => s + bonusFromEntry(e), 0);
  const loggedDays = new Set(logs.map((e) => startOfDay(e.payDate)));

  const baseCalc = calculatePaycheck({ ...config, overtimeHours: 0 }, asOf);
  const perPaycheckNet = baseCalc.netPay;

  const scheduled = scheduledPaydaysInMonth(config, monthAnchorMillis);
  const scheduledCount =
    scheduled.length > 0
      ? scheduled.length
      : paycheckCountInMonth(config, monthAnchorMillis);

  const remainingCount =
    scheduled.length > 0
      ? scheduled.filter((day) => !loggedDays.has(startOfDay(day))).length
      : Math.max(0, Math.round(scheduledCount) - logs.length);

  const projectedRemainder = remainingCount * perPaycheckNet;
  const projectedGross = remainingCount * baseCalc.grossPay;
  const projectedTaxes = remainingCount * baseCalc.totalTaxes;
  const projectedDeductions =
    remainingCount *
    (baseCalc.totalPreTaxDeductions + baseCalc.totalPostTaxDeductions);

  const totalTakeHome = loggedTakeHome + projectedRemainder;
  const totalGross = loggedGross + projectedGross;
  const totalTaxes = loggedTaxes + projectedTaxes;
  const totalDeductions = loggedDeductions + projectedDeductions;

  let source: MonthlyTakeHomeSource = "estimated";
  if (logs.length > 0 && remainingCount === 0) source = "logged";
  else if (logs.length > 0 && remainingCount > 0) source = "mixed";

  return {
    totalTakeHome,
    totalGross,
    totalTaxes,
    totalDeductions,
    loggedTakeHome,
    loggedGross,
    loggedTaxes,
    loggedDeductions,
    projectedRemainder,
    projectedGross,
    projectedTaxes,
    projectedDeductions,
    perPaycheckNet,
    loggedPaycheckCount: logs.length,
    remainingPaycheckCount: remainingCount,
    scheduledPaychecksInMonth: scheduledCount,
    loggedOvertimeHours,
    loggedBonusTotal,
    source,
  };
}

function entryOvertimeHours(e: PaycheckLogEntry): number {
  if (e.overtimeHours != null) return e.overtimeHours;
  const otLine = (e.earnings ?? []).find((l) =>
    /overtime|^ot\b/i.test(l.label),
  );
  return otLine?.hours ?? 0;
}

function entryEarnings(e: PaycheckLogEntry): PaycheckLineItem[] {
  if (e.earnings && e.earnings.length > 0) return e.earnings;
  return [{ id: e.id, label: "Earnings", amount: e.grossPay }];
}

function entryTaxes(e: PaycheckLogEntry): PaycheckLineItem[] {
  if (e.taxes && e.taxes.length > 0) return e.taxes;
  return e.totalTaxes != null
    ? [{ id: e.id, label: "Taxes", amount: e.totalTaxes }]
    : [];
}

function entryPreTax(e: PaycheckLogEntry): PaycheckLineItem[] {
  if (e.preTaxDeductions && e.preTaxDeductions.length > 0) return e.preTaxDeductions;
  return e.totalPreTaxDeductions
    ? [{ id: e.id, label: "Pre-tax deductions", amount: e.totalPreTaxDeductions }]
    : [];
}

function entryPostTax(e: PaycheckLogEntry): PaycheckLineItem[] {
  if (e.postTaxDeductions && e.postTaxDeductions.length > 0) return e.postTaxDeductions;
  return e.totalPostTaxDeductions
    ? [{ id: e.id, label: "Post-tax deductions", amount: e.totalPostTaxDeductions }]
    : [];
}

const OVERTIME_LABEL = /overtime|^ot\b/i;
const REGULAR_LABEL = /regular|^reg\b|base|salary|straight/i;

/** Paystubs that best describe current withholding: newest real (not generated) stubs. */
const RECENT_STUBS_FOR_RATES = 3;

function recentStubs(logs: PaycheckLogEntry[]): PaycheckLogEntry[] {
  const real = logs.filter((e) => !e.autoGenerated && e.grossPay > 0);
  const pool = real.length > 0 ? real : logs.filter((e) => e.grossPay > 0);
  return pool.slice(-RECENT_STUBS_FOR_RATES);
}

/** Each label's share of gross across [stubs]; null when the stubs have no such lines. */
function lineRates(
  stubs: PaycheckLogEntry[],
  lines: (e: PaycheckLogEntry) => PaycheckLineItem[],
): { label: string; rate: number }[] | null {
  const gross = stubs.reduce((s, e) => s + e.grossPay, 0);
  if (gross <= 0) return null;
  const merged = mergeLines(stubs.map(lines));
  if (merged.length === 0) return null;
  return merged.map((l) => ({ label: l.label, rate: l.amount / gross }));
}

/** Overtime ÷ regular hourly rate on the newest stub that itemizes both with hours. */
function impliedOvertimeMultiplier(logs: PaycheckLogEntry[]): number | null {
  for (let i = logs.length - 1; i >= 0; i--) {
    const earnings = logs[i]!.earnings ?? [];
    const ot = earnings.find((l) => OVERTIME_LABEL.test(l.label) && (l.hours ?? 0) > 0);
    const reg = earnings.find(
      (l) => !OVERTIME_LABEL.test(l.label) && REGULAR_LABEL.test(l.label) && (l.hours ?? 0) > 0,
    );
    if (!ot || !reg || reg.amount <= 0) continue;
    const multiplier = ot.amount / ot.hours! / (reg.amount / reg.hours!);
    if (multiplier >= 1 && multiplier <= 3) return multiplier;
  }
  return null;
}

/** Pay above straight time on overtime lines, the part the 2025–2028 deduction covers. */
function overtimePremium(
  earnings: YtdBreakdownLine[],
  multiplier: number,
): number {
  const otPay = earnings
    .filter((l) => OVERTIME_LABEL.test(l.label))
    .reduce((s, l) => s + l.amount, 0);
  if (otPay <= 0 || multiplier <= 1) return 0;
  return (otPay * (multiplier - 1)) / multiplier;
}

function mergeBreakdownLineGroups(
  groups: YtdBreakdownLine[][],
): YtdBreakdownLine[] {
  const map = new Map<string, YtdBreakdownLine>();
  for (const group of groups) {
    for (const line of group) {
      const existing = map.get(line.label);
      if (existing) {
        existing.amount += line.amount;
        if (line.hours != null) {
          existing.hours = (existing.hours ?? 0) + line.hours;
        }
      } else {
        map.set(line.label, { ...line });
      }
    }
  }
  return [...map.values()].filter(
    (l) => l.amount !== 0 || (l.hours ?? 0) !== 0,
  );
}

function scaleBreakdownForRemaining(
  lines: YtdBreakdownLine[],
  remaining: number,
): YtdBreakdownLine[] {
  return lines.map((line) => ({
    label: line.label,
    amount: line.amount * remaining,
    hours: line.hours != null ? line.hours * remaining : undefined,
  }));
}

function forwardBreakdownFromCalc(
  calc: PaycheckCalculation,
  overtimeHours: number,
  config: SalaryConfig,
): {
  earnings: YtdBreakdownLine[];
  taxes: YtdBreakdownLine[];
  preTaxDeductions: YtdBreakdownLine[];
  postTaxDeductions: YtdBreakdownLine[];
} {
  const earnings: YtdBreakdownLine[] = [
    { label: "Regular", amount: calc.regularPay },
  ];
  if (calc.overtimePay > 0) {
    earnings.push({
      label: "Overtime",
      amount: calc.overtimePay,
      hours: overtimeHours,
    });
  }
  const taxes: YtdBreakdownLine[] = [
    { label: "Federal income tax", amount: calc.federalTax },
    { label: "State income tax", amount: calc.stateTax },
    { label: "Social Security", amount: calc.socialSecurity },
    { label: "Medicare", amount: calc.medicare },
  ];
  if (calc.countyTax > 0) {
    taxes.splice(2, 0, {
      label: config.county.trim() ? `${config.county} tax` : "Local tax",
      amount: calc.countyTax,
    });
  }
  return {
    earnings,
    taxes: taxes.filter((t) => t.amount !== 0),
    preTaxDeductions: calc.preTaxDeductionBreakdown.map((l) => ({
      label: l.name || "Pre-tax",
      amount: l.amount,
    })),
    postTaxDeductions: calc.postTaxDeductionBreakdown.map((l) => ({
      label: l.name || "Post-tax",
      amount: l.amount,
    })),
  };
}

function extrapolationFromModel(
  modelAnnual: AnnualProjection,
  scheduledInYear: number,
  overtimeMultiplier: number,
): AnnualExtrapolation {
  const perPaycheckGross =
    scheduledInYear > 0 ? modelAnnual.annualGrossPay / scheduledInYear : 0;
  return {
    source: "estimated",
    basedOnPaychecks: 0,
    scheduledPaychecksInYear: scheduledInYear,
    remainingPaychecksProjected: scheduledInYear,
    unloggedPastPaychecks: 0,
    averageOvertimeHoursPerPaycheck: 0,
    projectOvertimeForward: true,
    overtimeMultiplier,
    annualGrossPay: modelAnnual.annualGrossPay,
    annualNetPay: modelAnnual.annualNetPay,
    annualTotalTaxes: modelAnnual.annualTotalTaxes,
    annualTotalDeductions:
      modelAnnual.annualPreTaxDeductions + modelAnnual.annualPostTaxDeductions,
    annualPreTaxDeductions: modelAnnual.annualPreTaxDeductions,
    annualPostTaxDeductions: modelAnnual.annualPostTaxDeductions,
    overtimeHours: modelAnnual.overtimeHoursUsed,
    perPaycheckNet: modelAnnual.perPaycheckNet,
    perPaycheckGross,
    earnings: [
      { label: "Regular", amount: modelAnnual.annualRegularPay },
      ...(modelAnnual.annualOvertimePay > 0
        ? [
            {
              label: "Overtime",
              amount: modelAnnual.annualOvertimePay,
              hours: modelAnnual.overtimeHoursUsed,
            },
          ]
        : []),
    ],
    taxes: [
      { label: "Federal income tax", amount: modelAnnual.annualFederalTax },
      { label: "State income tax", amount: modelAnnual.annualStateTax },
      ...(modelAnnual.annualCountyTax > 0
        ? [{ label: "County/local tax", amount: modelAnnual.annualCountyTax }]
        : []),
      { label: "Social Security", amount: modelAnnual.annualSocialSecurity },
      { label: "Medicare", amount: modelAnnual.annualMedicare },
    ].filter((line) => line.amount !== 0),
    preTaxDeductions: modelAnnual.preTaxDeductionBreakdown.map((line) => ({
      label: line.name || "Pre-tax",
      amount: line.amount,
    })),
    postTaxDeductions: modelAnnual.postTaxDeductionBreakdown.map((line) => ({
      label: line.name || "Post-tax",
      amount: line.amount,
    })),
    employerContributions: [],
    effectiveTaxRate: modelAnnual.effectiveTaxRate,
  };
}

/**
 * Project the year from logged YTD actuals plus the latest pay rate for remaining
 * scheduled paychecks. OT hours average over logged paychecks and extend forward
 * when projectOvertimeForward is true.
 */
export function extrapolateAnnualFromLogs(
  config: SalaryConfig,
  calc: PaycheckCalculation,
  modelAnnual: AnnualProjection,
  year: number,
  options: AnnualExtrapolationOptions = {},
): AnnualExtrapolation {
  const projectOvertimeForward = options.projectOvertimeForward !== false;
  const asOf = options.asOf ?? Date.now();
  const scheduledInYear = scheduledPaychecksInYear(config, year);
  const logs = [...logsForYear(config, year)].sort(
    (a, b) => a.payDate - b.payDate,
  );

  if (logs.length === 0) {
    return extrapolationFromModel(modelAnnual, scheduledInYear, config.overtimeMultiplier);
  }

  const { future, unlogged } = remainingPaychecksForYear(config, year, asOf);
  const remaining = future + unlogged;
  const n = logs.length;
  const latest = logs[logs.length - 1];
  const rate = effectiveRateAt(config, latest.payDate);
  const ytdNet = logs.reduce((s, e) => s + e.netPay, 0);
  const ytdOTHours = logs.reduce((s, e) => s + entryOvertimeHours(e), 0);
  const avgOTPerPaycheck = n > 0 ? ytdOTHours / n : 0;
  const forwardOT = projectOvertimeForward ? avgOTPerPaycheck : 0;
  const otMultiplier = impliedOvertimeMultiplier(logs) ?? config.overtimeMultiplier;

  const forwardConfig: SalaryConfig = {
    ...config,
    payType: rate.payType,
    hourlyRate: rate.hourlyRate,
    annualSalary: rate.annualSalary,
    standardHoursPerPeriod: rate.standardHoursPerPeriod,
    overtimeHours: forwardOT,
    overtimeMultiplier: otMultiplier,
  };
  const forwardPerPaycheck = calculatePaycheck(
    forwardConfig,
    Math.min(Math.max(asOf, latest.payDate), yearEnd(year)),
  );
  const modelBreakdown = forwardBreakdownFromCalc(
    forwardPerPaycheck,
    forwardOT,
    config,
  );
  const forwardGross = forwardPerPaycheck.grossPay;

  const stubs = recentStubs(logs);
  const stubEarnings = stubs.flatMap((e) => e.earnings ?? []);
  const overtimeLabel =
    stubEarnings.find((l) => OVERTIME_LABEL.test(l.label))?.label ?? "Overtime";
  const regularLabel =
    stubEarnings.find((l) => REGULAR_LABEL.test(l.label) && !OVERTIME_LABEL.test(l.label))
      ?.label ?? "Regular";
  const fromStubs = (
    lines: (e: PaycheckLogEntry) => PaycheckLineItem[],
    fallback: YtdBreakdownLine[],
  ): YtdBreakdownLine[] =>
    lineRates(stubs, lines)?.map((r) => ({ label: r.label, amount: r.rate * forwardGross })) ??
    fallback;

  const forwardEarnings = modelBreakdown.earnings.map((line) => ({
    ...line,
    label: line.label === "Overtime" ? overtimeLabel : regularLabel,
  }));
  const forwardTaxes = scaleBreakdownForRemaining(
    fromStubs(entryTaxes, modelBreakdown.taxes),
    remaining,
  );
  const forwardPreTax = scaleBreakdownForRemaining(
    fromStubs(entryPreTax, modelBreakdown.preTaxDeductions),
    remaining,
  );
  const forwardPostTax = scaleBreakdownForRemaining(
    fromStubs(entryPostTax, modelBreakdown.postTaxDeductions),
    remaining,
  );

  const ytdTaxLines = mergeLines(logs.map(entryTaxes));
  const ssRate =
    config.taxOverrides?.customSocialSecurityRate ?? FICA.SOCIAL_SECURITY_RATE;
  const ytdSocialSecurity = ytdTaxLines
    .filter((l) => classifyTaxLine(l.label) === "socialSecurity")
    .reduce((s, l) => s + l.amount, 0);
  const socialSecurityRoom = Math.max(
    0,
    socialSecurityWageBase(year) * ssRate - ytdSocialSecurity,
  );
  const forwardSocialSecurity = forwardTaxes
    .filter((l) => classifyTaxLine(l.label) === "socialSecurity")
    .reduce((s, l) => s + l.amount, 0);
  if (forwardSocialSecurity > socialSecurityRoom) {
    const scale = socialSecurityRoom / forwardSocialSecurity;
    for (const line of forwardTaxes) {
      if (classifyTaxLine(line.label) === "socialSecurity") line.amount *= scale;
    }
  }

  const total = (lines: YtdBreakdownLine[]) => lines.reduce((s, l) => s + l.amount, 0);
  const earnings = mergeBreakdownLineGroups([
    mergeLines(logs.map(entryEarnings)),
    scaleBreakdownForRemaining(forwardEarnings, remaining),
  ]);
  const taxes = mergeBreakdownLineGroups([ytdTaxLines, forwardTaxes]);
  const preTaxDeductions = mergeBreakdownLineGroups([
    mergeLines(logs.map(entryPreTax)),
    forwardPreTax,
  ]);
  const postTaxDeductions = mergeBreakdownLineGroups([
    mergeLines(logs.map(entryPostTax)),
    forwardPostTax,
  ]);

  const forwardGrossTotal = forwardGross * remaining;
  const annualGrossPay = logs.reduce((s, e) => s + e.grossPay, 0) + forwardGrossTotal;
  const annualTotalTaxes = total(taxes);
  const annualPreTaxDeductions = total(preTaxDeductions);
  const annualPostTaxDeductions = total(postTaxDeductions);
  const annualNetPay =
    ytdNet +
    forwardGrossTotal -
    total(forwardTaxes) -
    total(forwardPreTax) -
    total(forwardPostTax);
  const annualOTHours = ytdOTHours + forwardOT * remaining;
  const paychecksInYear = n + remaining;

  return {
    source: "logged",
    basedOnPaychecks: n,
    scheduledPaychecksInYear: scheduledInYear,
    remainingPaychecksProjected: remaining,
    unloggedPastPaychecks: unlogged,
    averageOvertimeHoursPerPaycheck: avgOTPerPaycheck,
    projectOvertimeForward,
    overtimeMultiplier: otMultiplier,
    annualGrossPay,
    annualNetPay,
    annualTotalTaxes,
    annualTotalDeductions: annualPreTaxDeductions + annualPostTaxDeductions,
    annualPreTaxDeductions,
    annualPostTaxDeductions,
    overtimeHours: annualOTHours,
    perPaycheckNet: paychecksInYear > 0 ? annualNetPay / paychecksInYear : 0,
    perPaycheckGross: paychecksInYear > 0 ? annualGrossPay / paychecksInYear : 0,
    earnings,
    taxes,
    preTaxDeductions,
    postTaxDeductions,
    employerContributions: mergeLines(logs.map((e) => e.employerContributions ?? [])),
    effectiveTaxRate:
      annualGrossPay > 0 ? annualTotalTaxes / annualGrossPay : calc.effectiveTaxRate,
  };
}

/**
 * Rough Form 1040 estimate: wages minus pre-tax deductions, the standard
 * deduction and the overtime-premium deduction, through that year's brackets.
 * Ignores credits, other income and itemizing.
 */
export function projectFederalTaxReturn(
  config: SalaryConfig,
  annual: Pick<
    AnnualExtrapolation,
    "annualGrossPay" | "annualPreTaxDeductions" | "taxes" | "earnings" | "overtimeMultiplier"
  >,
  year = new Date().getFullYear(),
): FederalTaxReturnProjection {
  const annualGross = annual.annualGrossPay;
  const annualPreTaxDeductions = annual.annualPreTaxDeductions;
  const adjustedGrossIncome = annualGross - annualPreTaxDeductions;
  const standardDeduction = federalStandardDeduction(config.filingStatus, year);
  const overtimeDeductionAmount = overtimeDeduction(
    overtimePremium(annual.earnings, annual.overtimeMultiplier),
    adjustedGrossIncome,
    config.filingStatus,
    year,
  );
  const federalTaxableIncome = Math.max(
    0,
    adjustedGrossIncome - standardDeduction - overtimeDeductionAmount,
  );
  const estimatedFederalTaxOwed = calculateFederalTax(
    federalTaxableIncome,
    config.filingStatus,
    year,
  );

  const federalWithheld = annual.taxes
    .filter((t) => classifyTaxLine(t.label) === "federal")
    .reduce((s, t) => s + t.amount, 0);
  const paychecksMissingFederalLine = logsForYear(config, year).filter(
    (e) =>
      (e.totalTaxes ?? 0) > 0 &&
      !entryTaxes(e).some((t) => classifyTaxLine(t.label) === "federal"),
  ).length;

  return {
    annualGross,
    annualPreTaxDeductions,
    adjustedGrossIncome,
    standardDeduction,
    overtimeDeduction: overtimeDeductionAmount,
    federalTaxableIncome,
    estimatedFederalTaxOwed,
    federalWithheld,
    refundOrBalance: federalWithheld - estimatedFederalTaxOwed,
    paychecksMissingFederalLine,
  };
}

export function summarizeYtd(
  config: SalaryConfig,
  calc: PaycheckCalculation,
  annual: AnnualProjection,
  year: number,
  asOf = Date.now(),
  extrapolationOptions: AnnualExtrapolationOptions = {},
): YtdSummary {
  const logs = logsForYear(config, year);
  const scheduledYtd = scheduledPaychecksYtd(config, year, asOf);
  const scheduledInYear = scheduledPaychecksInYear(config, year);
  const { future, unlogged } = remainingPaychecksForYear(config, year, asOf);
  const remaining = logs.length > 0 ? future + unlogged : future;
  const annualExtrapolation = extrapolateAnnualFromLogs(
    config,
    calc,
    annual,
    year,
    { ...extrapolationOptions, asOf },
  );
  const perPaycheckNet = annualExtrapolation.perPaycheckNet;

  if (logs.length > 0) {
    const grossPay = logs.reduce((s, e) => s + e.grossPay, 0);
    const netPay = logs.reduce((s, e) => s + e.netPay, 0);
    const totalTaxes = logs.reduce((s, e) => s + (e.totalTaxes ?? 0), 0);
    const totalPreTax = logs.reduce(
      (s, e) => s + (e.totalPreTaxDeductions ?? 0),
      0,
    );
    const totalPostTax = logs.reduce(
      (s, e) => s + (e.totalPostTaxDeductions ?? 0),
      0,
    );
    const overtimeHours = logs.reduce((s, e) => s + entryOvertimeHours(e), 0);
    const expectedNetToDate = perPaycheckNet * logs.length;
    const annualNetTarget = annualExtrapolation.annualNetPay;
    return {
      year,
      source: "logged",
      paycheckCount: logs.length,
      scheduledPaychecksYtd: scheduledYtd,
      scheduledPaychecksInYear: scheduledInYear,
      grossPay,
      netPay,
      totalTaxes,
      totalDeductions: totalPreTax + totalPostTax,
      totalPreTaxDeductions: totalPreTax,
      totalPostTaxDeductions: totalPostTax,
      overtimeHours,
      earnings: mergeLines(logs.map(entryEarnings)),
      taxes: mergeLines(logs.map(entryTaxes)),
      preTaxDeductions: mergeLines(logs.map(entryPreTax)),
      postTaxDeductions: mergeLines(logs.map(entryPostTax)),
      employerContributions: mergeLines(
        logs.map((e) => e.employerContributions ?? []),
      ),
      annualExtrapolation,
      annualNetTarget,
      progressPercent:
        annualNetTarget > 0 ? (netPay / annualNetTarget) * 100 : 0,
      remainingPaychecks: remaining,
      expectedNetToDate,
      netVariance: netPay - expectedNetToDate,
      projectedAnnualTaxes: annualExtrapolation.annualTotalTaxes,
    };
  }

  const n = scheduledYtd;
  const grossPay = calc.grossPay * n;
  const netPay = calc.netPay * n;
  const totalTaxes = calc.totalTaxes * n;
  const totalPreTax = calc.totalPreTaxDeductions * n;
  const totalPostTax = calc.totalPostTaxDeductions * n;

  const earnings: YtdBreakdownLine[] = [
    { label: "Regular", amount: calc.regularPay * n },
  ];
  if (calc.overtimePay > 0) {
    earnings.push({
      label: "Overtime",
      amount: calc.overtimePay * n,
      hours: config.overtimeHours * n,
    });
  }
  const taxes: YtdBreakdownLine[] = [
    { label: "Federal income tax", amount: calc.federalTax * n },
    { label: "State income tax", amount: calc.stateTax * n },
    { label: "Social Security", amount: calc.socialSecurity * n },
    { label: "Medicare", amount: calc.medicare * n },
  ];
  if (calc.countyTax > 0) {
    taxes.splice(2, 0, {
      label: config.county.trim() ? `${config.county} tax` : "Local tax",
      amount: calc.countyTax * n,
    });
  }

  return {
    year,
    source: "estimated",
    paycheckCount: n,
    scheduledPaychecksYtd: scheduledYtd,
    scheduledPaychecksInYear: scheduledInYear,
    grossPay,
    netPay,
    totalTaxes,
    totalDeductions: totalPreTax + totalPostTax,
    totalPreTaxDeductions: totalPreTax,
    totalPostTaxDeductions: totalPostTax,
    overtimeHours: config.overtimeHours * n,
    earnings,
    taxes: taxes.filter((t) => t.amount !== 0),
    preTaxDeductions: calc.preTaxDeductionBreakdown.map((l) => ({
      label: l.name || "Pre-tax",
      amount: l.amount * n,
    })),
    postTaxDeductions: calc.postTaxDeductionBreakdown.map((l) => ({
      label: l.name || "Post-tax",
      amount: l.amount * n,
    })),
    employerContributions: [],
    annualExtrapolation,
    annualNetTarget: annualExtrapolation.annualNetPay,
    progressPercent:
      annualExtrapolation.annualNetPay > 0
        ? (netPay / annualExtrapolation.annualNetPay) * 100
        : 0,
    remainingPaychecks: remaining,
    expectedNetToDate: perPaycheckNet * n,
    netVariance: 0,
    projectedAnnualTaxes: annualExtrapolation.annualTotalTaxes,
  };
}

function lineId(): string {
  return crypto.randomUUID();
}

/** Build the itemized earnings/taxes/deduction lines from a calculation. */
export function lineItemsFromCalculation(
  calc: PaycheckCalculation,
  overtimeHours?: number,
): {
  earnings: PaycheckLineItem[];
  taxes: PaycheckLineItem[];
  preTaxDeductions: PaycheckLineItem[];
  postTaxDeductions: PaycheckLineItem[];
} {
  const earnings: PaycheckLineItem[] = [
    { id: lineId(), label: "Regular", amount: calc.regularPay },
  ];
  if (calc.overtimePay > 0) {
    earnings.push({
      id: lineId(),
      label: "Overtime",
      amount: calc.overtimePay,
      hours: overtimeHours,
    });
  }
  const taxes: PaycheckLineItem[] = [
    { id: lineId(), label: "Federal income tax", amount: calc.federalTax },
    { id: lineId(), label: "State income tax", amount: calc.stateTax },
    { id: lineId(), label: "Social Security", amount: calc.socialSecurity },
    { id: lineId(), label: "Medicare", amount: calc.medicare },
  ];
  if (calc.countyTax > 0) {
    taxes.push({ id: lineId(), label: "Local tax", amount: calc.countyTax });
  }
  return {
    earnings,
    taxes,
    preTaxDeductions: calc.preTaxDeductionBreakdown.map((l) => ({
      id: lineId(),
      label: l.name,
      amount: l.amount,
    })),
    postTaxDeductions: calc.postTaxDeductionBreakdown.map((l) => ({
      id: lineId(),
      label: l.name,
      amount: l.amount,
    })),
  };
}

export function logEntryFromCalculation(
  calc: PaycheckCalculation,
  payDate: number,
  overtimeHours?: number,
  notes?: string,
  autoGenerated?: boolean,
): PaycheckLogEntry {
  const lines = lineItemsFromCalculation(calc, overtimeHours);
  return {
    id: crypto.randomUUID(),
    payDate,
    grossPay: calc.grossPay,
    netPay: calc.netPay,
    totalTaxes: calc.totalTaxes,
    totalPreTaxDeductions: calc.totalPreTaxDeductions,
    totalPostTaxDeductions: calc.totalPostTaxDeductions,
    overtimeHours,
    notes,
    autoGenerated,
    ...lines,
  };
}

/** Build estimated log entries for scheduled paydates in [year] that are not yet logged. */
export function generatePaycheckLogsForMissingDates(
  config: SalaryConfig,
  year: number,
  asOf = Date.now(),
): PaycheckLogEntry[] {
  const missing = missingPaydaysForYear(config, year, asOf);
  return missing.map((payDate) => {
    const calc = calculatePaycheck(config, payDate);
    return logEntryFromCalculation(
      calc,
      payDate,
      config.overtimeHours,
      undefined,
      true,
    );
  });
}

/** Recompute aggregate totals on an entry from its itemized lines. */
export function recomputeEntryTotals(entry: PaycheckLogEntry): PaycheckLogEntry {
  const sum = (lines?: PaycheckLineItem[]) =>
    (lines ?? []).reduce((s, l) => s + (Number.isFinite(l.amount) ? l.amount : 0), 0);
  const grossPay = sum(entry.earnings);
  const totalTaxes = sum(entry.taxes);
  const totalPreTaxDeductions = sum(entry.preTaxDeductions);
  const totalPostTaxDeductions = sum(entry.postTaxDeductions);
  const otLine = (entry.earnings ?? []).find((l) =>
    /overtime|^ot\b/i.test(l.label),
  );
  return {
    ...entry,
    grossPay,
    totalTaxes,
    totalPreTaxDeductions,
    totalPostTaxDeductions,
    netPay: grossPay - totalTaxes - totalPreTaxDeductions - totalPostTaxDeductions,
    overtimeHours: otLine?.hours ?? entry.overtimeHours,
  };
}

export function parseSalaryRecord(plaintext: string): SalaryConfig | null {
  try {
    const raw = JSON.parse(plaintext) as Partial<SalaryConfig>;
    return normalizeSalaryConfig(raw);
  } catch {
    return null;
  }
}

/** Prevent a stale empty local copy from wiping paycheck logs on the relay. */
export function mergeSalaryConfigPreserveLogs(
  incoming: SalaryConfig,
  existing: Partial<SalaryConfig> | null | undefined,
): SalaryConfig {
  if (!existing) return normalizeSalaryConfig(incoming);

  let merged: SalaryConfig = incoming;
  const incomingLogs = incoming.paycheckLog ?? [];
  const existingLogs = existing.paycheckLog ?? [];
  if (incomingLogs.length === 0 && existingLogs.length > 0) {
    merged = { ...merged, paycheckLog: existingLogs };
  }
  if (!incoming.id && existing.id) {
    merged = { ...merged, id: existing.id };
  }
  const incomingHistory = incoming.payRateHistory ?? [];
  const existingHistory = existing.payRateHistory ?? [];
  if (incomingHistory.length === 0 && existingHistory.length > 0) {
    merged = { ...merged, payRateHistory: existingHistory };
  }
  return normalizeSalaryConfig(merged);
}

/** Merge all relay salary copies (by JSON `updatedAt`) preserving paycheck logs. */
export function resolveSalaryConfigFromAppDataRecords(
  records: Array<{ d_tag?: string | null; plaintext?: string | null }>,
): SalaryConfig | null {
  const configs = records
    .filter((r) => r.d_tag === SALARY_D_TAG && r.plaintext)
    .map((r) => parseSalaryRecord(r.plaintext!))
    .filter((c): c is SalaryConfig => c != null);
  if (configs.length === 0) return null;

  const sorted = [...configs].sort((a, b) => a.updatedAt - b.updatedAt);
  let merged = sorted[0]!;
  for (let i = 1; i < sorted.length; i++) {
    merged = mergeSalaryConfigPreserveLogs(sorted[i]!, merged);
  }
  return merged;
}

export function normalizeSalaryConfig(
  raw: Partial<SalaryConfig>,
): SalaryConfig {
  const base = defaultSalaryConfig();
  return {
    ...base,
    ...raw,
    payType: (raw.payType as PayType) ?? base.payType,
    annualSalary: raw.annualSalary ?? base.annualSalary,
    preTaxDeductions: raw.preTaxDeductions ?? base.preTaxDeductions,
    postTaxDeductions: raw.postTaxDeductions ?? base.postTaxDeductions,
    directDeposits: raw.directDeposits ?? base.directDeposits,
    taxOverrides: { ...base.taxOverrides, ...raw.taxOverrides },
    payRateHistory: raw.payRateHistory ?? base.payRateHistory,
    paycheckLog: raw.paycheckLog ?? base.paycheckLog,
    payFrequency: (raw.payFrequency as PayFrequency) ?? base.payFrequency,
    filingStatus: (raw.filingStatus as FilingStatus) ?? base.filingStatus,
  };
}

/** Stable JSON for change detection — ignores `updatedAt` so auto-save does not loop. */
export function salaryFingerprint(config: SalaryConfig): string {
  const { updatedAt: _updatedAt, ...rest } = config;
  return JSON.stringify(rest);
}

export function serializeSalary(config: SalaryConfig): string {
  return JSON.stringify({
    ...config,
    updatedAt: Date.now(),
  });
}

export function formatPercent(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`;
}

export function formatIsoDate(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

export function parseIsoDate(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return null;
  const ms = Date.parse(`${trimmed}T12:00:00`);
  return Number.isFinite(ms) ? ms : null;
}
