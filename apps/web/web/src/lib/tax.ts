/** Ported from Android `TaxConfig.kt` / `PaycheckCalculation.kt`, with per-year tables. */

export type FilingStatus =
  | "SINGLE"
  | "MARRIED_FILING_JOINTLY"
  | "MARRIED_FILING_SEPARATELY"
  | "HEAD_OF_HOUSEHOLD";

export const FILING_STATUS_LABELS: Record<FilingStatus, string> = {
  SINGLE: "Single",
  MARRIED_FILING_JOINTLY: "Married filing jointly",
  MARRIED_FILING_SEPARATELY: "Married filing separately",
  HEAD_OF_HOUSEHOLD: "Head of household",
};

export type FederalTaxBracket = {
  min: number;
  max: number;
  rate: number;
  baseTax: number;
};

const BRACKET_RATES = [0.1, 0.12, 0.22, 0.24, 0.32, 0.35, 0.37] as const;

type TaxYearTable = {
  /** Upper bound of each bracket except the last (37%), by filing status. */
  bracketTops: Record<FilingStatus, readonly number[]>;
  standardDeduction: Record<FilingStatus, number>;
  socialSecurityWageBase: number;
};

/**
 * 2025: IRS Rev. Proc. 2024-40 brackets; standard deduction as raised by the
 * One Big Beautiful Bill Act (P.L. 119-21). 2026: IRS Rev. Proc. 2025-32.
 * Social Security wage bases from SSA.
 */
const TAX_YEARS: Record<number, TaxYearTable> = {
  2025: {
    bracketTops: {
      SINGLE: [11_925, 48_475, 103_350, 197_300, 250_525, 626_350],
      MARRIED_FILING_JOINTLY: [23_850, 96_950, 206_700, 394_600, 501_050, 751_600],
      MARRIED_FILING_SEPARATELY: [11_925, 48_475, 103_350, 197_300, 250_525, 375_800],
      HEAD_OF_HOUSEHOLD: [17_000, 64_850, 103_350, 197_300, 250_500, 626_350],
    },
    standardDeduction: {
      SINGLE: 15_750,
      MARRIED_FILING_JOINTLY: 31_500,
      MARRIED_FILING_SEPARATELY: 15_750,
      HEAD_OF_HOUSEHOLD: 23_625,
    },
    socialSecurityWageBase: 176_100,
  },
  2026: {
    bracketTops: {
      SINGLE: [12_400, 50_400, 105_700, 201_775, 256_225, 640_600],
      MARRIED_FILING_JOINTLY: [24_800, 100_800, 211_400, 403_550, 512_450, 768_700],
      MARRIED_FILING_SEPARATELY: [12_400, 50_400, 105_700, 201_775, 256_225, 384_350],
      HEAD_OF_HOUSEHOLD: [17_700, 67_450, 105_700, 201_750, 256_200, 640_600],
    },
    standardDeduction: {
      SINGLE: 16_100,
      MARRIED_FILING_JOINTLY: 32_200,
      MARRIED_FILING_SEPARATELY: 16_100,
      HEAD_OF_HOUSEHOLD: 24_150,
    },
    socialSecurityWageBase: 184_500,
  },
};

const TABLE_YEARS = Object.keys(TAX_YEARS)
  .map(Number)
  .sort((a, b) => a - b);

/** Tax year whose tables are used for `year` (clamped to the years on file). */
export function taxTableYear(year: number): number {
  const first = TABLE_YEARS[0]!;
  const last = TABLE_YEARS[TABLE_YEARS.length - 1]!;
  return Math.min(last, Math.max(first, Math.trunc(year)));
}

function tableFor(year: number): TaxYearTable {
  return TAX_YEARS[taxTableYear(year)]!;
}

export function yearOf(ms: number): number {
  return new Date(ms).getFullYear();
}

export function federalBracketsFor(
  status: FilingStatus,
  year = new Date().getFullYear(),
): FederalTaxBracket[] {
  const tops = tableFor(year).bracketTops[status];
  const brackets: FederalTaxBracket[] = [];
  let min = 0;
  let baseTax = 0;
  BRACKET_RATES.forEach((rate, i) => {
    const max = tops[i] ?? Number.POSITIVE_INFINITY;
    brackets.push({ min, max, rate, baseTax });
    if (Number.isFinite(max)) baseTax += (max - min) * rate;
    min = max;
  });
  return brackets;
}

export function federalStandardDeduction(
  status: FilingStatus,
  year = new Date().getFullYear(),
): number {
  return tableFor(year).standardDeduction[status];
}

export function socialSecurityWageBase(year = new Date().getFullYear()): number {
  return tableFor(year).socialSecurityWageBase;
}

export function calculateFederalTax(
  taxableIncome: number,
  status: FilingStatus,
  year = new Date().getFullYear(),
): number {
  const brackets = federalBracketsFor(status, year);
  for (let i = brackets.length - 1; i >= 0; i--) {
    const bracket = brackets[i]!;
    if (taxableIncome > bracket.min) {
      return bracket.baseTax + (taxableIncome - bracket.min) * bracket.rate;
    }
  }
  return 0;
}

export function federalMarginalRate(
  federalTaxableIncome: number,
  status: FilingStatus,
  year = new Date().getFullYear(),
): number {
  const brackets = federalBracketsFor(status, year);
  for (let i = brackets.length - 1; i >= 0; i--) {
    if (federalTaxableIncome > brackets[i]!.min) return brackets[i]!.rate;
  }
  return brackets[0]!.rate;
}

/**
 * "No tax on overtime" deduction (One Big Beautiful Bill Act, tax years
 * 2025–2028): the premium part of FLSA overtime, capped and phased out by MAGI.
 * Not available to married-filing-separately filers.
 */
export function overtimeDeduction(
  overtimePremium: number,
  modifiedAgi: number,
  status: FilingStatus,
  year: number,
): number {
  if (year < 2025 || year > 2028) return 0;
  if (status === "MARRIED_FILING_SEPARATELY") return 0;
  const joint = status === "MARRIED_FILING_JOINTLY";
  const cap = joint ? 25_000 : 12_500;
  const phaseoutStart = joint ? 300_000 : 150_000;
  const reduction =
    Math.ceil(Math.max(0, modifiedAgi - phaseoutStart) / 1_000) * 100;
  return Math.max(0, Math.min(Math.max(0, overtimePremium), cap) - reduction);
}

export const FICA = {
  SOCIAL_SECURITY_RATE: 0.062,
  MEDICARE_RATE: 0.0145,
  ADDITIONAL_MEDICARE_RATE: 0.009,
  /** Employers withhold Additional Medicare above $200k regardless of filing status. */
  ADDITIONAL_MEDICARE_WITHHOLDING_THRESHOLD: 200_000,
} as const;

/**
 * Rough per-state rate applied to taxable wages. Flat-tax states use their
 * actual 2026 rate; graduated states use a typical middle-income bracket, so
 * set a custom rate from a paystub for accuracy.
 */
export function estimateStateTaxRate(state: string): number {
  const rates: Record<string, number> = {
    AL: 0.05, AK: 0, AZ: 0.025, AR: 0.039, CA: 0.093, CO: 0.044, CT: 0.055,
    DE: 0.066, FL: 0, GA: 0.0519, HI: 0.0825, ID: 0.053, IL: 0.0495, IN: 0.0295,
    IA: 0.038, KS: 0.0558, KY: 0.035, LA: 0.03, ME: 0.0675, MD: 0.0475, MA: 0.05,
    MI: 0.0425, MN: 0.068, MS: 0.04, MO: 0.047, MT: 0.059, NE: 0.0455, NV: 0,
    NH: 0, NJ: 0.05525, NM: 0.049, NY: 0.055, NC: 0.0399, ND: 0.0195, OH: 0.0275,
    OK: 0.0475, OR: 0.0875, PA: 0.0307, RI: 0.0475, SC: 0.062, SD: 0, TN: 0,
    TX: 0, UT: 0.045, VT: 0.066, VA: 0.0575, WA: 0, WV: 0.0482, WI: 0.053,
    WY: 0, DC: 0.085,
  };
  return rates[state.toUpperCase()] ?? 0.05;
}
