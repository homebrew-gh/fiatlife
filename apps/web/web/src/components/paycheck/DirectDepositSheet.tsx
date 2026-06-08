import { useEffect, useState } from "react";
import type { DirectDeposit } from "../../lib/salary";

function emptyDeposit(sortOrder: number, isFirst: boolean): DirectDeposit {
  return {
    id: crypto.randomUUID(),
    accountName: "",
    bankName: "",
    amount: 0,
    isPercentage: false,
    isRemainder: isFirst,
    sortOrder,
  };
}

export function DirectDepositSheet({
  open,
  deposit,
  depositCount,
  onClose,
  onSave,
}: {
  open: boolean;
  deposit: DirectDeposit | null;
  depositCount: number;
  onClose: () => void;
  onSave: (deposit: DirectDeposit) => void;
}) {
  const [draft, setDraft] = useState<DirectDeposit>(() =>
    deposit ?? emptyDeposit(depositCount, depositCount === 0),
  );

  useEffect(() => {
    if (!open) return;
    setDraft(deposit ?? emptyDeposit(depositCount, depositCount === 0));
  }, [open, deposit, depositCount]);

  if (!open) return null;

  const isNew = deposit == null;

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="direct-deposit-title"
      onClick={onClose}
    >
      <div
        className="card w-full max-w-md p-5 max-h-[92vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="direct-deposit-title" className="page-title text-xl">
          {isNew ? "Add direct deposit" : "Edit direct deposit"}
        </h2>
        <p className="text-muted text-sm mt-1">
          Split take-home pay by fixed amount, percentage, or remainder account.
        </p>

        <div className="mt-4 space-y-3">
          <div>
            <label className="label" htmlFor="deposit-account-name">
              Account nickname
            </label>
            <input
              id="deposit-account-name"
              className="input"
              value={draft.accountName}
              onChange={(e) =>
                setDraft((d) => ({ ...d, accountName: e.target.value }))
              }
              placeholder="e.g. Main checking"
              autoFocus
            />
          </div>
          <div>
            <label className="label" htmlFor="deposit-bank-name">
              Bank name
            </label>
            <input
              id="deposit-bank-name"
              className="input"
              value={draft.bankName}
              onChange={(e) =>
                setDraft((d) => ({ ...d, bankName: e.target.value }))
              }
              placeholder="Optional"
            />
          </div>

          <label className="flex items-center gap-2 text-sm text-muted">
            <input
              type="checkbox"
              checked={draft.isRemainder}
              onChange={(e) =>
                setDraft((d) => ({ ...d, isRemainder: e.target.checked }))
              }
            />
            Remainder account (gets whatever is left)
          </label>

          {!draft.isRemainder ? (
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="label">Amount</label>
                <DecimalInput
                  className="input money"
                  value={draft.amount}
                  onChange={(amount) => setDraft((d) => ({ ...d, amount }))}
                />
              </div>
              <label className="flex items-end gap-2 text-sm text-muted pb-2">
                <input
                  type="checkbox"
                  checked={draft.isPercentage}
                  onChange={(e) =>
                    setDraft((d) => ({
                      ...d,
                      isPercentage: e.target.checked,
                    }))
                  }
                />
                % of net
              </label>
            </div>
          ) : null}
        </div>

        <div className="mt-6 flex gap-2 justify-end">
          <button type="button" className="btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={() => onSave(draft)}
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
}

function decimalDisplayValue(n: number): string {
  return n === 0 ? "" : String(n);
}

function DecimalInput({
  value,
  onChange,
  className,
}: {
  value: number;
  onChange: (n: number) => void;
  className?: string;
}) {
  const [text, setText] = useState(() => decimalDisplayValue(value));

  useEffect(() => {
    setText((prev) => {
      const parsed = Number.parseFloat(prev);
      if (prev !== "" && prev !== "." && Number.isFinite(parsed) && parsed === value) {
        return prev;
      }
      return decimalDisplayValue(value);
    });
  }, [value]);

  return (
    <input
      className={className}
      inputMode="decimal"
      value={text}
      onChange={(e) => {
        const raw = e.target.value;
        if (raw !== "" && !/^\d*\.?\d*$/.test(raw)) return;
        setText(raw);
        if (raw === "" || raw === ".") {
          onChange(0);
          return;
        }
        const n = Number.parseFloat(raw);
        if (Number.isFinite(n)) onChange(n);
      }}
      onBlur={() => {
        const n = Number.parseFloat(text);
        setText(Number.isFinite(n) ? String(n) : "");
        if (Number.isFinite(n)) onChange(n);
        else onChange(0);
      }}
    />
  );
}
