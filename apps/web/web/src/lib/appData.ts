import type { AppDataRecord } from "./api";

export type SyncCategory =
  | "salary"
  | "budget"
  | "bills"
  | "goals"
  | "debt"
  | "billers"
  | "banks"
  | "settings"
  | "subscriptions"
  | "tombstones";

const CATEGORY_LABELS: Record<SyncCategory, string> = {
  salary: "Paycheck",
  budget: "Budget",
  bills: "Bills",
  goals: "Goals",
  debt: "Debt",
  billers: "Companies",
  banks: "Bank accounts",
  settings: "Settings",
  subscriptions: "Subscriptions",
  tombstones: "Deleted subs",
};

export function categoryForDTag(dTag: string): SyncCategory | "other" {
  if (dTag === "fiatlife/salary") return "salary";
  if (dTag === "fiatlife/budget") return "budget";
  if (dTag.startsWith("fiatlife/bill/")) return "bills";
  if (dTag.startsWith("fiatlife/goal/")) return "goals";
  if (dTag.startsWith("fiatlife/credit/")) return "debt";
  if (dTag.startsWith("fiatlife/biller/")) return "billers";
  if (dTag.startsWith("fiatlife/settings/bank/")) return "banks";
  if (dTag === "fiatlife/settings/app") return "settings";
  if (dTag.startsWith("subscription:")) return "subscriptions";
  if (dTag.startsWith("fiatlife/cypherlog_deleted/")) return "tombstones";
  return "other";
}

export function categoryLabel(cat: SyncCategory): string {
  return CATEGORY_LABELS[cat];
}

export function summarizeAppData(records: AppDataRecord[]) {
  const counts = new Map<SyncCategory | "other", number>();
  let decrypted = 0;
  let failed = 0;

  for (const record of records) {
    const dTag = record.d_tag ?? "";
    const cat = categoryForDTag(dTag);
    counts.set(cat, (counts.get(cat) ?? 0) + 1);
    if (record.plaintext) decrypted += 1;
    else if (record.decrypt_error) failed += 1;
  }

  return {
    total: records.length,
    decrypted,
    failed,
    counts,
  };
}
