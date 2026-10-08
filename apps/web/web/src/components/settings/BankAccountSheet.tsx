import { useEffect, useState } from "react";
import {
  ALL_BANK_ACCOUNT_TYPES,
  BANK_ACCOUNT_TYPE_LABELS,
  bankAccountType,
  type BankAccount,
  type BankAccountType,
} from "../../lib/bankAccount";
import { formatUsd } from "../../lib/format";
import { formatBalanceAsOf } from "../../lib/simplefin";

export type BankAccountSheetValues = {
  name: string;
  type: BankAccountType;
  /** Manual balance; ignored for SimpleFIN-linked accounts. */
  balance: number | null;
};

export function BankAccountSheet({
  open,
  account,
  onClose,
  onSave,
  onDelete,
  saving,
}: {
  open: boolean;
  account: BankAccount | null;
  onClose: () => void;
  onSave: (values: BankAccountSheetValues) => Promise<void>;
  onDelete?: () => Promise<void>;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [type, setType] = useState<BankAccountType>("CHECKING");
  const [balance, setBalance] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(account?.name ?? "");
    setType(account ? bankAccountType(account) : "CHECKING");
    setBalance(account?.balance != null ? String(account.balance) : "");
    setError(null);
    setConfirmDelete(false);
  }, [open, account]);

  if (!open) return null;

  const isEdit = Boolean(account?.id);
  const isLinked = Boolean(account?.simplefinAccountKey);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Account name is required.");
      return;
    }
    const balanceText = balance.trim().replace(/[$,]/g, "");
    const parsedBalance = balanceText === "" ? null : Number(balanceText);
    if (parsedBalance != null && !Number.isFinite(parsedBalance)) {
      setError("Balance must be a number.");
      return;
    }
    try {
      await onSave({ name: trimmed, type, balance: parsedBalance });
      onClose();
    } catch {
      setError("Could not save account.");
    }
  };

  const onConfirmDelete = async () => {
    if (!onDelete) return;
    setError(null);
    try {
      await onDelete();
      onClose();
    } catch {
      setError("Could not delete account.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="bank-account-sheet-title"
    >
      <div className="card w-full max-w-md p-5">
        <h2 id="bank-account-sheet-title" className="page-title text-xl">
          {isEdit ? "Edit Account" : "Add Account"}
        </h2>
        <p className="text-sm text-muted mt-1">
          Checking and savings accounts can be picked as a bill&apos;s pay-from
          account. No credentials stored.
        </p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="bank-account-name">
              Account name
            </label>
            <input
              id="bank-account-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Chase Checking"
              autoFocus
            />
          </div>
          <div>
            <label className="label" htmlFor="bank-account-type">
              Type
            </label>
            <select
              id="bank-account-type"
              className="input"
              value={type}
              onChange={(e) => setType(e.target.value as BankAccountType)}
            >
              {ALL_BANK_ACCOUNT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {BANK_ACCOUNT_TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          </div>
          {isLinked ? (
            <div className="card-quiet p-3 text-sm">
              <p>
                Balance{" "}
                <span className="money">
                  {account?.balance != null ? formatUsd(account.balance) : "—"}
                </span>
              </p>
              <p className="text-xs text-muted mt-0.5">
                Synced from SimpleFIN
                {account?.balanceAsOf
                  ? ` · as of ${formatBalanceAsOf(account.balanceAsOf)}`
                  : ""}
              </p>
            </div>
          ) : (
            <div>
              <label className="label" htmlFor="bank-account-balance">
                Balance
              </label>
              <input
                id="bank-account-balance"
                className="input"
                inputMode="decimal"
                value={balance}
                onChange={(e) => setBalance(e.target.value)}
                placeholder="Optional"
              />
            </div>
          )}
          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : null}
          {isEdit && onDelete ? (
            confirmDelete ? (
              <div className="card-quiet p-3 space-y-2">
                <p className="text-sm">
                  Delete &ldquo;{account?.name}&rdquo;? Bills tagged with this
                  account will show no payment account.
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="btn-ghost flex-1 text-sm"
                    onClick={() => setConfirmDelete(false)}
                    disabled={saving}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    className="btn-ghost flex-1 text-sm text-error"
                    onClick={() => void onConfirmDelete()}
                    disabled={saving}
                  >
                    {saving ? "Deleting…" : "Delete"}
                  </button>
                </div>
              </div>
            ) : (
              <button
                type="button"
                className="btn-ghost text-sm text-error"
                onClick={() => setConfirmDelete(true)}
                disabled={saving}
              >
                Delete account
              </button>
            )
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
            <button
              type="submit"
              className="btn-primary flex-1"
              disabled={saving || !name.trim()}
            >
              {saving ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
