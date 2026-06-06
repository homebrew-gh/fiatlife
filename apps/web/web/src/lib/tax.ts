/** Ported from Android `TaxConfig.kt` / `PaycheckCalculation.kt`. */

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

const SINGLE_BRACKETS: FederalTaxBracket[] = [
  { min: 0, max: 11_925, rate: 0.1, baseTax: 0 },
  { min: 11_925, max: 48_475, rate: 0.12, baseTax: 1_192.5 },
  { min: 48_475, max: 103_350, rate: 0.22, baseTax: 5_578.5 },
  { min: 103_350, max: 197_300, rate: 0.24, baseTax: 17_651 },
  { min: 197_300, max: 250_525, rate: 0.32, baseTax: 40_199 },
  { min: 250_525, max: 626_350, rate: 0.35, baseTax: 57_231 },
  { min: 626_350, max: Number.POSITIVE_INFINITY, rate: 0.37, baseTax: 188_769.75 },
];

const MFJ_BRACKETS: FederalTaxBracket[] = [
  { min: 0, max: 23_850, rate: 0.1, baseTax: 0 },
  { min: 23_850, max: 96_950, rate: 0.12, baseTax: 2_385 },
  { min: 96_950, max: 206_700, rate: 0.22, baseTax: 11_157 },
  { min: 206_700, max: 394_600, rate: 0.24, baseTax: 35_302 },
  { min: 394_600, max: 501_050, rate: 0.32, baseTax: 80_398 },
  { min: 501_050, max: 751_600, rate: 0.35, baseTax: 114_462 },
  { min: 751_600, max: Number.POSITIVE_INFINITY, rate: 0.37, baseTax: 202_154.5 },
];

const MFS_BRACKETS: FederalTaxBracket[] = [
  { min: 0, max: 11_925, rate: 0.1, baseTax: 0 },
  { min: 11_925, max: 48_475, rate: 0.12, baseTax: 1_192.5 },
  { min: 48_475, max: 103_350, rate: 0.22, baseTax: 5_578.5 },
  { min: 103_350, max: 197_300, rate: 0.24, baseTax: 17_651 },
  { min: 197_300, max: 250_525, rate: 0.32, baseTax: 40_199 },
  { min: 250_525, max: 375_800, rate: 0.35, baseTax: 57_231 },
  { min: 375_800, max: Number.POSITIVE_INFINITY, rate: 0.37, baseTax: 101_077.25 },
];

const HOH_BRACKETS: FederalTaxBracket[] = [
  { min: 0, max: 17_000, rate: 0.1, baseTax: 0 },
  { min: 17_000, max: 64_850, rate: 0.12, baseTax: 1_700 },
  { min: 64_850, max: 103_350, rate: 0.22, baseTax: 7_442 },
  { min: 103_350, max: 197_300, rate: 0.24, baseTax: 15_912 },
  { min: 197_300, max: 250_500, rate: 0.32, baseTax: 38_460 },
  { min: 250_500, max: 626_350, rate: 0.35, baseTax: 55_484 },
  { min: 626_350, max: Number.POSITIVE_INFINITY, rate: 0.37, baseTax: 187_031.5 },
];

export function federalBracketsFor(status: FilingStatus): FederalTaxBracket[] {
  switch (status) {
    case "MARRIED_FILING_JOINTLY":
      return MFJ_BRACKETS;
    case "MARRIED_FILING_SEPARATELY":
      return MFS_BRACKETS;
    case "HEAD_OF_HOUSEHOLD":
      return HOH_BRACKETS;
    default:
      return SINGLE_BRACKETS;
  }
}

export function federalStandardDeduction(status: FilingStatus): number {
  switch (status) {
    case "MARRIED_FILING_JOINTLY":
      return 30_000;
    case "HEAD_OF_HOUSEHOLD":
      return 22_500;
    default:
      return 15_000;
  }
}

export function calculateFederalTax(
  taxableIncome: number,
  status: FilingStatus,
): number {
  const brackets = federalBracketsFor(status);
  for (let i = brackets.length - 1; i >= 0; i--) {
    const bracket = brackets[i];
    if (taxableIncome > bracket.min) {
      return bracket.baseTax + (taxableIncome - bracket.min) * bracket.rate;
    }
  }
  return 0;
}

export function federalMarginalRate(
  federalTaxableIncome: number,
  status: FilingStatus,
): number {
  const brackets = federalBracketsFor(status);
  for (let i = brackets.length - 1; i >= 0; i--) {
    if (federalTaxableIncome > brackets[i].min) return brackets[i].rate;
  }
  return brackets[0].rate;
}

export const FICA = {
  SOCIAL_SECURITY_RATE: 0.062,
  SOCIAL_SECURITY_WAGE_BASE: 176_100,
  MEDICARE_RATE: 0.0145,
  ADDITIONAL_MEDICARE_RATE: 0.009,
  ADDITIONAL_MEDICARE_THRESHOLD_SINGLE: 200_000,
  ADDITIONAL_MEDICARE_THRESHOLD_JOINT: 250_000,
} as const;

export function estimateStateTaxRate(state: string): number {
  const rates: Record<string, number> = {
    AL: 0.05, AK: 0, AZ: 0.025, AR: 0.044, CA: 0.093, CO: 0.044, CT: 0.05,
    DE: 0.066, FL: 0, GA: 0.055, HI: 0.075, ID: 0.058, IL: 0.0495, IN: 0.0315,
    IA: 0.06, KS: 0.057, KY: 0.04, LA: 0.0425, ME: 0.0715, MD: 0.0575, MA: 0.05,
    MI: 0.0425, MN: 0.0985, MS: 0.05, MO: 0.048, MT: 0.0575, NE: 0.0564, NV: 0,
    NH: 0, NJ: 0.0897, NM: 0.059, NY: 0.0685, NC: 0.045, ND: 0.0195, OH: 0.04,
    OK: 0.0475, OR: 0.099, PA: 0.0307, RI: 0.0599, SC: 0.065, SD: 0, TN: 0,
    TX: 0, UT: 0.0465, VT: 0.0875, VA: 0.0575, WA: 0, WV: 0.055, WI: 0.0765,
    WY: 0, DC: 0.0895,
  };
  return rates[state.toUpperCase()] ?? 0.05;
}
