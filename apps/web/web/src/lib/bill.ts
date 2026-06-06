/** Bill domain types aligned with Android `domain/model/Bill.kt`. */

import {
  fromLegacyCategory,
  SUBCATEGORY_GENERAL,
  type BillSubcategory,
} from "./billSubcategory";
export {
  fromLegacyCategory,
  generalForSubcategory,
  subcategoriesForGeneral,
  subcategoryLabel,
  SUBCATEGORY_LABELS,
  type BillSubcategory,
} from "./billSubcategory";
import {
  dueAmountInMonth,
  effectiveAmountDue,
} from "./billSchedule";

export {
  daysUntilDue,
  dueAmountInMonth,
  dueAmountInYear,
  dueOccurrencesInMonth,
  effectiveAmountDue,
  formatDueCountdown,
  isCreditCard,
  isCreditOrLoan,
  isPaidForCurrentCycle,
  isPastDue,
  lastDueDateMillis,
  nextDueDateMillis,
  skippedNextDueDateMillis,
} from "./billSchedule";

export type BillFrequency =
  | "WEEKLY"
  | "BIWEEKLY"
  | "MONTHLY"
  | "BIMONTHLY"
  | "QUARTERLY"
  | "SEMIANNUALLY"
  | "ANNUALLY";

export type BillRecurrenceUnit = "DAY" | "WEEK" | "MONTH" | "YEAR";

export type BillGeneralCategory =
  | "AUTO"
  | "UTILITIES"
  | "HOME"
  | "HEALTH"
  | "CREDIT_LOANS"
  | "SUBSCRIPTION"
  | "PERSONAL"
  | "OTHER";

export type BillSource = "NATIVE" | "CYPHERLOG";

export type BillPayment = {
  date: number;
  amount: number;
};

export type StatementEntry = {
  hash: string;
  addedAt: number;
  label: string;
};

export type BillStatusEvent = {
  date: number;
  type: string;
  note: string;
};

export type CreditCardMinPaymentType =
  | "FIXED"
  | "PERCENT_OF_BALANCE"
  | "FULL_BALANCE";

export type CreditCardDetails = {
  currentBalance?: number;
  apr?: number;
  minimumPaymentType?: CreditCardMinPaymentType;
  minimumPaymentValue?: number;
  interestChargedLastPeriod?: number;
};

export type Bill = {
  id: string;
  name: string;
  amount: number;
  subcategory?: string | null;
  category?: string;
  frequency: BillFrequency;
  dueDay?: number;
  autoPay?: boolean;
  renewalDateMillis?: number | null;
  initialPurchaseDateMillis?: number | null;
  recurrenceUnit?: BillRecurrenceUnit | null;
  recurrenceIntervalCount?: number;
  recurrenceTimezone?: string | null;
  isRecurring?: boolean;
  rateValidUntilMillis?: number | null;
  isCancelled?: boolean;
  cancelledAt?: number | null;
  statusHistory?: BillStatusEvent[];
  accountName?: string;
  notes?: string;
  attachmentHashes?: string[];
  statementEntries?: StatementEntry[];
  paymentHistory?: BillPayment[];
  isPaid?: boolean;
  lastPaidDate?: number | null;
  createdAt?: number;
  updatedAt?: number;
  creditCardDetails?: CreditCardDetails | null;
  linkedCreditAccountId?: string | null;
  linkedBillerId?: string | null;
  billerName?: string;
  payFromBankAccountId?: string | null;
  payFromCreditAccountId?: string | null;
};

export type BillWithSource = {
  bill: Bill;
  source: BillSource;
  dTag: string;
  isCypherLog?: boolean;
  /** Unmapped CypherLog tags preserved for round-trip publish. */
  preservedTags?: Record<string, string[]>;
};

export const GENERAL_CATEGORY_LABELS: Record<BillGeneralCategory, string> = {
  AUTO: "Auto",
  UTILITIES: "Utilities",
  HOME: "Home",
  HEALTH: "Health",
  CREDIT_LOANS: "Credit/Loans",
  SUBSCRIPTION: "Subscription",
  PERSONAL: "Personal",
  OTHER: "Other",
};

export const ALL_GENERAL_CATEGORIES: BillGeneralCategory[] = [
  "HOME",
  "UTILITIES",
  "AUTO",
  "CREDIT_LOANS",
  "SUBSCRIPTION",
  "HEALTH",
  "PERSONAL",
  "OTHER",
];

export function effectiveSubcategory(bill: Bill): BillSubcategory {
  if (bill.subcategory) {
    return bill.subcategory as BillSubcategory;
  }
  if (bill.category) return fromLegacyCategory(bill.category);
  return "OTHER";
}

export function generalCategoryForBill(bill: Bill): BillGeneralCategory {
  const sub = effectiveSubcategory(bill);
  return SUBCATEGORY_GENERAL[sub] ?? "OTHER";
}

export function monthlyEquivalent(bill: Bill, monthAnchor = Date.now()): number {
  if (bill.isCancelled) return 0;
  if (bill.isRecurring === false) return 0;
  return dueAmountInMonth(bill, monthAnchor) || 0;
}

export function frequencyLabel(freq: BillFrequency): string {
  return freq.charAt(0) + freq.slice(1).toLowerCase().replace(/_/g, " ");
}

export function canSkipInterval(bill: Bill): boolean {
  return (
    bill.isRecurring !== false &&
    !bill.isCancelled &&
    generalCategoryForBill(bill) === "SUBSCRIPTION" &&
    (effectiveSubcategory(bill) === "FOOD" ||
      effectiveSubcategory(bill) === "HEALTH_WELLNESS")
  );
}

export function statementsOrderedByDate(bill: Bill): StatementEntry[] {
  const entries = bill.statementEntries ?? [];
  if (entries.length > 0) {
    return [...entries].sort((a, b) => b.addedAt - a.addedAt);
  }
  const hashes = bill.attachmentHashes ?? [];
  const updated = bill.updatedAt ?? 0;
  return hashes
    .map((hash) => ({ hash, addedAt: updated, label: "Statement" }))
    .sort((a, b) => b.addedAt - a.addedAt);
}

export function annualTotalPaidSoFar(bill: Bill, now = Date.now()): number {
  const d = new Date(now);
  d.setMonth(0);
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  const yearStart = d.getTime();
  return (bill.paymentHistory ?? [])
    .filter((p) => p.date >= yearStart)
    .reduce((sum, p) => sum + p.amount, 0);
}

function startOfDay(ms: number): number {
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function parseBillRecord(
  dTag: string,
  plaintext: string,
): BillWithSource | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    if (parsed.deleted === true) return null;

    const source: BillSource = dTag.startsWith("subscription:")
      ? "CYPHERLOG"
      : "NATIVE";

    const bill: Bill = {
      id: String(parsed.id ?? dTag.split("/").pop() ?? crypto.randomUUID()),
      name: String(parsed.name ?? "Unnamed bill"),
      amount: Number(parsed.amount ?? 0),
      subcategory: parsed.subcategory != null ? String(parsed.subcategory) : null,
      category: parsed.category != null ? String(parsed.category) : undefined,
      frequency: (parsed.frequency as BillFrequency) ?? "MONTHLY",
      dueDay: Number(parsed.dueDay ?? 1),
      autoPay: Boolean(parsed.autoPay),
      renewalDateMillis:
        parsed.renewalDateMillis != null
          ? Number(parsed.renewalDateMillis)
          : null,
      initialPurchaseDateMillis:
        parsed.initialPurchaseDateMillis != null
          ? Number(parsed.initialPurchaseDateMillis)
          : null,
      recurrenceUnit:
        parsed.recurrenceUnit != null
          ? (String(parsed.recurrenceUnit) as BillRecurrenceUnit)
          : null,
      recurrenceIntervalCount: Number(parsed.recurrenceIntervalCount ?? 1),
      recurrenceTimezone:
        parsed.recurrenceTimezone != null
          ? String(parsed.recurrenceTimezone)
          : null,
      isRecurring: parsed.isRecurring !== false,
      rateValidUntilMillis:
        parsed.rateValidUntilMillis != null
          ? Number(parsed.rateValidUntilMillis)
          : null,
      isCancelled: Boolean(parsed.isCancelled),
      cancelledAt:
        parsed.cancelledAt != null ? Number(parsed.cancelledAt) : null,
      statusHistory: Array.isArray(parsed.statusHistory)
        ? (parsed.statusHistory as BillStatusEvent[])
        : [],
      accountName: parsed.accountName != null ? String(parsed.accountName) : "",
      notes: parsed.notes != null ? String(parsed.notes) : "",
      attachmentHashes: Array.isArray(parsed.attachmentHashes)
        ? (parsed.attachmentHashes as string[])
        : [],
      statementEntries: Array.isArray(parsed.statementEntries)
        ? (parsed.statementEntries as StatementEntry[])
        : [],
      isPaid: Boolean(parsed.isPaid),
      lastPaidDate:
        parsed.lastPaidDate != null ? Number(parsed.lastPaidDate) : null,
      paymentHistory: Array.isArray(parsed.paymentHistory)
        ? (parsed.paymentHistory as BillPayment[])
        : [],
      creditCardDetails:
        parsed.creditCardDetails != null
          ? (parsed.creditCardDetails as CreditCardDetails)
          : null,
      linkedCreditAccountId:
        parsed.linkedCreditAccountId != null
          ? String(parsed.linkedCreditAccountId)
          : null,
      linkedBillerId:
        parsed.linkedBillerId != null ? String(parsed.linkedBillerId) : null,
      billerName: parsed.billerName != null ? String(parsed.billerName) : "",
      payFromBankAccountId:
        parsed.payFromBankAccountId != null
          ? String(parsed.payFromBankAccountId)
          : null,
      payFromCreditAccountId:
        parsed.payFromCreditAccountId != null
          ? String(parsed.payFromCreditAccountId)
          : null,
      createdAt: Number(parsed.createdAt ?? 0),
      updatedAt: Number(parsed.updatedAt ?? 0),
    };

    if (!bill.name.trim()) return null;
    return {
      bill,
      source,
      dTag,
      isCypherLog: source === "CYPHERLOG",
    };
  } catch {
    return null;
  }
}

export function billDTag(id: string, source: BillSource): string {
  return source === "CYPHERLOG" ? `subscription:${id}` : `fiatlife/bill/${id}`;
}

export function newBillId(): string {
  return crypto.randomUUID();
}

export function markBillPaid(
  bill: Bill,
  amount?: number,
  now = Date.now(),
): Bill {
  const paidAmount = amount ?? effectiveAmountDue(bill);
  const history = [...(bill.paymentHistory ?? [])];
  history.push({ date: startOfDay(now), amount: paidAmount });
  return {
    ...bill,
    isPaid: true,
    lastPaidDate: now,
    paymentHistory: history,
    updatedAt: now,
  };
}

export function markBillUnpaid(bill: Bill, now = Date.now()): Bill {
  return {
    ...bill,
    isPaid: false,
    updatedAt: now,
  };
}

export function serializeBill(bill: Bill): string {
  return JSON.stringify(bill);
}
