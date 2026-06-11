/**
 * Monthly budget model — mirrors the planned Android `BudgetConfig`.
 *
 * A budget is a single per-user config record (d_tag `fiatlife/budget`), like
 * salary. It holds a monthly target for each spending category plus, for
 * categories that aren't backed by recurring bills, a manually-entered
 * "spent so far this month" figure.
 *
 * Actuals are hybrid:
 *  - "bill"-kind categories pull their actual from existing bill data
 *    (sum of monthly-equivalent amounts per `BillGeneralCategory`).
 *  - "variable"-kind categories use the user-entered `manualSpent`.
 *
 * `manualSpent` resets at the start of each calendar month (see
 * `rollBudgetPeriod`); targets persist month to month.
 */
import {
  ALL_GENERAL_CATEGORIES,
  GENERAL_CATEGORY_LABELS,
  type BillGeneralCategory,
} from "./bill";

export const BUDGET_D_TAG = "fiatlife/budget";

export type BudgetCategoryKind = "bill" | "variable";

export type CategoryBudget = {
  /** `BillGeneralCategory` name for bill-kind; a `VariableCategoryKey` otherwise. */
  key: string;
  kind: BudgetCategoryKind;
  /** Monthly budget target in dollars. */
  target: number;
  /** Manually-entered spend so far this month (variable categories only). */
  manualSpent: number;
};

export type BudgetConfig = {
  id: string;
  /** Calendar month the `manualSpent` figures apply to, as "YYYY-MM". */
  periodMonth: string;
  categoryBudgets: CategoryBudget[];
  updatedAt: number;
};

/** Spending categories that aren't recurring bills — the "general purchases". */
export const VARIABLE_CATEGORIES: { key: string; label: string }[] = [
  { key: "GROCERIES", label: "Groceries" },
  { key: "DINING", label: "Dining Out" },
  { key: "TRANSPORTATION", label: "Transportation/Fuel" },
  { key: "ENTERTAINMENT", label: "Entertainment" },
  { key: "SHOPPING", label: "Shopping" },
  { key: "PERSONAL_CARE", label: "Personal Care" },
  { key: "MISC", label: "Miscellaneous" },
];

const VARIABLE_LABELS: Record<string, string> = Object.fromEntries(
  VARIABLE_CATEGORIES.map((c) => [c.key, c.label]),
);

export function variableCategoryLabel(key: string): string {
  return VARIABLE_LABELS[key] ?? key;
}

/** Current calendar month as "YYYY-MM" in local time. */
export function currentPeriodMonth(now = Date.now()): string {
  const d = new Date(now);
  const month = `${d.getMonth() + 1}`.padStart(2, "0");
  return `${d.getFullYear()}-${month}`;
}

export function defaultBudgetConfig(now = Date.now()): BudgetConfig {
  return {
    id: "",
    periodMonth: currentPeriodMonth(now),
    categoryBudgets: [],
    updatedAt: 0,
  };
}

function isVariableKey(key: string): boolean {
  return VARIABLE_CATEGORIES.some((c) => c.key === key);
}

function isBillKey(key: string): boolean {
  return (ALL_GENERAL_CATEGORIES as string[]).includes(key);
}

function sanitizeAmount(value: unknown): number {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n) || n < 0) return 0;
  return n;
}

export function normalizeBudgetConfig(config: BudgetConfig): BudgetConfig {
  const seen = new Set<string>();
  const categoryBudgets: CategoryBudget[] = [];
  for (const entry of config.categoryBudgets ?? []) {
    const key = entry.key;
    if (!key || seen.has(key)) continue;
    if (!isVariableKey(key) && !isBillKey(key)) continue;
    seen.add(key);
    const kind: BudgetCategoryKind = isVariableKey(key) ? "variable" : "bill";
    categoryBudgets.push({
      key,
      kind,
      target: sanitizeAmount(entry.target),
      manualSpent: kind === "variable" ? sanitizeAmount(entry.manualSpent) : 0,
    });
  }
  return {
    ...config,
    periodMonth: config.periodMonth || currentPeriodMonth(),
    categoryBudgets,
  };
}

export function parseBudgetRecord(plaintext: string): BudgetConfig | null {
  try {
    const raw = JSON.parse(plaintext) as Partial<BudgetConfig> & {
      deleted?: boolean;
    };
    if (!raw || typeof raw !== "object" || raw.deleted) return null;
    return normalizeBudgetConfig({
      id: typeof raw.id === "string" ? raw.id : "",
      periodMonth:
        typeof raw.periodMonth === "string" ? raw.periodMonth : currentPeriodMonth(),
      categoryBudgets: Array.isArray(raw.categoryBudgets)
        ? (raw.categoryBudgets as CategoryBudget[])
        : [],
      updatedAt: typeof raw.updatedAt === "number" ? raw.updatedAt : 0,
    });
  } catch {
    return null;
  }
}

export function serializeBudget(config: BudgetConfig): string {
  return JSON.stringify(normalizeBudgetConfig(config));
}

/** Stable string for change detection (excludes `updatedAt`). */
export function budgetFingerprint(config: BudgetConfig): string {
  const sorted = [...config.categoryBudgets]
    .map((c) => `${c.key}:${c.kind}:${c.target}:${c.manualSpent}`)
    .sort();
  return `${config.periodMonth}|${sorted.join(",")}`;
}

/**
 * Roll the budget into the current month if it's stale: targets carry over,
 * manually-entered spend resets to 0. Returns the same object when no change.
 */
export function rollBudgetPeriod(
  config: BudgetConfig,
  now = Date.now(),
): BudgetConfig {
  const period = currentPeriodMonth(now);
  if (config.periodMonth === period) return config;
  return {
    ...config,
    periodMonth: period,
    categoryBudgets: config.categoryBudgets.map((c) => ({
      ...c,
      manualSpent: 0,
    })),
  };
}

export function getCategoryBudget(
  config: BudgetConfig,
  key: string,
): CategoryBudget | undefined {
  return config.categoryBudgets.find((c) => c.key === key);
}

/** Immutably upsert a single category's target/spent. */
export function setCategoryBudget(
  config: BudgetConfig,
  key: string,
  kind: BudgetCategoryKind,
  patch: { target?: number; manualSpent?: number },
): BudgetConfig {
  const existing = getCategoryBudget(config, key);
  const next: CategoryBudget = {
    key,
    kind,
    target: sanitizeAmount(patch.target ?? existing?.target ?? 0),
    manualSpent:
      kind === "variable"
        ? sanitizeAmount(patch.manualSpent ?? existing?.manualSpent ?? 0)
        : 0,
  };
  const others = config.categoryBudgets.filter((c) => c.key !== key);
  return { ...config, categoryBudgets: [...others, next] };
}

export type BudgetRow = {
  key: string;
  label: string;
  kind: BudgetCategoryKind;
  target: number;
  /** Bill-kind: derived from bills. Variable-kind: `manualSpent`. */
  actual: number;
  remaining: number;
  /** 0–100+, where >100 means over budget. */
  percentUsed: number;
};

export type BudgetSummary = {
  billRows: BudgetRow[];
  variableRows: BudgetRow[];
  totalTarget: number;
  totalActual: number;
  totalBillActual: number;
  totalVariableActual: number;
  takeHome: number;
  /** take-home minus everything budgeted (targets). */
  unbudgeted: number;
  /** take-home minus everything actually spent/committed. */
  remaining: number;
};

function makeRow(
  key: string,
  label: string,
  kind: BudgetCategoryKind,
  target: number,
  actual: number,
): BudgetRow {
  return {
    key,
    label,
    kind,
    target,
    actual,
    remaining: target - actual,
    percentUsed: target > 0 ? (actual / target) * 100 : actual > 0 ? 100 : 0,
  };
}

/**
 * Build the displayed budget. Bill-kind rows are shown for every general
 * category that has a target set or has bills; variable rows are always shown.
 */
export function computeBudgetSummary(input: {
  config: BudgetConfig;
  billCategoryTotals: Partial<Record<BillGeneralCategory, number>>;
  takeHome: number;
}): BudgetSummary {
  const { config, billCategoryTotals, takeHome } = input;

  const billRows: BudgetRow[] = [];
  for (const cat of ALL_GENERAL_CATEGORIES) {
    const billActual = billCategoryTotals[cat] ?? 0;
    const target = getCategoryBudget(config, cat)?.target ?? 0;
    if (billActual <= 0 && target <= 0) continue;
    billRows.push(
      makeRow(cat, GENERAL_CATEGORY_LABELS[cat], "bill", target, billActual),
    );
  }

  const variableRows: BudgetRow[] = VARIABLE_CATEGORIES.map(({ key, label }) => {
    const entry = getCategoryBudget(config, key);
    return makeRow(key, label, "variable", entry?.target ?? 0, entry?.manualSpent ?? 0);
  });

  const totalBillActual = billRows.reduce((s, r) => s + r.actual, 0);
  const totalVariableActual = variableRows.reduce((s, r) => s + r.actual, 0);
  const totalActual = totalBillActual + totalVariableActual;
  const totalTarget =
    billRows.reduce((s, r) => s + r.target, 0) +
    variableRows.reduce((s, r) => s + r.target, 0);

  return {
    billRows,
    variableRows,
    totalTarget,
    totalActual,
    totalBillActual,
    totalVariableActual,
    takeHome,
    unbudgeted: takeHome - totalTarget,
    remaining: takeHome - totalActual,
  };
}
