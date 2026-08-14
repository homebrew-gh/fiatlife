import { useEffect, useState } from "react";
import { DateInput } from "../DateInput";
import {
  effectiveAmountDue,
  isCreditOrLoan,
  type BillWithSource,
} from "../../lib/bill";
import { parseDateInput, todayDateInputValue } from "../../lib/dateInput";
import {
  effectiveAmountDue as effectiveAccountAmountDue,
} from "../../lib/creditAccount";
import { useDebtData } from "../../lib/debtData";
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
  onConfirm: (
    amount: number,
    newBalance?: number,
    paymentDate?: number,
  ) => Promise<void>;
  saving: boolean;
}) {
  const { getAccountById } = useDebtData();
  const [mode, setMode] = useState<PayBillMode>("MINIMUM");
  const [customAmount, setCustomAmount] = useState("");
  const [newBalance, setNewBalance] = useState("");
  const [paymentDate, setPaymentDate] = useState(todayDateInputValue);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !item) return;
    setMode("MINIMUM");
    setCustomAmount("");
    setNewBalance("");
    setPaymentDate(todayDateInputValue());
    setError(null);
  }, [open, item]);

  if (!open || !item) return null;

  const bill = item.bill;
  const linkedAccount = bill.linkedCreditAccountId
    ? getAccountById(bill.linkedCreditAccountId)
    : undefined;
  const creditBill = isCreditOrLoan(bill);
  const minimum = linkedAccount
    ? effectiveAccountAmountDue(linkedAccount)
    : effectiveAmountDue(bill);
  const fullBalance =
    linkedAccount?.currentBalance ??
    bill.creditCardDetails?.currentBalance ??
    bill.amount;
  const showBalance =
    creditBill && (linkedAccount != null || bill.creditCardDetails != null);

  const resolvedAmount = (): number => {
    if (!creditBill) return minimum;
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
    const paidAt = parseDateInput(paymentDate);
    if (paidAt == null) {
      setError("Enter a valid payment date.");
      return;
    }
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
      await onConfirm(amount, balance, paidAt);
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
        <h2 className="page-title text-xl">
          {creditBill ? "Record Payment" : "Mark as Paid"}
        </h2>
        <p className="text-sm text-muted mt-1">{bill.name}</p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <DateInput
            label="Payment date"
            value={paymentDate}
            onChange={setPaymentDate}
            required
            disabled={saving}
          />
          {creditBill ? (
            <div className="space-y-2">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="pay-mode"
                  checked={mode === "MINIMUM"}
                  onChange={() => setMode("MINIMUM")}
                />
                <span>Minimum due ({formatUsd(minimum)})</span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="radio"
                  name="pay-mode"
                  checked={mode === "FULL"}
                  onChange={() => setMode("FULL")}
                />
                <span>Pay in full ({formatUsd(fullBalance)})</span>
              </label>
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
          ) : (
            <p className="text-sm text-body">
              Amount: <span className="money font-medium">{formatUsd(minimum)}</span>
            </p>
          )}
          {creditBill && mode === "CUSTOM" ? (
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
                New balance after payment (optional)
              </label>
              <input
                id="pay-balance"
                className="input money"
                inputMode="decimal"
                value={newBalance}
                onChange={(e) => setNewBalance(e.target.value)}
                placeholder={formatUsd(
                  Math.max(0, fullBalance - resolvedAmount()),
                )}
              />
              <p className="text-xs text-muted mt-1">
                Leave blank to subtract the payment from the current account
                balance.
              </p>
            </div>
          ) : null}
          {linkedAccount ? (
            <p className="notice-panel p-3 text-sm">
              Paying this bill updates {linkedAccount.name} in Debt.
            </p>
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
              {saving ? "Saving…" : creditBill ? "Record payment" : "Mark paid"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
