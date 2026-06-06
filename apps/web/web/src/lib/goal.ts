export const GOAL_D_TAG_PREFIX = "fiatlife/goal/";

export const ALL_GOAL_CATEGORIES = [
  "EMERGENCY_FUND",
  "RETIREMENT",
  "HOUSE_DOWN_PAYMENT",
  "CAR_PURCHASE",
  "VACATION",
  "WEDDING",
  "EDUCATION",
  "DEBT_PAYOFF",
  "GENERAL_SAVINGS",
  "INVESTMENT",
  "HOME_IMPROVEMENT",
  "MEDICAL",
  "OTHER",
] as const;

export type GoalCategory = (typeof ALL_GOAL_CATEGORIES)[number];

export const GOAL_CATEGORY_LABELS: Record<GoalCategory, string> = {
  EMERGENCY_FUND: "Emergency Fund",
  RETIREMENT: "Retirement",
  HOUSE_DOWN_PAYMENT: "House Down Payment",
  CAR_PURCHASE: "New Car",
  VACATION: "Trip/Vacation",
  WEDDING: "Wedding",
  EDUCATION: "Education",
  DEBT_PAYOFF: "Debt Payoff",
  GENERAL_SAVINGS: "Cash Savings",
  INVESTMENT: "Investment",
  HOME_IMPROVEMENT: "Home Improvement",
  MEDICAL: "Medical",
  OTHER: "Other",
};

export const GOAL_CATEGORY_COLORS: Record<GoalCategory, string> = {
  EMERGENCY_FUND: "#F44336",
  RETIREMENT: "#9C27B0",
  HOUSE_DOWN_PAYMENT: "#2196F3",
  CAR_PURCHASE: "#FF9800",
  VACATION: "#00BCD4",
  WEDDING: "#E91E63",
  EDUCATION: "#3F51B5",
  DEBT_PAYOFF: "#795548",
  GENERAL_SAVINGS: "#4CAF50",
  INVESTMENT: "#009688",
  HOME_IMPROVEMENT: "#607D8B",
  MEDICAL: "#F44336",
  OTHER: "#9E9E9E",
};

export type FinancialGoal = {
  id: string;
  name: string;
  category: GoalCategory;
  targetAmount: number;
  currentAmount: number;
  monthlyContribution: number;
  targetDate?: number | null;
  notes: string;
  color: string;
  createdAt: number;
  updatedAt: number;
};

export function goalDTag(id: string): string {
  return `${GOAL_D_TAG_PREFIX}${id}`;
}

export function newGoalId(): string {
  return crypto.randomUUID();
}

export function mapLegacyGoalCategory(raw: unknown): GoalCategory {
  const value = String(raw ?? "")
    .trim()
    .toUpperCase();
  switch (value) {
    case "HOME_RENOVATION":
      return "HOME_IMPROVEMENT";
    case "CAR":
    case "AUTO":
    case "CAR_GOAL":
    case "VEHICLE":
      return "CAR_PURCHASE";
    case "EMERGENCY_FUND":
    case "RETIREMENT":
    case "HOUSE_DOWN_PAYMENT":
    case "CAR_PURCHASE":
    case "VACATION":
    case "WEDDING":
    case "EDUCATION":
    case "DEBT_PAYOFF":
    case "GENERAL_SAVINGS":
    case "INVESTMENT":
    case "HOME_IMPROVEMENT":
    case "MEDICAL":
    case "OTHER":
      return value;
    default:
      return "OTHER";
  }
}

export function goalProgressPercent(goal: FinancialGoal): number {
  if (goal.targetAmount <= 0) return 0;
  return Math.min(100, (goal.currentAmount / goal.targetAmount) * 100);
}

export function goalRemainingAmount(goal: FinancialGoal): number {
  return Math.max(0, goal.targetAmount - goal.currentAmount);
}

export function goalIsComplete(goal: FinancialGoal): boolean {
  return goal.currentAmount >= goal.targetAmount;
}

export function goalMonthsRemaining(goal: FinancialGoal): number | null {
  if (goal.monthlyContribution <= 0) return null;
  const remaining = goalRemainingAmount(goal);
  if (remaining <= 0) return null;
  return Math.ceil(remaining / goal.monthlyContribution);
}

export function summarizeGoals(goals: FinancialGoal[]): {
  totalTarget: number;
  totalSaved: number;
  overallProgress: number;
} {
  const totalTarget = goals.reduce((sum, g) => sum + g.targetAmount, 0);
  const totalSaved = goals.reduce((sum, g) => sum + g.currentAmount, 0);
  const overallProgress =
    totalTarget > 0 ? (totalSaved / totalTarget) * 100 : 0;
  return { totalTarget, totalSaved, overallProgress };
}

export function defaultGoal(
  partial?: Partial<FinancialGoal>,
): FinancialGoal {
  const category = partial?.category ?? "GENERAL_SAVINGS";
  const now = Date.now();
  return {
    id: partial?.id ?? "",
    name: partial?.name ?? "",
    category,
    targetAmount: partial?.targetAmount ?? 0,
    currentAmount: partial?.currentAmount ?? 0,
    monthlyContribution: partial?.monthlyContribution ?? 0,
    targetDate: partial?.targetDate ?? null,
    notes: partial?.notes ?? "",
    color: partial?.color ?? GOAL_CATEGORY_COLORS[category],
    createdAt: partial?.createdAt ?? now,
    updatedAt: partial?.updatedAt ?? now,
  };
}

export function parseGoalRecord(
  dTag: string,
  plaintext: string,
): FinancialGoal | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    if (parsed.deleted === true) return null;
    const name = String(parsed.name ?? "").trim();
    if (!name) return null;
    const category = mapLegacyGoalCategory(parsed.category);
    return {
      id: String(parsed.id ?? dTag.split("/").pop() ?? newGoalId()),
      name,
      category,
      targetAmount: Number(parsed.targetAmount ?? 0),
      currentAmount: Number(parsed.currentAmount ?? 0),
      monthlyContribution: Number(parsed.monthlyContribution ?? 0),
      targetDate:
        parsed.targetDate != null ? Number(parsed.targetDate) : null,
      notes: parsed.notes != null ? String(parsed.notes) : "",
      color:
        parsed.color != null
          ? String(parsed.color)
          : GOAL_CATEGORY_COLORS[category],
      createdAt: Number(parsed.createdAt ?? 0),
      updatedAt: Number(parsed.updatedAt ?? 0),
    };
  } catch {
    return null;
  }
}

export function serializeGoal(goal: FinancialGoal): string {
  return JSON.stringify(goal);
}
