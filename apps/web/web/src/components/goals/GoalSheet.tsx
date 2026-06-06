import { useEffect, useState } from "react";
import {
  ALL_GOAL_CATEGORIES,
  GOAL_CATEGORY_COLORS,
  GOAL_CATEGORY_LABELS,
  type FinancialGoal,
  type GoalCategory,
} from "../../lib/goal";

type GoalInput = Omit<FinancialGoal, "id" | "createdAt" | "updatedAt">;

export function GoalSheet({
  open,
  goal,
  onClose,
  onSave,
  saving,
}: {
  open: boolean;
  goal: FinancialGoal | null;
  onClose: () => void;
  onSave: (input: GoalInput) => Promise<void>;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [category, setCategory] = useState<GoalCategory>("GENERAL_SAVINGS");
  const [targetAmount, setTargetAmount] = useState("");
  const [currentAmount, setCurrentAmount] = useState("");
  const [monthlyContribution, setMonthlyContribution] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName(goal?.name ?? "");
    setCategory(goal?.category ?? "GENERAL_SAVINGS");
    setTargetAmount(
      goal?.targetAmount != null && goal.targetAmount > 0
        ? String(goal.targetAmount)
        : "",
    );
    setCurrentAmount(
      goal?.currentAmount != null && goal.currentAmount > 0
        ? String(goal.currentAmount)
        : "",
    );
    setMonthlyContribution(
      goal?.monthlyContribution != null && goal.monthlyContribution > 0
        ? String(goal.monthlyContribution)
        : "",
    );
    setNotes(goal?.notes ?? "");
    setError(null);
  }, [open, goal]);

  if (!open) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const parsedTarget = Number.parseFloat(targetAmount);
    const parsedCurrent = Number.parseFloat(currentAmount) || 0;
    const parsedMonthly = Number.parseFloat(monthlyContribution) || 0;

    if (!name.trim()) {
      setError("Name is required.");
      return;
    }
    if (!Number.isFinite(parsedTarget) || parsedTarget <= 0) {
      setError("Enter a target amount greater than zero.");
      return;
    }
    if (!Number.isFinite(parsedCurrent) || parsedCurrent < 0) {
      setError("Enter a valid current amount.");
      return;
    }
    if (!Number.isFinite(parsedMonthly) || parsedMonthly < 0) {
      setError("Enter a valid monthly contribution.");
      return;
    }

    try {
      await onSave({
        name: name.trim(),
        category,
        targetAmount: parsedTarget,
        currentAmount: parsedCurrent,
        monthlyContribution: parsedMonthly,
        targetDate: goal?.targetDate ?? null,
        notes: notes.trim(),
        color: GOAL_CATEGORY_COLORS[category],
      });
      onClose();
    } catch {
      setError("Could not save goal.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="goal-sheet-title"
    >
      <div className="card w-full max-w-md p-5 max-h-[90vh] overflow-y-auto">
        <h2 id="goal-sheet-title" className="page-title text-xl">
          {goal ? "Edit Goal" : "Add Goal"}
        </h2>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="goal-name">
              Goal name
            </label>
            <input
              id="goal-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Emergency fund"
              autoFocus
            />
          </div>
          <div>
            <label className="label" htmlFor="goal-category">
              Category
            </label>
            <select
              id="goal-category"
              className="input"
              value={category}
              onChange={(e) => setCategory(e.target.value as GoalCategory)}
            >
              {ALL_GOAL_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {GOAL_CATEGORY_LABELS[c]}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="goal-target">
              Target amount
            </label>
            <input
              id="goal-target"
              className="input money"
              inputMode="decimal"
              value={targetAmount}
              onChange={(e) => setTargetAmount(e.target.value)}
              placeholder="10000.00"
            />
          </div>
          <div>
            <label className="label" htmlFor="goal-current">
              Current amount
            </label>
            <input
              id="goal-current"
              className="input money"
              inputMode="decimal"
              value={currentAmount}
              onChange={(e) => setCurrentAmount(e.target.value)}
              placeholder="0.00"
            />
          </div>
          <div>
            <label className="label" htmlFor="goal-monthly">
              Monthly contribution
            </label>
            <input
              id="goal-monthly"
              className="input money"
              inputMode="decimal"
              value={monthlyContribution}
              onChange={(e) => setMonthlyContribution(e.target.value)}
              placeholder="0.00"
            />
          </div>
          <div>
            <label className="label" htmlFor="goal-notes">
              Notes
            </label>
            <textarea
              id="goal-notes"
              className="input min-h-[4.5rem] resize-y"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Optional notes"
            />
          </div>
          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : null}
          <div className="flex gap-2 pt-2">
            <button
              type="button"
              className="btn-ghost flex-1"
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1" disabled={saving}>
              {saving ? "Saving…" : goal ? "Save changes" : "Add goal"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
