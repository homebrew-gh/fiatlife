import { useEffect, useState } from "react";
import type { CreditAccount } from "../../lib/creditAccount";
import { formatUsd } from "../../lib/format";

export function UpdateBalanceSheet({
  open,
  account,
  onClose,
  onUpdate,
  saving,
}: {
  open: boolean;
  account: CreditAccount | null;
  onClose: () => void;
  onUpdate: (accountId: string, currentBalance: number) => Promise<void>;
  saving: boolean;
}) {
  const [balance, setBalance] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !account) return;
    setBalance(
      account.currentBalance > 0 ? String(account.currentBalance) : "",
    );
    setError(null);
  }, [open, account]);

  if (!open || !account) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const parsed = Number.parseFloat(balance);
    if (!Number.isFinite(parsed) || parsed < 0) {
      setError("Enter a valid balance.");
      return;
    }
    try {
      await onUpdate(account.id, parsed);
      onClose();
    } catch {
      setError("Could not update balance.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="update-balance-title"
    >
      <div className="card w-full max-w-md p-5">
        <h2 id="update-balance-title" className="page-title text-xl">
          Update Balance
        </h2>
        <p className="text-sm text-muted mt-1">{account.name}</p>
        <p className="text-sm text-muted">
          Previous: {formatUsd(account.currentBalance)}
        </p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="balance-amount">
              Current balance
            </label>
            <input
              id="balance-amount"
              className="input money"
              inputMode="decimal"
              value={balance}
              onChange={(e) => setBalance(e.target.value)}
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
