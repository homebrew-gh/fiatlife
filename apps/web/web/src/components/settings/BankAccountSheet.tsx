import { useEffect, useState } from "react";
import type { BankAccount } from "../../lib/bankAccount";

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
  onSave: (name: string) => Promise<void>;
  onDelete?: () => Promise<void>;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(account?.name ?? "");
    setError(null);
    setConfirmDelete(false);
  }, [open, account]);

  if (!open) return null;

  const isEdit = Boolean(account?.id);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Account name is required.");
      return;
    }
    try {
      await onSave(trimmed);
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
          {isEdit ? "Edit Bank Account" : "Add Bank Account"}
        </h2>
        <p className="text-sm text-muted mt-1">
          Named account to tag which bills are paid from which account. No
          credentials stored.
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
