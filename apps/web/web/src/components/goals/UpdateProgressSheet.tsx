import { useEffect, useState } from "react";
import type { FinancialGoal } from "../../lib/goal";
import { formatUsd } from "../../lib/format";

export function UpdateProgressSheet({
  open,
  goal,
  onClose,
  onUpdate,
  saving,
}: {
  open: boolean;
  goal: FinancialGoal | null;
  onClose: () => void;
  onUpdate: (goalId: string, currentAmount: number) => Promise<void>;
  saving: boolean;
}) {
  const [amount, setAmount] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !goal) return;
    setAmount(
      goal.currentAmount > 0 ? String(goal.currentAmount) : "",
    );
    setError(null);
  }, [open, goal]);

  if (!open || !goal) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const parsed = Number.parseFloat(amount);
    if (!Number.isFinite(parsed) || parsed < 0) {
      setError("Enter a valid amount.");
      return;
    }
    try {
      await onUpdate(goal.id, parsed);
      onClose();
    } catch {
      setError("Could not update progress.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="update-progress-title"
    >
      <div className="card w-full max-w-md p-5">
        <h2 id="update-progress-title" className="page-title text-xl">
          Update Progress
        </h2>
        <p className="text-sm text-muted mt-1">{goal.name}</p>
        <p className="text-sm text-muted">
          Target: {formatUsd(goal.targetAmount)}
        </p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="progress-amount">
              Current amount
            </label>
            <input
              id="progress-amount"
              className="input money"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              autoFocus
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
              {saving ? "Saving…" : "Update"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
