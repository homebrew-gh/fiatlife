import {
  calculateFederalTax,
  estimateStateTaxRate,
  federalMarginalRate,
  federalStandardDeduction,
  FICA,
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

export type Deduction = {
  id: string;
  name: string;
  amount: number;
  type?: "PRE_TAX" | "POST_TAX";
  category?: string;
  isPercentage: boolean;
  isEnabled: boolean;
};

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
};

export type SalaryConfig = {
  id: string;
  name: string;
  hourlyRate: number;
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
  paycheckLog?: PaycheckLogEntry[];
  updatedAt: number;
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

function calculateDeposits(
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
  annualPostTaxDeductions: number;
  annualNetPay: number;
  effectiveTaxRate: number;
  marginalFederalRate: number;
  overtimeHoursUsed: number;
  perPaycheckNet: number;
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
  annualNetTarget: number;
  progressPercent: number;
  remainingPaychecks: number;
};

export function defaultSalaryConfig(): SalaryConfig {
  return {
    id: "",
    name: "My Salary",
    hourlyRate: 0,
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
    paycheckLog: [],
    updatedAt: 0,
  };
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

export function calculatePaycheck(config: SalaryConfig): PaycheckCalculation {
  const regularPay = config.hourlyRate * config.standardHoursPerPeriod;
  const overtimePay =
    config.hourlyRate * config.overtimeMultiplier * config.overtimeHours;
  const grossPay = regularPay + overtimePay;
  const periodsPerYear = PERIODS_PER_YEAR[config.payFrequency];
  const overrides = config.taxOverrides ?? {};

  const pre = calcDeductionLines(config.preTaxDeductions, grossPay);
  const taxableGross = grossPay - pre.total;
  const annualTaxable = taxableGross * periodsPerYear;
  const standardDeduction = federalStandardDeduction(config.filingStatus);
  const federalTaxableAnnual = Math.max(0, annualTaxable - standardDeduction);

  const marginal =
    overrides.customFederalTaxRate ??
    federalMarginalRate(federalTaxableAnnual, config.filingStatus);

  let federalTax = 0;
  if (!overrides.isExemptFromFederal) {
    const annualFederal = overrides.customFederalTaxRate
      ? annualTaxable * overrides.customFederalTaxRate
      : calculateFederalTax(federalTaxableAnnual, config.filingStatus);
    federalTax =
      annualFederal / periodsPerYear +
      (overrides.federalAdditionalWithholding ?? 0);
  }

  const stateTaxRate = overrides.isExemptFromState
    ? 0
    : (overrides.customStateTaxRate ??
      estimateStateTaxRate(config.state));
  const stateTax = overrides.isExemptFromState
    ? 0
    : (annualTaxable * stateTaxRate) / periodsPerYear +
      (overrides.stateAdditionalWithholding ?? 0);

  const countyTaxRate = overrides.isExemptFromLocal
    ? 0
    : (overrides.customCountyTaxRate ?? 0);
  const countyTax = overrides.isExemptFromLocal
    ? 0
    : (annualTaxable * countyTaxRate) / periodsPerYear;

  const annualGross = grossPay * periodsPerYear;
  const ssRate =
    overrides.customSocialSecurityRate ?? FICA.SOCIAL_SECURITY_RATE;
  const socialSecurity =
    (Math.min(annualGross, FICA.SOCIAL_SECURITY_WAGE_BASE) * ssRate) /
    periodsPerYear;

  const medRate = overrides.customMedicareRate ?? FICA.MEDICARE_RATE;
  const medicareThreshold =
    config.filingStatus === "MARRIED_FILING_JOINTLY"
      ? FICA.ADDITIONAL_MEDICARE_THRESHOLD_JOINT
      : FICA.ADDITIONAL_MEDICARE_THRESHOLD_SINGLE;
  const baseMedicare = annualGross * medRate;
  const additionalMedicare =
    overrides.customMedicareRate == null && annualGross > medicareThreshold
      ? (annualGross - medicareThreshold) * FICA.ADDITIONAL_MEDICARE_RATE
      : 0;
  const medicare = (baseMedicare + additionalMedicare) / periodsPerYear;

  const totalTaxes =
    federalTax + stateTax + countyTax + socialSecurity + medicare;

  const post = calcDeductionLines(config.postTaxDeductions, grossPay);
  const netPay = grossPay - pre.total - totalTaxes - post.total;
  const depositAllocations = calculateDeposits(config.directDeposits, netPay);

  return {
    grossPay,
    regularPay,
    overtimePay,
    totalPreTaxDeductions: pre.total,
    preTaxDeductionBreakdown: pre.lines,
    federalTax,
    federalMarginalRate: marginal,
    stateTax,
    stateTaxRate,
    countyTax,
    countyTaxRate,
    socialSecurity,
    socialSecurityRate: ssRate,
    medicare,
    medicareRate: medRate,
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

export function calculateAnnual(
  config: SalaryConfig,
  annualOvertimeHours: number,
): AnnualProjection {
  const periodsPerYear = PERIODS_PER_YEAR[config.payFrequency];
  const annualRegularPay =
    config.hourlyRate * config.standardHoursPerPeriod * periodsPerYear;
  const annualOvertimePay =
    config.hourlyRate * config.overtimeMultiplier * annualOvertimeHours;
  const annualGross = annualRegularPay + annualOvertimePay;
  const perPeriodGross = annualGross / periodsPerYear;
  const overrides = config.taxOverrides ?? {};

  const preEnabled = config.preTaxDeductions.filter((d) => d.isEnabled);
  const preTaxBreakdown = preEnabled.map((d) => {
    const perPeriod = d.isPercentage
      ? perPeriodGross * (d.amount / 100)
      : d.amount;
    return { name: d.name, amount: perPeriod * periodsPerYear };
  });
  const annualPreTax = preTaxBreakdown.reduce((s, l) => s + l.amount, 0);

  const annualTaxable = annualGross - annualPreTax;
  const federalTaxableAnnual = Math.max(
    0,
    annualTaxable - federalStandardDeduction(config.filingStatus),
  );

  let annualFederalTax = 0;
  if (!overrides.isExemptFromFederal) {
    const base = overrides.customFederalTaxRate
      ? annualTaxable * overrides.customFederalTaxRate
      : calculateFederalTax(federalTaxableAnnual, config.filingStatus);
    annualFederalTax =
      base + (overrides.federalAdditionalWithholding ?? 0) * periodsPerYear;
  }

  let annualStateTax = 0;
  if (!overrides.isExemptFromState) {
    const rate =
      overrides.customStateTaxRate ?? estimateStateTaxRate(config.state);
    annualStateTax =
      annualTaxable * rate +
      (overrides.stateAdditionalWithholding ?? 0) * periodsPerYear;
  }

  const annualCountyTax = overrides.isExemptFromLocal
    ? 0
    : annualTaxable * (overrides.customCountyTaxRate ?? 0);

  const ssRate =
    overrides.customSocialSecurityRate ?? FICA.SOCIAL_SECURITY_RATE;
  const annualSS =
    Math.min(annualGross, FICA.SOCIAL_SECURITY_WAGE_BASE) * ssRate;

  const medRate = overrides.customMedicareRate ?? FICA.MEDICARE_RATE;
  const medicareThreshold =
    config.filingStatus === "MARRIED_FILING_JOINTLY"
      ? FICA.ADDITIONAL_MEDICARE_THRESHOLD_JOINT
      : FICA.ADDITIONAL_MEDICARE_THRESHOLD_SINGLE;
  const annualMedicare =
    annualGross * medRate +
    (overrides.customMedicareRate == null && annualGross > medicareThreshold
      ? (annualGross - medicareThreshold) * FICA.ADDITIONAL_MEDICARE_RATE
      : 0);

  const annualTotalTaxes =
    annualFederalTax + annualStateTax + annualCountyTax + annualSS + annualMedicare;

  const postEnabled = config.postTaxDeductions.filter((d) => d.isEnabled);
  const annualPostTax = postEnabled.reduce((sum, d) => {
    const perPeriod = d.isPercentage
      ? perPeriodGross * (d.amount / 100)
      : d.amount;
    return sum + perPeriod * periodsPerYear;
  }, 0);

  const annualNet =
    annualGross - annualPreTax - annualTotalTaxes - annualPostTax;

  return {
    annualRegularPay,
    annualOvertimePay,
    annualGrossPay: annualGross,
    annualPreTaxDeductions: annualPreTax,
    annualTotalTaxes,
    annualPostTaxDeductions: annualPostTax,
    annualNetPay: annualNet,
    effectiveTaxRate: annualGross > 0 ? annualTotalTaxes / annualGross : 0,
    marginalFederalRate: federalMarginalRate(
      federalTaxableAnnual,
      config.filingStatus,
    ),
    overtimeHoursUsed: annualOvertimeHours,
    perPaycheckNet: periodsPerYear > 0 ? annualNet / periodsPerYear : 0,
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

  const stepMs =
    frequency === "WEEKLY"
      ? 7 * 24 * 60 * 60 * 1000
      : 14 * 24 * 60 * 60 * 1000;

  let count = 0;
  let payday = startOfDay(firstPaydayMillis);
  const maxIterations = 500;
  for (let i = 0; i < maxIterations && payday <= rangeEnd; i++) {
    if (payday >= rangeStart && payday <= rangeEnd) count++;
    payday += stepMs;
  }
  return count;
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
  if (!anchor) return PERIODS_PER_YEAR[config.payFrequency];
  return countPaychecksInRange(
    anchor,
    config.payFrequency,
    yearStart(year),
    yearEnd(year),
  );
}

export function logsForYear(
  config: SalaryConfig,
  year: number,
): PaycheckLogEntry[] {
  return (config.paycheckLog ?? [])
    .filter((e) => new Date(e.payDate).getFullYear() === year)
    .sort((a, b) => b.payDate - a.payDate);
}

export function summarizeYtd(
  config: SalaryConfig,
  calc: PaycheckCalculation,
  annual: AnnualProjection,
  year: number,
  asOf = Date.now(),
): YtdSummary {
  const logs = logsForYear(config, year);
  const scheduledYtd = scheduledPaychecksYtd(config, year, asOf);
  const scheduledInYear = scheduledPaychecksInYear(config, year);
  const remaining = Math.max(0, scheduledInYear - scheduledYtd);

  if (logs.length > 0) {
    const grossPay = logs.reduce((s, e) => s + e.grossPay, 0);
    const netPay = logs.reduce((s, e) => s + e.netPay, 0);
    const totalTaxes = logs.reduce((s, e) => s + (e.totalTaxes ?? 0), 0);
    const totalDeductions = logs.reduce(
      (s, e) =>
        s + (e.totalPreTaxDeductions ?? 0) + (e.totalPostTaxDeductions ?? 0),
      0,
    );
    return {
      year,
      source: "logged",
      paycheckCount: logs.length,
      scheduledPaychecksYtd: scheduledYtd,
      scheduledPaychecksInYear: scheduledInYear,
      grossPay,
      netPay,
      totalTaxes,
      totalDeductions,
      annualNetTarget: annual.annualNetPay,
      progressPercent:
        annual.annualNetPay > 0 ? (netPay / annual.annualNetPay) * 100 : 0,
      remainingPaychecks: remaining,
    };
  }

  const grossPay = calc.grossPay * scheduledYtd;
  const netPay = calc.netPay * scheduledYtd;
  const totalTaxes = calc.totalTaxes * scheduledYtd;
  const totalDeductions =
    (calc.totalPreTaxDeductions + calc.totalPostTaxDeductions) * scheduledYtd;

  return {
    year,
    source: "estimated",
    paycheckCount: scheduledYtd,
    scheduledPaychecksYtd: scheduledYtd,
    scheduledPaychecksInYear: scheduledInYear,
    grossPay,
    netPay,
    totalTaxes,
    totalDeductions,
    annualNetTarget: annual.annualNetPay,
    progressPercent:
      annual.annualNetPay > 0 ? (netPay / annual.annualNetPay) * 100 : 0,
    remainingPaychecks: remaining,
  };
}

export function logEntryFromCalculation(
  calc: PaycheckCalculation,
  payDate: number,
  overtimeHours?: number,
  notes?: string,
): PaycheckLogEntry {
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

export function normalizeSalaryConfig(
  raw: Partial<SalaryConfig>,
): SalaryConfig {
  const base = defaultSalaryConfig();
  return {
    ...base,
    ...raw,
    preTaxDeductions: raw.preTaxDeductions ?? base.preTaxDeductions,
    postTaxDeductions: raw.postTaxDeductions ?? base.postTaxDeductions,
    directDeposits: raw.directDeposits ?? base.directDeposits,
    taxOverrides: { ...base.taxOverrides, ...raw.taxOverrides },
    paycheckLog: raw.paycheckLog ?? base.paycheckLog,
    payFrequency: (raw.payFrequency as PayFrequency) ?? base.payFrequency,
    filingStatus: (raw.filingStatus as FilingStatus) ?? base.filingStatus,
  };
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
