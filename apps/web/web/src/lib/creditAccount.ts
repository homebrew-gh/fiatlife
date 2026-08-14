import type { BillFrequency } from "./bill";

export const CREDIT_D_TAG_PREFIX = "fiatlife/credit/";

export const ALL_CREDIT_ACCOUNT_TYPES = [
  "CREDIT_CARD",
  "MORTGAGE",
  "CAR_LOAN",
  "STUDENT_LOAN",
  "PERSONAL_LOAN",
  "HELOC",
  "RETIREMENT_LOAN",
  "OTHER",
] as const;

export type CreditAccountType = (typeof ALL_CREDIT_ACCOUNT_TYPES)[number];

export const CREDIT_ACCOUNT_TYPE_LABELS: Record<CreditAccountType, string> = {
  CREDIT_CARD: "Credit Card",
  MORTGAGE: "Mortgage",
  CAR_LOAN: "Car Loan",
  STUDENT_LOAN: "Student Loan",
  PERSONAL_LOAN: "Personal Loan",
  HELOC: "HELOC",
  RETIREMENT_LOAN: "401k/IRA Loan",
  OTHER: "Other",
};

export const ALL_MIN_PAYMENT_TYPES = [
  "FIXED",
  "PERCENT_OF_BALANCE",
  "FULL_BALANCE",
] as const;

export type CreditCardMinPaymentType = (typeof ALL_MIN_PAYMENT_TYPES)[number];

export const ALL_PROMOTION_APPLIES_TO = [
  "PURCHASES",
  "BALANCE_TRANSFER",
  "BOTH",
] as const;

export type PromotionAppliesTo =
  (typeof ALL_PROMOTION_APPLIES_TO)[number];

export const MIN_PAYMENT_TYPE_LABELS: Record<CreditCardMinPaymentType, string> = {
  FIXED: "Fixed amount",
  PERCENT_OF_BALANCE: "% of balance",
  FULL_BALANCE: "Pay in full",
};

export type StatementEntry = {
  hash: string;
  addedAt: number;
  label: string;
};

export type CreditAccount = {
  id: string;
  name: string;
  type: CreditAccountType;
  institution: string;
  accountNumberLast4: string;
  apr: number;
  /** Ongoing APR after a promotion. Falls back to `apr` for older records. */
  standardApr?: number | null;
  promotionalApr?: number | null;
  promotionalAprEndDate?: number | null;
  promotionAppliesTo?: PromotionAppliesTo | null;
  deferredInterest?: boolean;
  currentBalance: number;
  /** Issuer-reported statement balance date. */
  statementBalanceAsOfMillis?: number | null;
  /** Issuer-reported amount due; null uses the configured minimum formula. */
  statementAmountDue?: number | null;
  dueDay: number;
  linkedBillId?: string | null;
  annualFeeLinkedBillId?: string | null;
  notes: string;
  createdAt: number;
  updatedAt: number;
  statementEntries: StatementEntry[];
  attachmentHashes: string[];
  creditLimit: number;
  minimumPaymentType: CreditCardMinPaymentType;
  minimumPaymentValue: number;
  originalPrincipal: number;
  termMonths?: number | null;
  monthlyPaymentAmount?: number | null;
  startDate?: number | null;
  endDate?: number | null;
  annualFeeAmount: number;
  annualFeeRenewalDateMillis?: number | null;
  annualFeeFrequency: BillFrequency;
};

export function creditAccountDTag(id: string): string {
  return `${CREDIT_D_TAG_PREFIX}${id}`;
}

export function newCreditAccountId(): string {
  return crypto.randomUUID();
}

export function isRevolvingType(type: CreditAccountType): boolean {
  return type === "CREDIT_CARD" || type === "HELOC";
}

export function isAmortizingType(type: CreditAccountType): boolean {
  return (
    type === "MORTGAGE" ||
    type === "CAR_LOAN" ||
    type === "STUDENT_LOAN" ||
    type === "PERSONAL_LOAN" ||
    type === "RETIREMENT_LOAN"
  );
}

export function mapLegacyCreditAccountType(raw: unknown): CreditAccountType {
  const value = String(raw ?? "")
    .trim()
    .toUpperCase();
  if ((ALL_CREDIT_ACCOUNT_TYPES as readonly string[]).includes(value)) {
    return value as CreditAccountType;
  }
  return "OTHER";
}

export function mapLegacyMinPaymentType(raw: unknown): CreditCardMinPaymentType {
  const value = String(raw ?? "")
    .trim()
    .toUpperCase();
  if ((ALL_MIN_PAYMENT_TYPES as readonly string[]).includes(value)) {
    return value as CreditCardMinPaymentType;
  }
  return "PERCENT_OF_BALANCE";
}

export function minimumDue(account: CreditAccount): number {
  switch (account.minimumPaymentType) {
    case "FIXED":
      return Math.max(0, account.minimumPaymentValue);
    case "PERCENT_OF_BALANCE":
      return Math.max(
        0,
        account.currentBalance * (account.minimumPaymentValue / 100),
      );
    case "FULL_BALANCE":
      return Math.max(0, account.currentBalance);
    default:
      return 0;
  }
}

function dueDateInMonth(dueDay: number, year: number, month: number): Date {
  const lastDay = new Date(year, month + 1, 0).getDate();
  const date = new Date(year, month, Math.min(Math.max(1, dueDay), lastDay));
  date.setHours(0, 0, 0, 0);
  return date;
}

function endOfDayMillis(date: Date): number {
  return date.getTime() + 86_400_000 - 1;
}

/** Statement amount due applies until that cycle's due date passes. */
export function statementOverrideActive(
  account: CreditAccount,
  asOf = Date.now(),
): boolean {
  if (
    account.statementAmountDue == null ||
    !Number.isFinite(account.statementAmountDue)
  ) {
    return false;
  }
  if (account.statementBalanceAsOfMillis == null) return true;
  const stmt = new Date(account.statementBalanceAsOfMillis);
  let cycleDue = dueDateInMonth(
    account.dueDay,
    stmt.getFullYear(),
    stmt.getMonth(),
  );
  if (account.statementBalanceAsOfMillis > endOfDayMillis(cycleDue)) {
    const next = new Date(stmt.getFullYear(), stmt.getMonth() + 1, 1);
    cycleDue = dueDateInMonth(
      account.dueDay,
      next.getFullYear(),
      next.getMonth(),
    );
  }
  return asOf <= endOfDayMillis(cycleDue);
}

export function effectiveAmountDue(
  account: CreditAccount,
  asOf = Date.now(),
): number {
  if (statementOverrideActive(account, asOf)) {
    return Math.max(0, account.statementAmountDue ?? 0);
  }
  return minimumDue(account);
}

export type DueUrgency = {
  days: number;
  overdue: boolean;
};

export function dueUrgency(
  account: CreditAccount,
  paidThisCycle = false,
  asOf = Date.now(),
): DueUrgency {
  const now = new Date(asOf);
  now.setHours(0, 0, 0, 0);
  const thisMonthDue = dueDateInMonth(
    account.dueDay,
    now.getFullYear(),
    now.getMonth(),
  );
  if (
    account.currentBalance > 0 &&
    asOf > endOfDayMillis(thisMonthDue) &&
    !paidThisCycle
  ) {
    return {
      days: Math.max(
        1,
        Math.ceil((asOf - thisMonthDue.getTime()) / 86_400_000),
      ),
      overdue: true,
    };
  }
  let next = thisMonthDue;
  if (asOf > endOfDayMillis(thisMonthDue)) {
    const following = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    next = dueDateInMonth(
      account.dueDay,
      following.getFullYear(),
      following.getMonth(),
    );
  }
  return {
    days: Math.max(
      0,
      Math.ceil((next.getTime() - now.getTime()) / 86_400_000),
    ),
    overdue: false,
  };
}

export function sortAccountsByUrgency(
  accounts: CreditAccount[],
  paidThisCycleById: Record<string, boolean> = {},
): CreditAccount[] {
  return [...accounts].sort((a, b) => {
    if (a.currentBalance <= 0 && b.currentBalance > 0) return 1;
    if (b.currentBalance <= 0 && a.currentBalance > 0) return -1;
    const ua = dueUrgency(a, paidThisCycleById[a.id] === true);
    const ub = dueUrgency(b, paidThisCycleById[b.id] === true);
    if (ua.overdue !== ub.overdue) return ua.overdue ? -1 : 1;
    if (ua.overdue && ub.overdue) return ub.days - ua.days;
    return ua.days - ub.days;
  });
}

export function isPromotionActive(
  account: CreditAccount,
  asOf = Date.now(),
): boolean {
  return (
    account.promotionalApr != null &&
    account.promotionalAprEndDate != null &&
    account.promotionalAprEndDate >= asOf
  );
}

export function effectiveApr(
  account: CreditAccount,
  asOf = Date.now(),
): number {
  if (isPromotionActive(account, asOf)) {
    return Math.max(0, account.promotionalApr ?? 0);
  }
  return Math.max(0, account.standardApr ?? account.apr);
}

export function monthsUntilPromotionEnds(
  account: CreditAccount,
  asOf = Date.now(),
): number | null {
  if (!isPromotionActive(account, asOf) || account.promotionalAprEndDate == null) {
    return null;
  }
  const start = new Date(asOf);
  const end = new Date(account.promotionalAprEndDate);
  return Math.max(
    1,
    (end.getFullYear() - start.getFullYear()) * 12 +
      end.getMonth() -
      start.getMonth() +
      (end.getDate() >= start.getDate() ? 1 : 0),
  );
}

export function paymentToClearPromotion(
  account: CreditAccount,
  asOf = Date.now(),
): number | null {
  const months = monthsUntilPromotionEnds(account, asOf);
  if (months == null || months <= 0 || account.currentBalance <= 0) return null;
  return account.currentBalance / months;
}

export function effectiveMonthlyPayment(account: CreditAccount): number {
  if (isRevolvingType(account.type)) {
    return account.currentBalance > 0 ? effectiveAmountDue(account) : 0;
  }
  if (isAmortizingType(account.type)) {
    return account.monthlyPaymentAmount ?? 0;
  }
  return 0;
}

export function utilizationPercent(account: CreditAccount): number | null {
  if (!isRevolvingType(account.type) || account.creditLimit <= 0) return null;
  return Math.min(
    100,
    (account.currentBalance / account.creditLimit) * 100,
  );
}

export type DebtSummary = {
  totalDebt: number;
  totalMonthlyPayment: number;
  totalCreditAvailable: number;
  totalCreditUtilized: number;
  utilizationPercent: number;
  accountCount: number;
};

export function summarizeDebt(accounts: CreditAccount[]): DebtSummary {
  const revolving = accounts.filter((a) => isRevolvingType(a.type));
  const totalCreditAvailable = revolving.reduce(
    (sum, a) => sum + Math.max(0, a.creditLimit),
    0,
  );
  const totalCreditUtilized = revolving.reduce(
    (sum, a) => sum + a.currentBalance,
    0,
  );
  const utilization =
    totalCreditAvailable > 0 ? totalCreditUtilized / totalCreditAvailable : 0;

  return {
    totalDebt: accounts.reduce((sum, a) => sum + a.currentBalance, 0),
    totalMonthlyPayment: accounts.reduce(
      (sum, a) => sum + effectiveMonthlyPayment(a),
      0,
    ),
    totalCreditAvailable,
    totalCreditUtilized,
    utilizationPercent: utilization * 100,
    accountCount: accounts.length,
  };
}

export function sortCreditAccounts(accounts: CreditAccount[]): CreditAccount[] {
  return [...accounts].sort((a, b) => {
    const aRevolving = isRevolvingType(a.type) ? 0 : 1;
    const bRevolving = isRevolvingType(b.type) ? 0 : 1;
    if (aRevolving !== bRevolving) return aRevolving - bRevolving;
    return a.name.localeCompare(b.name, undefined, { sensitivity: "base" });
  });
}

export function defaultCreditAccount(
  partial?: Partial<CreditAccount>,
): CreditAccount {
  const type = partial?.type ?? "CREDIT_CARD";
  const now = Date.now();
  return {
    id: partial?.id ?? "",
    name: partial?.name ?? "",
    type,
    institution: partial?.institution ?? "",
    accountNumberLast4: partial?.accountNumberLast4 ?? "",
    apr: partial?.apr ?? 0,
    standardApr: partial?.standardApr ?? partial?.apr ?? 0,
    promotionalApr: partial?.promotionalApr ?? null,
    promotionalAprEndDate: partial?.promotionalAprEndDate ?? null,
    promotionAppliesTo: partial?.promotionAppliesTo ?? null,
    deferredInterest: partial?.deferredInterest ?? false,
    currentBalance: partial?.currentBalance ?? 0,
    statementBalanceAsOfMillis: partial?.statementBalanceAsOfMillis ?? null,
    statementAmountDue: partial?.statementAmountDue ?? null,
    dueDay: partial?.dueDay ?? 1,
    linkedBillId: partial?.linkedBillId ?? null,
    annualFeeLinkedBillId: partial?.annualFeeLinkedBillId ?? null,
    notes: partial?.notes ?? "",
    createdAt: partial?.createdAt ?? now,
    updatedAt: partial?.updatedAt ?? now,
    statementEntries: partial?.statementEntries ?? [],
    attachmentHashes: partial?.attachmentHashes ?? [],
    creditLimit: partial?.creditLimit ?? 0,
    minimumPaymentType: partial?.minimumPaymentType ?? "PERCENT_OF_BALANCE",
    minimumPaymentValue: partial?.minimumPaymentValue ?? 2,
    originalPrincipal: partial?.originalPrincipal ?? 0,
    termMonths: partial?.termMonths ?? null,
    monthlyPaymentAmount: partial?.monthlyPaymentAmount ?? null,
    startDate: partial?.startDate ?? null,
    endDate: partial?.endDate ?? null,
    annualFeeAmount: partial?.annualFeeAmount ?? 0,
    annualFeeRenewalDateMillis: partial?.annualFeeRenewalDateMillis ?? null,
    annualFeeFrequency: partial?.annualFeeFrequency ?? "ANNUALLY",
  };
}

export function parseCreditAccountRecord(
  dTag: string,
  plaintext: string,
): CreditAccount | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    if (parsed.deleted === true) return null;
    const name = String(parsed.name ?? "").trim();
    if (!name) return null;
    const type = mapLegacyCreditAccountType(parsed.type);
    return defaultCreditAccount({
      id: String(parsed.id ?? dTag.split("/").pop() ?? newCreditAccountId()),
      name,
      type,
      institution: String(parsed.institution ?? ""),
      accountNumberLast4: String(parsed.accountNumberLast4 ?? ""),
      apr: Number(parsed.apr ?? 0),
      standardApr:
        parsed.standardApr != null
          ? Number(parsed.standardApr)
          : Number(parsed.apr ?? 0),
      promotionalApr:
        parsed.promotionalApr != null ? Number(parsed.promotionalApr) : null,
      promotionalAprEndDate:
        parsed.promotionalAprEndDate != null
          ? Number(parsed.promotionalAprEndDate)
          : null,
      promotionAppliesTo:
        parsed.promotionAppliesTo === "PURCHASES" ||
        parsed.promotionAppliesTo === "BALANCE_TRANSFER" ||
        parsed.promotionAppliesTo === "BOTH"
          ? parsed.promotionAppliesTo
          : null,
      deferredInterest: Boolean(parsed.deferredInterest ?? false),
      currentBalance: Number(parsed.currentBalance ?? 0),
      statementBalanceAsOfMillis:
        parsed.statementBalanceAsOfMillis != null
          ? Number(parsed.statementBalanceAsOfMillis)
          : null,
      statementAmountDue:
        parsed.statementAmountDue != null
          ? Number(parsed.statementAmountDue)
          : null,
      dueDay: Number(parsed.dueDay ?? 1),
      linkedBillId:
        parsed.linkedBillId != null ? String(parsed.linkedBillId) : null,
      annualFeeLinkedBillId:
        parsed.annualFeeLinkedBillId != null
          ? String(parsed.annualFeeLinkedBillId)
          : null,
      notes: parsed.notes != null ? String(parsed.notes) : "",
      createdAt: Number(parsed.createdAt ?? 0),
      updatedAt: Number(parsed.updatedAt ?? 0),
      statementEntries: Array.isArray(parsed.statementEntries)
        ? (parsed.statementEntries as StatementEntry[])
        : [],
      attachmentHashes: Array.isArray(parsed.attachmentHashes)
        ? (parsed.attachmentHashes as string[])
        : [],
      creditLimit: Number(parsed.creditLimit ?? 0),
      minimumPaymentType: mapLegacyMinPaymentType(parsed.minimumPaymentType),
      minimumPaymentValue: Number(parsed.minimumPaymentValue ?? 2),
      originalPrincipal: Number(parsed.originalPrincipal ?? 0),
      termMonths:
        parsed.termMonths != null ? Number(parsed.termMonths) : null,
      monthlyPaymentAmount:
        parsed.monthlyPaymentAmount != null
          ? Number(parsed.monthlyPaymentAmount)
          : null,
      startDate: parsed.startDate != null ? Number(parsed.startDate) : null,
      endDate: parsed.endDate != null ? Number(parsed.endDate) : null,
      annualFeeAmount: Number(parsed.annualFeeAmount ?? 0),
      annualFeeRenewalDateMillis:
        parsed.annualFeeRenewalDateMillis != null
          ? Number(parsed.annualFeeRenewalDateMillis)
          : null,
      annualFeeFrequency:
        (parsed.annualFeeFrequency as BillFrequency) ?? "ANNUALLY",
    });
  } catch {
    return null;
  }
}

export function serializeCreditAccount(account: CreditAccount): string {
  return JSON.stringify(account);
}

export function parseIsoDate(input: string): number | null {
  const value = input.trim();
  if (!value) return null;
  const parts = value.split("-");
  if (parts.length !== 3) return null;
  const year = Number.parseInt(parts[0] ?? "", 10);
  const month = Number.parseInt(parts[1] ?? "", 10);
  const day = Number.parseInt(parts[2] ?? "", 10);
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return null;
  }
  const date = new Date(year, month - 1, day);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

export function formatIsoDate(millis: number): string {
  const d = new Date(millis);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
