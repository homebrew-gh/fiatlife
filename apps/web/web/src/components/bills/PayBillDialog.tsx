import { useEffect, useState } from "react";
import {
  effectiveAmountDue,
  isCreditOrLoan,
  type BillWithSource,
} from "../../lib/bill";
import { formatUsd } from "../../lib/format";

export type PayBillMode = "MINIMUM" | "FULL" | "CUSTOM";

export function PayBillDialog({
  item,
  open,
  onClose,
  onConfirm,
  saving,
}: {
  item: BillWithSource | null;
  open: boolean;
  onClose: () => void;
  onConfirm: (amount: number, newBalance?: number) => Promise<void>;
  saving: boolean;
}) {
  const [mode, setMode] = useState<PayBillMode>("MINIMUM");
  const [customAmount, setCustomAmount] = useState("");
  const [newBalance, setNewBalance] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !item) return;
    setMode("MINIMUM");
    setCustomAmount("");
    const balance = item.bill.creditCardDetails?.currentBalance;
    setNewBalance(balance != null ? String(balance) : "");
    setError(null);
  }, [open, item]);

  if (!open || !item) return null;

  const bill = item.bill;
  const minimum = effectiveAmountDue(bill);
  const fullBalance = bill.creditCardDetails?.currentBalance ?? bill.amount;
  const showBalance = isCreditOrLoan(bill) && bill.creditCardDetails != null;

  const resolvedAmount = (): number => {
    switch (mode) {
      case "FULL":
        return fullBalance;
      case "CUSTOM":
        return Number.parseFloat(customAmount);
      default:
        return minimum;
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const amount = resolvedAmount();
    if (!Number.isFinite(amount) || amount <= 0) {
      setError("Enter a valid payment amount.");
      return;
    }
    let balance: number | undefined;
    if (showBalance && newBalance.trim()) {
      balance = Number.parseFloat(newBalance);
      if (!Number.isFinite(balance) || balance < 0) {
        setError("Enter a valid new balance.");
        return;
      }
    }
    try {
      await onConfirm(amount, balance);
      onClose();
    } catch {
      setError("Could not record payment.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
    >
      <div className="card w-full max-w-md p-5">
        <h2 className="page-title text-xl">Record Payment</h2>
        <p className="text-sm text-muted mt-1">{bill.name}</p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div className="space-y-2">
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="pay-mode"
                checked={mode === "MINIMUM"}
                onChange={() => setMode("MINIMUM")}
              />
              <span>
                Minimum due ({formatUsd(minimum)})
              </span>
            </label>
            {isCreditOrLoan(bill) ? (
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="pay-mode"
                  checked={mode === "FULL"}
                  onChange={() => setMode("FULL")}
                />
                <span>Pay in full ({formatUsd(fullBalance)})</span>
              </label>
            ) : null}
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="radio"
                name="pay-mode"
                checked={mode === "CUSTOM"}
                onChange={() => setMode("CUSTOM")}
              />
              <span>Custom amount</span>
            </label>
          </div>
          {mode === "CUSTOM" ? (
            <div>
              <label className="label" htmlFor="pay-custom">
                Amount
              </label>
              <input
                id="pay-custom"
                className="input money"
                inputMode="decimal"
                value={customAmount}
                onChange={(e) => setCustomAmount(e.target.value)}
              />
            </div>
          ) : null}
          {showBalance ? (
            <div>
              <label className="label" htmlFor="pay-balance">
                New balance (optional)
              </label>
              <input
                id="pay-balance"
                className="input money"
                inputMode="decimal"
                value={newBalance}
                onChange={(e) => setNewBalance(e.target.value)}
              />
            </div>
          ) : null}
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
              {saving ? "Saving…" : "Record payment"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
