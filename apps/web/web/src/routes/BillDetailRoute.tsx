import { useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { BillSheet, type BillSheetInput } from "../components/BillSheet";
import { BillStatementAttachments } from "../components/bills/BillStatementAttachments";
import { PayBillDialog } from "../components/bills/PayBillDialog";
import {
  annualTotalPaidSoFar,
  canSkipInterval,
  effectiveAmountDue,
  formatDueCountdown,
  frequencyLabel,
  generalCategoryForBill,
  GENERAL_CATEGORY_LABELS,
  isPaidForCurrentCycle,
  nextDueDateMillis,
  isPastDue,
  subcategoryLabel,
  effectiveSubcategory,
} from "../lib/bill";
import { useBankAccountsData } from "../lib/bankAccountsData";
import { useBillersData } from "../lib/billersData";
import { useBillsData } from "../lib/billsData";
import { useDebtData } from "../lib/debtData";
import { formatUsd } from "../lib/format";
import { useRecordBillPayment } from "../lib/useBillPayment";
import { useSaveBill } from "../lib/useSaveBill";

function formatDate(ms: number): string {
  if (ms <= 0) return "—";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(ms));
}

export function BillDetailRoute() {
  const { billId } = useParams<{ billId: string }>();
  const navigate = useNavigate();
  const {
    getBillById,
    loading,
    error,
    saving,
    deleteBill,
    cancelSubscription,
    reactivateSubscription,
    skipInterval,
    attachStatement,
    togglePaid,
  } = useBillsData();
  const { billers } = useBillersData();
  const { accounts: bankAccounts } = useBankAccountsData();
  const { accounts: creditAccounts } = useDebtData();
  const recordBillPayment = useRecordBillPayment();
  const saveBillWithBiller = useSaveBill();

  const item = billId ? getBillById(billId) : undefined;
  const bill = item?.bill;

  const linkedAccount = useMemo(
    () =>
      bill?.linkedCreditAccountId
        ? creditAccounts.find((a) => a.id === bill.linkedCreditAccountId)
        : undefined,
    [bill, creditAccounts],
  );

  const payFromLabel = useMemo(() => {
    if (!bill) return null;
    if (bill.payFromBankAccountId) {
      return bankAccounts.find((a) => a.id === bill.payFromBankAccountId)?.name;
    }
    if (bill.payFromCreditAccountId) {
      return creditAccounts.find((a) => a.id === bill.payFromCreditAccountId)?.name;
    }
    return null;
  }, [bill, bankAccounts, creditAccounts]);

  const [showEdit, setShowEdit] = useState(false);
  const [showPay, setShowPay] = useState(false);
  const [attaching, setAttaching] = useState(false);

  if (!billId) return <Navigate to="/app/bills" replace />;
  if (!loading && !item) return <Navigate to="/app/bills" replace />;
  if (!bill || !item) {
    return <p className="text-sm text-muted">Loading bill…</p>;
  }

  const now = Date.now();
  const pastDue = isPastDue(bill, now);
  const paidCycle = isPaidForCurrentCycle(bill, now);
  const nextDue = nextDueDateMillis(bill, now);

  const onSaveEdit = async (input: BillSheetInput) => {
    await saveBillWithBiller(input, item);
    setShowEdit(false);
  };

  const onDelete = async () => {
    const message = bill.linkedCreditAccountId
      ? `Delete "${bill.name}"? This bill is linked to a debt account.`
      : `Delete "${bill.name}"?`;
    if (!window.confirm(message)) return;
    await deleteBill(item);
    navigate("/app/bills", { replace: true });
  };

  const onPay = async (
    amount: number,
    newBalance?: number,
    paymentDate?: number,
  ) => {
    await recordBillPayment(item, amount, newBalance, paymentDate);
  };

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2">
        <Link to="/app/bills" className="btn-ghost text-sm">
          ← Bills
        </Link>
      </div>

      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="page-title">{bill.name}</h1>
          <p className="text-sm text-muted mt-1">
            {GENERAL_CATEGORY_LABELS[generalCategoryForBill(bill)]} ·{" "}
            {subcategoryLabel(effectiveSubcategory(bill))} ·{" "}
            {frequencyLabel(bill.frequency)}
          </p>
        </div>
        <p className="money text-2xl shrink-0">
          {formatUsd(effectiveAmountDue(bill))}
        </p>
      </div>

      {error ? (
        <div className="card-quiet p-4 text-sm text-error" role="alert">
          {error}
        </div>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {pastDue && !paidCycle ? (
          <span className="badge-error text-xs px-2 py-0.5 rounded-pill font-medium">
            Past due
          </span>
        ) : null}
        {paidCycle ? (
          <span className="badge-success text-xs px-2 py-0.5 rounded-pill font-medium">
            Paid this cycle
          </span>
        ) : (
          <span className="text-xs px-2 py-0.5 rounded-pill bg-surface-variant text-muted">
            {formatDueCountdown(bill, now)}
          </span>
        )}
        {bill.autoPay ? (
          <span className="badge-autopay text-xs px-2 py-0.5 rounded-pill">
            Autopay
          </span>
        ) : null}
        {bill.isCancelled ? (
          <span className="text-xs px-2 py-0.5 rounded-pill bg-surface-variant text-muted">
            Cancelled
          </span>
        ) : null}
        {item.source === "CYPHERLOG" ? (
          <span className="text-xs px-2 py-0.5 rounded-pill bg-surface-variant text-muted">
            CypherLog
          </span>
        ) : null}
      </div>

      <section className="card p-4 space-y-3">
        <h2 className="font-medium">Details</h2>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-muted">Due day</dt>
          <dd>{bill.dueDay ?? 1}</dd>
          <dt className="text-muted">Next due</dt>
          <dd>{nextDue != null ? formatDate(nextDue) : "—"}</dd>
          {bill.billerName ? (
            <>
              <dt className="text-muted">Biller</dt>
              <dd>{bill.billerName}</dd>
            </>
          ) : null}
          {payFromLabel ? (
            <>
              <dt className="text-muted">Pay from</dt>
              <dd>{payFromLabel}</dd>
            </>
          ) : null}
          {linkedAccount ? (
            <>
              <dt className="text-muted">Linked debt</dt>
              <dd>
                <Link
                  to={`/app/debt/${linkedAccount.id}`}
                  className="text-primary underline"
                >
                  {linkedAccount.name}
                </Link>
              </dd>
            </>
          ) : null}
          <dt className="text-muted">Paid YTD</dt>
          <dd className="money">{formatUsd(annualTotalPaidSoFar(bill, now))}</dd>
        </dl>
        {bill.notes ? (
          <p className="text-sm text-muted border-t border-border pt-3">
            {bill.notes}
          </p>
        ) : null}
      </section>

      {bill.creditCardDetails ? (
        <section className="card p-4 space-y-2">
          <h2 className="font-medium">Credit Card</h2>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            <dt className="text-muted">Balance</dt>
            <dd className="money">
              {formatUsd(bill.creditCardDetails.currentBalance ?? 0)}
            </dd>
            <dt className="text-muted">APR</dt>
            <dd>
              {((bill.creditCardDetails.apr ?? 0) * 100).toFixed(2)}%
            </dd>
            <dt className="text-muted">Minimum due</dt>
            <dd className="money">{formatUsd(effectiveAmountDue(bill))}</dd>
          </dl>
        </section>
      ) : null}

      <section className="card p-4">
        <BillStatementAttachments
          bill={bill}
          attaching={attaching}
          onAttach={async (file) => {
            setAttaching(true);
            try {
              await attachStatement(item, file);
            } finally {
              setAttaching(false);
            }
          }}
        />
      </section>

      {(bill.paymentHistory ?? []).length > 0 ? (
        <section className="card p-4">
          <h2 className="font-medium mb-3">Payment History</h2>
          <ul className="space-y-2">
            {[...(bill.paymentHistory ?? [])]
              .sort((a, b) => b.date - a.date)
              .map((p, i) => (
                <li
                  key={`${p.date}-${i}`}
                  className="flex justify-between text-sm border-b border-border pb-2 last:border-0"
                >
                  <span className="text-muted">{formatDate(p.date)}</span>
                  <span className="money">{formatUsd(p.amount)}</span>
                </li>
              ))}
          </ul>
        </section>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {!bill.isCancelled ? (
          paidCycle ? (
            <button
              type="button"
              className="btn-ghost"
              disabled={saving}
              onClick={() => void togglePaid(item)}
            >
              Mark unpaid
            </button>
          ) : (
            <button
              type="button"
              className="btn-primary"
              disabled={saving}
              onClick={() => setShowPay(true)}
            >
              Mark paid
            </button>
          )
        ) : null}
        <button
          type="button"
          className="btn-ghost"
          disabled={saving}
          onClick={() => setShowEdit(true)}
        >
          Edit
        </button>
        {canSkipInterval(bill) && !bill.isCancelled ? (
          <button
            type="button"
            className="btn-ghost"
            disabled={saving}
            onClick={() => void skipInterval(item)}
          >
            Skip interval
          </button>
        ) : null}
        {bill.isCancelled ? (
          <button
            type="button"
            className="btn-ghost"
            disabled={saving}
            onClick={() => void reactivateSubscription(item)}
          >
            Reactivate
          </button>
        ) : bill.isRecurring !== false ? (
          <button
            type="button"
            className="btn-ghost text-error"
            disabled={saving}
            onClick={() => {
              if (window.confirm(`Cancel subscription "${bill.name}"?`)) {
                void cancelSubscription(item);
              }
            }}
          >
            Cancel subscription
          </button>
        ) : null}
        <button
          type="button"
          className="btn-ghost text-error"
          disabled={saving}
          onClick={() => void onDelete()}
        >
          Delete
        </button>
      </div>

      <BillSheet
        open={showEdit}
        editing={item}
        billers={billers}
        bankAccounts={bankAccounts}
        creditAccounts={creditAccounts}
        onClose={() => setShowEdit(false)}
        onSave={onSaveEdit}
        saving={saving}
      />

      <PayBillDialog
        item={item}
        open={showPay}
        onClose={() => setShowPay(false)}
        onConfirm={onPay}
        saving={saving}
      />
    </div>
  );
}
