import { useEffect, useState } from "react";
import {
  formatBtc,
  parseBtcToSats,
  satsToUsd,
  type BitcoinWallet,
} from "../../lib/bitcoin";
import { formatUsd } from "../../lib/format";

export function BitcoinWalletSheet({
  open,
  wallet,
  usdPerBtc,
  onClose,
  onSave,
  onDelete,
  saving,
}: {
  open: boolean;
  wallet: BitcoinWallet | null;
  usdPerBtc: number | null;
  onClose: () => void;
  onSave: (values: {
    name: string;
    sats: number;
    notes: string;
    retirement: boolean;
  }) => Promise<void>;
  onDelete?: () => Promise<void>;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [notes, setNotes] = useState("");
  const [retirement, setRetirement] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    if (!open) return;
    setName(wallet?.name ?? "");
    setAmount(wallet?.id ? formatBtc(wallet.sats).replace(/,/g, "") : "");
    setNotes(wallet?.notes ?? "");
    setRetirement(wallet?.retirement === true);
    setError(null);
    setConfirmDelete(false);
  }, [open, wallet]);

  if (!open) return null;

  const isEdit = Boolean(wallet?.id);
  const sats = parseBtcToSats(amount);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) {
      setError("Wallet name is required.");
      return;
    }
    if (sats == null) {
      setError("Enter a BTC amount with up to 8 decimal places.");
      return;
    }
    try {
      await onSave({ name: name.trim(), sats, notes: notes.trim(), retirement });
      onClose();
    } catch {
      setError("Could not save wallet.");
    }
  };

  const onConfirmDelete = async () => {
    if (!onDelete) return;
    setError(null);
    try {
      await onDelete();
      onClose();
    } catch {
      setError("Could not delete wallet.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="btc-wallet-sheet-title"
    >
      <div className="card w-full max-w-md p-5">
        <h2 id="btc-wallet-sheet-title" className="page-title text-xl">
          {isEdit ? "Update Wallet" : "Add Bitcoin Wallet"}
        </h2>
        <p className="text-sm text-muted mt-1">
          Amount only — no addresses, xpubs, or keys are stored.
        </p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="btc-wallet-name">
              Wallet name
            </label>
            <input
              id="btc-wallet-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Cold storage"
              autoFocus={!isEdit}
            />
          </div>
          <div>
            <label className="label" htmlFor="btc-wallet-amount">
              Amount (BTC)
            </label>
            <input
              id="btc-wallet-amount"
              className="input font-mono"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00000000"
              autoFocus={isEdit}
            />
            {sats != null && usdPerBtc != null ? (
              <p className="text-xs text-muted mt-1">
                ≈ {formatUsd(satsToUsd(sats, usdPerBtc))} ·{" "}
                {sats.toLocaleString("en-US")} sats
              </p>
            ) : null}
          </div>
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={retirement}
              onChange={(e) => setRetirement(e.target.checked)}
            />
            <span>
              Retirement account (e.g. bitcoin IRA)
              <span className="block text-xs text-muted">
                Still counted once, under Bitcoin, and labeled as retirement.
              </span>
            </span>
          </label>
          <div>
            <label className="label" htmlFor="btc-wallet-notes">
              Notes
            </label>
            <input
              id="btc-wallet-notes"
              className="input"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Optional"
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
                <p className="text-sm">Delete &ldquo;{wallet?.name}&rdquo;?</p>
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
                Delete wallet
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
              disabled={saving || !name.trim() || sats == null}
            >
              {saving ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
