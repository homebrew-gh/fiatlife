import { useEffect, useState } from "react";
import {
  effectiveAmountDue,
  formatIsoDate,
  minimumDue,
  parseIsoDate,
  type CreditAccount,
} from "../../lib/creditAccount";
import { formatUsd } from "../../lib/format";

export type StatementUpdateInput = {
  statementBalance: number;
  statementBalanceAsOfMillis: number;
  statementAmountDue: number | null;
  dueDay: number;
  paymentAmount: number;
  balanceAfterPayment: number;
};

function suggestedDueDate(dueDay: number): string {
  const now = new Date();
  const dateForMonth = (monthOffset: number) => {
    const year = now.getFullYear();
    const month = now.getMonth() + monthOffset;
    const lastDay = new Date(year, month + 1, 0).getDate();
    return new Date(year, month, Math.min(Math.max(1, dueDay), lastDay));
  };
  let due = dateForMonth(0);
  if (due.getTime() < now.getTime()) due = dateForMonth(1);
  return formatIsoDate(due.getTime());
}

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
  onUpdate: (
    accountId: string,
    input: StatementUpdateInput,
  ) => Promise<void>;
  saving: boolean;
}) {
  const [statementBalance, setStatementBalance] = useState("");
  const [statementDate, setStatementDate] = useState("");
  const [amountDue, setAmountDue] = useState("");
  const [useFormula, setUseFormula] = useState(false);
  const [dueDate, setDueDate] = useState("");
  const [paymentAmount, setPaymentAmount] = useState("");
  const [balanceAfterPayment, setBalanceAfterPayment] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !account) return;
    setStatementBalance(
      account.currentBalance > 0 ? String(account.currentBalance) : "",
    );
    setStatementDate(
      formatIsoDate(account.statementBalanceAsOfMillis ?? Date.now()),
    );
    setAmountDue(String(effectiveAmountDue(account)));
    setUseFormula(account.statementAmountDue == null);
    setDueDate(suggestedDueDate(account.dueDay));
    setPaymentAmount("");
    setBalanceAfterPayment(
      account.currentBalance > 0 ? String(account.currentBalance) : "0",
    );
    setError(null);
  }, [open, account]);

  if (!open || !account) return null;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const parsedBalance = Number.parseFloat(statementBalance);
    const parsedStatementDate = parseIsoDate(statementDate);
    const parsedAmountDue = useFormula ? null : Number.parseFloat(amountDue);
    const parsedDueDate = parseIsoDate(dueDate);
    const parsedPayment = Number.parseFloat(paymentAmount) || 0;
    const parsedAfter = Number.parseFloat(balanceAfterPayment);
    if (!Number.isFinite(parsedBalance) || parsedBalance < 0) {
      setError("Enter a valid statement balance.");
      return;
    }
    if (parsedStatementDate == null || parsedDueDate == null) {
      setError("Enter valid statement and due dates.");
      return;
    }
    if (
      parsedAmountDue != null &&
      (!Number.isFinite(parsedAmountDue) || parsedAmountDue < 0)
    ) {
      setError("Enter a valid amount due, or use the minimum formula.");
      return;
    }
    if (!Number.isFinite(parsedAfter) || parsedAfter < 0) {
      setError("Enter a valid balance after payment.");
      return;
    }
    try {
      await onUpdate(account.id, {
        statementBalance: parsedBalance,
        statementBalanceAsOfMillis: parsedStatementDate,
        statementAmountDue: parsedAmountDue,
        dueDay: new Date(parsedDueDate).getDate(),
        paymentAmount: Math.max(0, parsedPayment),
        balanceAfterPayment: parsedAfter,
      });
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
          Update Statement
        </h2>
        <p className="text-sm text-muted mt-1">{account.name}</p>
        <p className="text-sm text-muted">
          Current account balance: {formatUsd(account.currentBalance)}
        </p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="statement-balance">
              Statement balance
            </label>
            <input
              id="statement-balance"
              className="input money"
              inputMode="decimal"
              value={statementBalance}
              onChange={(e) => {
                setStatementBalance(e.target.value);
                if (!paymentAmount.trim()) setBalanceAfterPayment(e.target.value);
              }}
              placeholder="0.00"
              autoFocus
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="statement-date">
                Statement date
              </label>
              <input
                id="statement-date"
                className="input"
                type="date"
                value={statementDate}
                onChange={(e) => setStatementDate(e.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="statement-due-date">
                Due date
              </label>
              <input
                id="statement-due-date"
                className="input"
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="statement-amount-due">
              Amount due this cycle
            </label>
            <input
              id="statement-amount-due"
              className="input money"
              inputMode="decimal"
              value={useFormula ? String(minimumDue(account)) : amountDue}
              onChange={(e) => {
                setUseFormula(false);
                setAmountDue(e.target.value);
              }}
              disabled={useFormula}
            />
            <label className="mt-2 flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={useFormula}
                onChange={(e) => {
                  setUseFormula(e.target.checked);
                  if (e.target.checked) {
                    setAmountDue(String(minimumDue(account)));
                  }
                }}
              />
              Use minimum-payment formula ({formatUsd(minimumDue(account))})
            </label>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="statement-payment">
                Payment made now
              </label>
              <input
                id="statement-payment"
                className="input money"
                inputMode="decimal"
                value={paymentAmount}
                onChange={(e) => {
                  setPaymentAmount(e.target.value);
                  const balance = Number.parseFloat(statementBalance) || 0;
                  const payment = Number.parseFloat(e.target.value) || 0;
                  setBalanceAfterPayment(String(Math.max(0, balance - payment)));
                }}
                placeholder="Optional"
              />
            </div>
            <div>
              <label className="label" htmlFor="statement-after">
                Balance after payment
              </label>
              <input
                id="statement-after"
                className="input money"
                inputMode="decimal"
                value={balanceAfterPayment}
                onChange={(e) => setBalanceAfterPayment(e.target.value)}
              />
            </div>
          </div>
          {paymentAmount.trim() ? (
            <p className="notice-panel p-3 text-sm">
              Records a {formatUsd(Number.parseFloat(paymentAmount) || 0)} payment
              and sets the account balance to{" "}
              {formatUsd(Number.parseFloat(balanceAfterPayment) || 0)}.
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
              {saving ? "Saving…" : "Update"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
