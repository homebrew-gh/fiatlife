/**
 * CypherLog subscription interop — mirrors Android `CypherLogSubscriptionRepository`.
 * Primary path: Nostr kind 37004 with UUID `d` tag and bill fields in tags (optional JSON content).
 * Legacy: kind 30078 `subscription:{id}` events from earlier FiatLife builds.
 */
import type {
  Bill,
  BillFrequency,
  BillPayment,
  BillRecurrenceUnit,
  BillSubcategory,
  BillWithSource,
} from "./bill";
import { effectiveSubcategory } from "./bill";

export const SUBSCRIPTION_DTAG_PREFIX = "subscription:";
export const DELETE_TOMBSTONE_DTAG_PREFIX = "fiatlife/cypherlog_deleted/";

const MAPPED_TAG_KEYS = new Set([
  "d",
  "id",
  "name",
  "cost",
  "amount",
  "currency",
  "billing_frequency",
  "recurrence",
  "subscription_type",
  "company_name",
  "company_id",
  "notes",
  "alt",
  "due_day",
  "renewal_date",
  "next_due_date",
  "due_date",
  "initial_purchase_date",
  "purchase_date",
  "anchor_date",
  "start_date",
  "interval_unit",
  "interval_count",
  "timezone",
  "fiatlife_is_recurring",
  "fiatlife_rate_valid_until",
  "fiatlife_is_cancelled",
  "fiatlife_cancelled_at",
  "fiatlife_is_paid",
  "fiatlife_last_paid_date",
  "fiatlife_payment",
  "fiatlife_pay_from_bank_id",
  "fiatlife_pay_from_credit_id",
  "schema_version",
  "updated_at",
]);

function tagsToMap(tags: string[][]): Map<string, string[]> {
  const map = new Map<string, string[]>();
  for (const pair of tags) {
    if (pair.length < 2) continue;
    const key = pair[0].toLowerCase();
    const list = map.get(key) ?? [];
    list.push(pair[1]);
    map.set(key, list);
  }
  return map;
}

export function toSubscriptionDTag(idOrDTag: string): string {
  const value = idOrDTag.trim();
  if (value.startsWith(SUBSCRIPTION_DTAG_PREFIX)) return value;
  return `${SUBSCRIPTION_DTAG_PREFIX}${value}`;
}

export function rawSubscriptionId(dTag: string): string {
  return dTag.replace(SUBSCRIPTION_DTAG_PREFIX, "");
}

function rawIdFromDTag(dTag: string): string {
  return rawSubscriptionId(dTag);
}

function updatedAtFromTags(tags: string[][]): number {
  const tagMap = tagsToMap(tags);
  const seconds = Number.parseInt(tagMap.get("updated_at")?.[0] ?? "", 10);
  return Number.isFinite(seconds) ? seconds * 1000 : 0;
}

function nameFromAltTag(tagMap: Map<string, string[]>): string {
  const alt = tagMap.get("alt")?.[0] ?? "";
  const lower = alt.toLowerCase();
  if (lower.includes("encrypted") && lower.includes("subscription data")) return "";
  return alt.replace(/^Subscription:\s*/i, "").trim();
}

function parseIsoDateToMillis(value: string | undefined | null): number | null {
  if (!value?.trim()) return null;
  const input = value.trim();
  const isoParts = input.split("-");
  let year: number;
  let month: number;
  let day: number;
  if (isoParts.length === 3) {
    year = Number.parseInt(isoParts[0], 10);
    month = Number.parseInt(isoParts[1], 10);
    day = Number.parseInt(isoParts[2], 10);
  } else {
    const usParts = input.split("/");
    if (usParts.length !== 3) return null;
    year = Number.parseInt(usParts[2], 10);
    month = Number.parseInt(usParts[0], 10);
    day = Number.parseInt(usParts[1], 10);
  }
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return null;
  }
  const d = new Date(year, month - 1, Math.max(1, day));
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function formatIsoDate(ms: number): string {
  const d = new Date(ms);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function formatUsDate(ms: number): string {
  const d = new Date(ms);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${m}/${day}/${y}`;
}

function dayOfMonthFromMillis(ms: number | null | undefined): number | null {
  if (ms == null) return null;
  return new Date(ms).getDate();
}

function billingFrequencyToBillFrequency(value: string | undefined): BillFrequency {
  switch (value?.toLowerCase()) {
    case "weekly":
      return "WEEKLY";
    case "monthly":
      return "MONTHLY";
    case "quarterly":
      return "QUARTERLY";
    case "semi-annually":
      return "SEMIANNUALLY";
    case "annually":
    case "one-time":
      return "ANNUALLY";
    default:
      return "MONTHLY";
  }
}

function billFrequencyToCypherLog(f: BillFrequency): string {
  switch (f) {
    case "WEEKLY":
      return "weekly";
    case "MONTHLY":
    case "BIWEEKLY":
      return "monthly";
    case "QUARTERLY":
    case "BIMONTHLY":
      return "quarterly";
    case "SEMIANNUALLY":
      return "semi-annually";
    case "ANNUALLY":
      return "annually";
    default:
      return "monthly";
  }
}

function intervalUnitFromCypherLog(
  value: string | undefined,
): BillRecurrenceUnit | null {
  switch (value?.trim().toLowerCase()) {
    case "day":
    case "days":
      return "DAY";
    case "week":
    case "weeks":
      return "WEEK";
    case "month":
    case "months":
      return "MONTH";
    case "year":
    case "years":
      return "YEAR";
    default:
      return null;
  }
}

function intervalUnitToCypherLog(unit: BillRecurrenceUnit | null | undefined): string | null {
  switch (unit) {
    case "DAY":
      return "day";
    case "WEEK":
      return "week";
    case "MONTH":
      return "month";
    case "YEAR":
      return "year";
    default:
      return null;
  }
}

function subscriptionTypeToSubcategory(value: string | undefined): BillSubcategory {
  const v = value?.trim().toLowerCase() ?? "";
  switch (v) {
    case "streaming":
      return "STREAMING";
    case "software":
      return "SOFTWARE";
    case "health/wellness":
    case "health":
    case "wellness":
      return "HEALTH_WELLNESS";
    case "shopping":
      return "SHOPPING";
    case "vehicle":
      return "VEHICLE";
    case "food":
      return "FOOD";
    case "gaming":
      return "GAMING";
    case "news/media":
    case "news":
    case "media":
      return "NEWS_MEDIA";
    case "music":
      return "MUSIC";
    case "home":
      return "SUB_HOME";
    case "finance":
      return "FINANCE";
    case "pet care":
    case "petcare":
      return "PET_CARE";
    case "education":
      return "EDUCATION";
    case "travel":
      return "TRAVEL";
    case "firearm":
    case "firearms":
    case "2a":
    case "gun":
    case "guns":
      return "FIREARM";
    default:
      return "OTHER_SUBSCRIPTION";
  }
}

function billSubcategoryToSubscriptionType(sub: BillSubcategory): string {
  switch (sub) {
    case "STREAMING":
      return "Streaming";
    case "SOFTWARE":
      return "Software";
    case "HEALTH_WELLNESS":
      return "Health/Wellness";
    case "SHOPPING":
      return "Shopping";
    case "VEHICLE":
      return "Vehicle";
    case "FOOD":
      return "Food";
    case "GAMING":
      return "Gaming";
    case "NEWS_MEDIA":
      return "News/Media";
    case "MUSIC":
      return "Music";
    case "SUB_HOME":
      return "Home";
    case "FINANCE":
      return "Finance";
    case "PET_CARE":
      return "Pet Care";
    case "EDUCATION":
      return "Education";
    case "TRAVEL":
      return "Travel";
    case "FIREARM":
      return "Firearm";
    default:
      return "Other";
  }
}

function parseFiatLifePayment(value: string): BillPayment | null {
  const parts = value.split("|");
  if (parts.length !== 2) return null;
  const date = Number.parseInt(parts[0], 10);
  const amount = Number.parseFloat(parts[1]);
  if (!Number.isFinite(date) || !Number.isFinite(amount)) return null;
  return { date, amount };
}

function isRenderableSubscription(
  tags: string[][],
  contentJson: string | null,
): boolean {
  const tagMap = tagsToMap(tags);
  const name = tagMap.get("name")?.[0]?.trim() ?? "";
  const cost =
    Number.parseFloat(tagMap.get("cost")?.[0] ?? "") ||
    Number.parseFloat(tagMap.get("amount")?.[0] ?? "");
  const frequency = tagMap.get("billing_frequency")?.[0]?.trim() ?? "";
  const type = tagMap.get("subscription_type")?.[0]?.trim() ?? "";
  const alt = tagMap.get("alt")?.[0]?.trim().toLowerCase() ?? "";
  const altIsPlaceholder =
    alt.includes("encrypted") && alt.includes("subscription data");

  if (name || Number.isFinite(cost) || frequency || type) return true;
  if (contentJson?.trim() && !contentJson.toLowerCase().includes("could not decrypt")) {
    return true;
  }
  if (alt && !altIsPlaceholder) return true;
  return false;
}

function extractPreservedTags(
  tagMap: Map<string, string[]>,
): Record<string, string[]> | null {
  const preserved: Record<string, string[]> = {};
  for (const [key, values] of tagMap) {
    if (!MAPPED_TAG_KEYS.has(key)) preserved[key] = [...values];
  }
  return Object.keys(preserved).length > 0 ? preserved : null;
}

function contentJsonToBill(
  dTag: string,
  contentJson: string,
  tags: string[][],
): { bill: Bill; preservedTags: Record<string, string[]> | null } | null {
  const tagMap = tagsToMap(tags);
  const preserved = extractPreservedTags(tagMap);
  try {
    const root = JSON.parse(contentJson) as unknown;
    let obj: Record<string, unknown>;
    if (root && typeof root === "object" && !Array.isArray(root)) {
      const record = root as Record<string, unknown>;
      if (
        "name" in record ||
        "cost" in record ||
        "billing_frequency" in record
      ) {
        obj = record;
      } else if (record.data && typeof record.data === "object") {
        obj = record.data as Record<string, unknown>;
      } else {
        obj = record;
      }
    } else if (Array.isArray(root) && root.length > 0 && typeof root[0] === "object") {
      obj = root[0] as Record<string, unknown>;
    } else {
      return tagsToBill(dTag, tags);
    }

    const str = (...keys: string[]) => {
      for (const key of keys) {
        const value = obj[key];
        if (typeof value === "string" && value.trim()) return value.trim();
        if (typeof value === "number" && Number.isFinite(value)) {
          return String(value);
        }
      }
      return "";
    };
    const num = (...keys: string[]) => {
      for (const key of keys) {
        const value = obj[key];
        if (typeof value === "number" && Number.isFinite(value)) return value;
        if (typeof value === "string") {
          const parsed = Number.parseFloat(value);
          if (Number.isFinite(parsed)) return parsed;
        }
      }
      return null;
    };

    let name = str("name", "subscriptionName", "subscription_name", "title", "description");
    const cost = num("cost", "amount", "price", "costAmount", "subscriptionCost") ?? 0;
    const frequency = billingFrequencyToBillFrequency(
      str("billing_frequency", "billingFrequency", "recurrence") ||
        tagMap.get("billing_frequency")?.[0],
    );
    const notes = str("notes") || tagMap.get("notes")?.[0] || "";
    const companyName =
      str("company_name", "companyName") || tagMap.get("company_name")?.[0] || "";
    const parsedDueDay = Number.parseInt(str("due_day") || tagMap.get("due_day")?.[0] || "", 10);
    const renewalDateMillis = parseIsoDateToMillis(
      str("renewal_date", "next_due_date", "due_date") ||
        tagMap.get("renewal_date")?.[0] ||
        tagMap.get("next_due_date")?.[0] ||
        tagMap.get("due_date")?.[0],
    );
    const initialPurchaseDateMillis = parseIsoDateToMillis(
      str("initial_purchase_date", "purchase_date", "anchor_date", "start_date") ||
        tagMap.get("initial_purchase_date")?.[0] ||
        tagMap.get("purchase_date")?.[0] ||
        tagMap.get("anchor_date")?.[0] ||
        tagMap.get("start_date")?.[0],
    );
    const dueDay =
      Number.isFinite(parsedDueDay) && parsedDueDay >= 1 && parsedDueDay <= 31
        ? parsedDueDay
        : dayOfMonthFromMillis(renewalDateMillis) ??
          dayOfMonthFromMillis(initialPurchaseDateMillis) ??
          1;
    if (!name) name = nameFromAltTag(tagMap);
    const subcategory = subscriptionTypeToSubcategory(
      str("subscription_type", "subscriptionType") || tagMap.get("subscription_type")?.[0],
    );

    const bill: Bill = {
      id: tagMap.get("id")?.[0]?.trim() || rawIdFromDTag(dTag),
      name: name || "Subscription",
      amount: cost,
      subcategory,
      frequency,
      dueDay,
      renewalDateMillis,
      initialPurchaseDateMillis,
      recurrenceUnit: intervalUnitFromCypherLog(
        str("interval_unit") || tagMap.get("interval_unit")?.[0],
      ),
      recurrenceIntervalCount: Math.max(
        Number.parseInt(str("interval_count") || tagMap.get("interval_count")?.[0] || "1", 10) || 1,
        1,
      ),
      recurrenceTimezone: str("timezone") || tagMap.get("timezone")?.[0] || null,
      isRecurring:
        (str("fiatlife_is_recurring") || tagMap.get("fiatlife_is_recurring")?.[0] || "")
          .toLowerCase() !== "false",
      rateValidUntilMillis: parseIsoDateToMillis(
        str("fiatlife_rate_valid_until") || tagMap.get("fiatlife_rate_valid_until")?.[0],
      ),
      isCancelled:
        (str("fiatlife_is_cancelled") || tagMap.get("fiatlife_is_cancelled")?.[0] || "")
          .toLowerCase() === "true",
      cancelledAt:
        Number.parseInt(
          str("fiatlife_cancelled_at") || tagMap.get("fiatlife_cancelled_at")?.[0] || "",
          10,
        ) || null,
      isPaid:
        (str("fiatlife_is_paid") || tagMap.get("fiatlife_is_paid")?.[0] || "")
          .toLowerCase() === "true",
      lastPaidDate:
        Number.parseInt(
          str("fiatlife_last_paid_date") || tagMap.get("fiatlife_last_paid_date")?.[0] || "",
          10,
        ) || null,
      paymentHistory: (tagMap.get("fiatlife_payment") ?? [])
        .map(parseFiatLifePayment)
        .filter((p): p is BillPayment => p != null),
      accountName: companyName,
      billerName: companyName,
      notes,
      updatedAt: updatedAtFromTags(tags),
      payFromBankAccountId: str("fiatlife_pay_from_bank_id") || tagMap.get("fiatlife_pay_from_bank_id")?.[0] || null,
      payFromCreditAccountId:
        str("fiatlife_pay_from_credit_id") || tagMap.get("fiatlife_pay_from_credit_id")?.[0] || null,
    };
    return { bill, preservedTags: preserved };
  } catch {
    return tagsToBill(dTag, tags);
  }
}

function tagsToBill(
  dTag: string,
  tags: string[][],
): { bill: Bill; preservedTags: Record<string, string[]> | null } | null {
  if (!isRenderableSubscription(tags, null)) return null;
  const tagMap = tagsToMap(tags);
  const first = (key: string) => tagMap.get(key)?.[0];

  let name = first("name")?.trim() ?? "";
  if (!name) name = nameFromAltTag(tagMap);
  const cost =
    Number.parseFloat(first("cost") ?? "") ||
    Number.parseFloat(first("amount") ?? "") ||
    0;
  const frequency = billingFrequencyToBillFrequency(
    first("billing_frequency") ?? first("recurrence"),
  );
  const notes = first("notes") ?? "";
  const companyName = first("company_name") ?? "";
  const parsedDueDay = Number.parseInt(first("due_day") ?? "", 10);
  const subcategory = subscriptionTypeToSubcategory(first("subscription_type"));
  const renewalDateMillis = parseIsoDateToMillis(
    first("renewal_date") ?? first("next_due_date") ?? first("due_date"),
  );
  const initialPurchaseDateMillis = parseIsoDateToMillis(
    first("initial_purchase_date") ??
      first("purchase_date") ??
      first("anchor_date") ??
      first("start_date"),
  );
  const dueDay =
    Number.isFinite(parsedDueDay) && parsedDueDay >= 1 && parsedDueDay <= 31
      ? parsedDueDay
      : dayOfMonthFromMillis(renewalDateMillis) ??
        dayOfMonthFromMillis(initialPurchaseDateMillis) ??
        1;
  const recurrenceUnit = intervalUnitFromCypherLog(first("interval_unit"));
  const recurrenceIntervalCount = Math.max(
    Number.parseInt(first("interval_count") ?? "1", 10) || 1,
    1,
  );
  const recurrenceTimezone = first("timezone") ?? null;
  const isRecurring = first("fiatlife_is_recurring")?.toLowerCase() !== "false";
  const rateValidUntilMillis = parseIsoDateToMillis(first("fiatlife_rate_valid_until"));
  const isCancelled = first("fiatlife_is_cancelled")?.toLowerCase() === "true";
  const cancelledAt = Number.parseInt(first("fiatlife_cancelled_at") ?? "", 10) || null;
  const isPaid = first("fiatlife_is_paid")?.toLowerCase() === "true";
  const lastPaidDate = Number.parseInt(first("fiatlife_last_paid_date") ?? "", 10) || null;
  const paymentHistory = (tagMap.get("fiatlife_payment") ?? [])
    .map(parseFiatLifePayment)
    .filter((p): p is BillPayment => p != null);

  const bill: Bill = {
    id: first("id")?.trim() || rawIdFromDTag(dTag),
    name: name || "Subscription",
    amount: cost,
    subcategory,
    frequency,
    dueDay,
    renewalDateMillis,
    initialPurchaseDateMillis,
    recurrenceUnit,
    recurrenceIntervalCount,
    recurrenceTimezone,
    isRecurring,
    rateValidUntilMillis,
    isCancelled,
    cancelledAt,
    paymentHistory,
    isPaid,
    lastPaidDate,
    accountName: companyName,
    billerName: companyName,
    notes,
    updatedAt: updatedAtFromTags(tags),
    payFromBankAccountId: first("fiatlife_pay_from_bank_id") || null,
    payFromCreditAccountId: first("fiatlife_pay_from_credit_id") || null,
  };

  return { bill, preservedTags: extractPreservedTags(tagMap) };
}

export function billToCypherLogTags(
  bill: Bill,
  preservedTags?: Record<string, string[]> | null,
): string[][] {
  const rawId = bill.id || crypto.randomUUID();
  const tags: string[][] = [
    ["d", rawId],
    ["id", rawId],
    ["alt", `Subscription: ${bill.name}`],
    ["name", bill.name],
    ["subscription_type", billSubcategoryToSubscriptionType(effectiveSubcategory(bill))],
    ["cost", String(bill.amount)],
    ["amount", String(bill.amount)],
    ["billing_frequency", billFrequencyToCypherLog(bill.frequency)],
    ["recurrence", billFrequencyToCypherLog(bill.frequency)],
    ["due_day", String(bill.dueDay ?? 1)],
    ["schema_version", "2"],
    ["updated_at", String(Math.floor(Date.now() / 1000))],
  ];

  if (bill.renewalDateMillis) {
    tags.push(["renewal_date", formatIsoDate(bill.renewalDateMillis)]);
  }
  if (bill.initialPurchaseDateMillis) {
    tags.push(["initial_purchase_date", formatIsoDate(bill.initialPurchaseDateMillis)]);
    tags.push(["start_date", formatUsDate(bill.initialPurchaseDateMillis)]);
  }
  const intervalUnit = intervalUnitToCypherLog(bill.recurrenceUnit);
  if (intervalUnit) tags.push(["interval_unit", intervalUnit]);
  if ((bill.recurrenceIntervalCount ?? 1) > 1) {
    tags.push(["interval_count", String(bill.recurrenceIntervalCount)]);
  }
  if (bill.recurrenceTimezone) tags.push(["timezone", bill.recurrenceTimezone]);
  if (bill.isRecurring === false) tags.push(["fiatlife_is_recurring", "false"]);
  if (bill.rateValidUntilMillis) {
    tags.push(["fiatlife_rate_valid_until", formatIsoDate(bill.rateValidUntilMillis)]);
  }
  if (bill.isCancelled) tags.push(["fiatlife_is_cancelled", "true"]);
  if (bill.cancelledAt) tags.push(["fiatlife_cancelled_at", String(bill.cancelledAt)]);
  if (bill.isPaid) tags.push(["fiatlife_is_paid", "true"]);
  if (bill.lastPaidDate) tags.push(["fiatlife_last_paid_date", String(bill.lastPaidDate)]);
  for (const p of bill.paymentHistory ?? []) {
    tags.push(["fiatlife_payment", `${p.date}|${p.amount}`]);
  }
  if (bill.notes?.trim()) tags.push(["notes", bill.notes.trim()]);
  const companyName = (bill.billerName || bill.accountName || "").trim();
  if (companyName) tags.push(["company_name", companyName]);
  if (bill.payFromBankAccountId) {
    tags.push(["fiatlife_pay_from_bank_id", bill.payFromBankAccountId]);
  }
  if (bill.payFromCreditAccountId) {
    tags.push(["fiatlife_pay_from_credit_id", bill.payFromCreditAccountId]);
  }
  if (preservedTags) {
    for (const [key, values] of Object.entries(preservedTags)) {
      if (key === "d" || key === "id") continue;
      for (const v of values) tags.push([key, v]);
    }
  }
  return tags;
}

export type CypherLogSubscriptionRecord = {
  event_id: string;
  d_tag: string;
  created_at: number;
  tags: string[][];
  content: string;
  plaintext?: string | null;
  decrypt_error?: string | null;
};

export function parseCypherLog37004Record(
  record: CypherLogSubscriptionRecord,
): BillWithSource | null {
  const dTag = record.d_tag.trim();
  if (!dTag || dTag.startsWith(SUBSCRIPTION_DTAG_PREFIX)) return null;

  const tags = record.tags ?? [];
  const contentJson = record.plaintext?.trim() || null;
  const fromContent =
    contentJson && !contentJson.toLowerCase().includes("could not decrypt")
      ? contentJsonToBill(dTag, contentJson, tags)
      : null;
  const parsed = fromContent ?? tagsToBill(dTag, tags);
  if (!parsed) return null;

  return {
    bill: {
      ...parsed.bill,
      updatedAt: Math.max(parsed.bill.updatedAt ?? 0, record.created_at * 1000),
    },
    source: "CYPHERLOG",
    dTag,
    isCypherLog: true,
    preservedTags: parsed.preservedTags ?? undefined,
  };
}

export function mergeCypherLogSubscriptions(
  legacy30078: BillWithSource[],
  kind37004: BillWithSource[],
): BillWithSource[] {
  const byId = new Map<string, BillWithSource>();

  const score = (item: BillWithSource) => item.bill.updatedAt ?? 0;

  for (const item of [...legacy30078, ...kind37004]) {
    const id = rawSubscriptionId(item.dTag);
    const existing = byId.get(id);
    if (!existing || score(item) >= score(existing)) {
      byId.set(id, item);
    }
  }
  return [...byId.values()];
}

export function parseCypherLogRecord(
  dTag: string,
  tags: string[][],
  plaintext: string | null | undefined,
): BillWithSource | null {
  const normalized = toSubscriptionDTag(dTag);
  if (tags.length > 0) {
    const fromTags = tagsToBill(normalized, tags);
    if (fromTags) {
      return {
        bill: fromTags.bill,
        source: "CYPHERLOG",
        dTag: normalized,
        isCypherLog: true,
        preservedTags: fromTags.preservedTags ?? undefined,
      };
    }
  }
  // Legacy: web previously published encrypted JSON under subscription: d-tags.
  if (plaintext?.trim()) {
    try {
      const parsed = JSON.parse(plaintext) as Record<string, unknown>;
      if (parsed.deleted === true) return null;
      const bill: Bill = {
        id: String(parsed.id ?? rawIdFromDTag(normalized)),
        name: String(parsed.name ?? "Subscription"),
        amount: Number(parsed.amount ?? 0),
        subcategory: parsed.subcategory != null ? String(parsed.subcategory) : "OTHER_SUBSCRIPTION",
        frequency: (parsed.frequency as BillFrequency) ?? "MONTHLY",
        dueDay: Number(parsed.dueDay ?? 1),
        isRecurring: parsed.isRecurring !== false,
        isCancelled: Boolean(parsed.isCancelled),
        isPaid: Boolean(parsed.isPaid),
        paymentHistory: Array.isArray(parsed.paymentHistory)
          ? (parsed.paymentHistory as BillPayment[])
          : [],
        billerName: String(parsed.billerName ?? ""),
        notes: String(parsed.notes ?? ""),
        updatedAt: Number(parsed.updatedAt ?? 0),
      };
      if (!bill.name.trim()) return null;
      return {
        bill,
        source: "CYPHERLOG",
        dTag: normalized,
        isCypherLog: true,
      };
    } catch {
      return null;
    }
  }
  return null;
}

/** Collect deleted subscription raw ids from tombstone records. */
export function deletedSubscriptionIds(
  records: Array<{ d_tag?: string | null; plaintext?: string | null }>,
): Set<string> {
  const deleted = new Set<string>();
  for (const record of records) {
    const dTag = record.d_tag?.trim() ?? "";
    if (!dTag.startsWith(DELETE_TOMBSTONE_DTAG_PREFIX)) continue;
    const suffix = dTag.slice(DELETE_TOMBSTONE_DTAG_PREFIX.length).trim();
    if (suffix) deleted.add(rawSubscriptionId(suffix));
    if (record.plaintext?.trim()) {
      try {
        const parsed = JSON.parse(record.plaintext) as { dTag?: string; deleted?: boolean };
        if (parsed.deleted !== false && parsed.dTag?.trim()) {
          deleted.add(rawSubscriptionId(parsed.dTag.trim()));
        }
      } catch {
        // ignore
      }
    }
  }
  return deleted;
}

/** @deprecated use deletedSubscriptionIds */
export function deletedSubscriptionDTags(
  records: Array<{ d_tag?: string | null; plaintext?: string | null }>,
): Set<string> {
  const ids = deletedSubscriptionIds(records);
  return new Set([...ids].map((id) => toSubscriptionDTag(id)));
}

export function cypherLogTombstoneDTag(subscriptionDTag: string): string {
  return `${DELETE_TOMBSTONE_DTAG_PREFIX}${rawSubscriptionId(subscriptionDTag)}`;
}

export function cypherLogTombstonePayload(subscriptionDTag: string): string {
  const rawId = rawSubscriptionId(subscriptionDTag);
  return JSON.stringify({
    deleted: true,
    dTag: rawId,
    updatedAt: Date.now(),
  });
}

export const CYPHERLOG_KIND = 37004;
export const LEGACY_SUBSCRIPTION_KIND = 30078;
