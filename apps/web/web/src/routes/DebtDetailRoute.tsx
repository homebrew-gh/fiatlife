import { useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import clsx from "clsx";
import { CreditAccountSheet } from "../components/debt/CreditAccountSheet";
import { PaymentHistoryList } from "../components/debt/PaymentHistoryList";
import { StatementAttachments } from "../components/debt/StatementAttachments";
import { MortgageScheduleSection } from "../components/debt/MortgageScheduleSection";
import { UpdateBalanceSheet } from "../components/debt/UpdateBalanceSheet";
import { findLinkedBill } from "../lib/creditBillLink";
import {
  CREDIT_ACCOUNT_TYPE_LABELS,
  effectiveMonthlyPayment,
  formatIsoDate,
  isRevolvingType,
  utilizationPercent,
  type CreditAccount,
} from "../lib/creditAccount";
import {
  formatMonths,
  formatPayoffDate,
  monthlyInterest,
  projectAccountPayoff,
} from "../lib/debtPayoff";
import { useBillsData } from "../lib/billsData";
import { useDebtData } from "../lib/debtData";
import { formatUsd } from "../lib/format";

export function DebtDetailRoute() {
  const { accountId } = useParams<{ accountId: string }>();
  const navigate = useNavigate();
  const { bills } = useBillsData();
  const {
    getAccountById,
    loading,
    error,
    saving,
    saveAccount,
    updateBalance,
    deleteAccount,
    attachStatement,
  } = useDebtData();

  const account = accountId ? getAccountById(accountId) : undefined;
  const linkedBill = useMemo(
    () => (account ? findLinkedBill(account, bills) : undefined),
    [account, bills],
  );

  const [showEdit, setShowEdit] = useState(false);
  const [showBalance, setShowBalance] = useState(false);
  const [attaching, setAttaching] = useState(false);

  if (!accountId) return <Navigate to="/app/debt" replace />;

  if (!loading && !account) {
    return <Navigate to="/app/debt" replace />;
  }

  if (!account) {
    return <p className="text-sm text-muted">Loading account…</p>;
  }

  const util = utilizationPercent(account);
  const monthly = effectiveMonthlyPayment(account);

  const onDelete = async () => {
    if (
      !window.confirm(`Delete "${account.name}"? Linked bills will be removed too.`)
    ) {
      return;
    }
    await deleteAccount(account);
    navigate("/app/debt", { replace: true });
  };

  const onSaveEdit = async (
    input: Omit<CreditAccount, "id" | "createdAt" | "updatedAt">,
  ) => {
    await saveAccount({ ...account, ...input });
    setShowEdit(false);
  };

  const onAttach = async (file: File) => {
    setAttaching(true);
    try {
      await attachStatement(account, file);
    } finally {
      setAttaching(false);
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-3">
        <Link to="/app/debt" className="btn-ghost text-sm py-1.5 shrink-0">
          ← Debt
        </Link>
        <div className="min-w-0 flex-1">
          <h1 className="page-title truncate">{account.name}</h1>
          <p className="text-sm text-muted mt-1">
            {CREDIT_ACCOUNT_TYPE_LABELS[account.type]}
            {account.institution ? ` · ${account.institution}` : ""}
          </p>
        </div>
        <div className="flex gap-1 shrink-0">
          <button
            type="button"
            className="btn-ghost text-sm py-1.5"
            onClick={() => setShowEdit(true)}
          >
            Edit
          </button>
          <button
            type="button"
            className="btn-ghost text-sm py-1.5 text-error"
            onClick={() => void onDelete()}
            disabled={saving}
          >
            Delete
          </button>
        </div>
      </div>

      {error ? (
        <p className="notice-error text-sm" role="alert">
          {error}
        </p>
      ) : null}

      <section className="card p-6 bg-primary-container text-on-primary-container text-center">
        <p className="text-sm opacity-80">Current balance</p>
        <p className="font-mono text-3xl font-bold mt-2">
          {formatUsd(account.currentBalance)}
        </p>
        <p className="text-sm opacity-80 mt-2">
          {formatUsd(monthly)}/mo · Due day {account.dueDay}
        </p>
        {util != null ? (
          <div className="mt-4 max-w-xs mx-auto">
            <div className="h-2 rounded-full bg-on-primary-container/20 overflow-hidden">
              <div
                className={clsx(
                  "h-full rounded-full",
                  util >= 90 ? "bg-error" : util >= 50 ? "bg-warn" : "bg-success",
                )}
                style={{ width: `${util}%` }}
              />
            </div>
            <p className="text-xs opacity-80 mt-1">
              {util.toFixed(0)}% of {formatUsd(account.creditLimit)} limit
            </p>
          </div>
        ) : null}
        <button
          type="button"
          className="btn-ghost mt-4 text-sm"
          onClick={() => setShowBalance(true)}
        >
          Update balance
        </button>
      </section>

      {account.type === "MORTGAGE" ? (
        <MortgageScheduleSection account={account} />
      ) : (
        <PayoffSection account={account} />
      )}

      {linkedBill ? (
        <section className="card p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <h2 className="section-title">Tracked in Bills</h2>
            <Link to="/app/bills" className="text-sm text-accent">
              Open Bills
            </Link>
          </div>
          <div className="rounded-lg bg-surface-variant/60 p-3">
            <p className="font-medium text-body">{linkedBill.bill.name}</p>
            <p className="text-sm text-muted mt-1">
              {formatUsd(linkedBill.bill.amount)} · {linkedBill.bill.frequency.toLowerCase()}
            </p>
          </div>
        </section>
      ) : null}

      <section className="card p-4 space-y-3">
        <h2 className="section-title">Payment History</h2>
        <PaymentHistoryList payments={linkedBill?.bill.paymentHistory ?? []} />
      </section>

      <section className="card p-4 space-y-3">
        <h2 className="section-title">Overview</h2>
        <dl className="space-y-2 text-sm">
          {account.apr > 0 ? (
            <div className="flex justify-between gap-4">
              <dt className="text-muted">APR</dt>
              <dd>{(account.apr * 100).toFixed(2)}%</dd>
            </div>
          ) : null}
          {isRevolvingType(account.type) && account.creditLimit > 0 ? (
            <>
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Credit limit</dt>
                <dd className="font-mono">{formatUsd(account.creditLimit)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted">Available</dt>
                <dd className="font-mono">
                  {formatUsd(
                    Math.max(0, account.creditLimit - account.currentBalance),
                  )}
                </dd>
              </div>
            </>
          ) : null}
          {account.type === "MORTGAGE" && account.originalPrincipal > 0 ? (
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Loan amount</dt>
              <dd className="font-mono">{formatUsd(account.originalPrincipal)}</dd>
            </div>
          ) : null}
          {account.type === "MORTGAGE" && account.startDate ? (
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Loan start</dt>
              <dd>{formatIsoDate(account.startDate)}</dd>
            </div>
          ) : null}
          {account.termMonths ? (
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Term</dt>
              <dd>
                {account.type === "MORTGAGE"
                  ? `${Math.round((account.termMonths / 12) * 10) / 10} years`
                  : `${account.termMonths} months`}
              </dd>
            </div>
          ) : null}
          <div className="flex justify-between gap-4">
            <dt className="text-muted">
              {isRevolvingType(account.type) ? "Minimum due" : "Monthly payment"}
            </dt>
            <dd className="font-mono">{formatUsd(monthly)}</dd>
          </div>
          {account.accountNumberLast4 ? (
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Account</dt>
              <dd>····{account.accountNumberLast4}</dd>
            </div>
          ) : null}
          {account.notes ? (
            <div>
              <dt className="text-muted">Notes</dt>
              <dd className="mt-1 whitespace-pre-wrap">{account.notes}</dd>
            </div>
          ) : null}
        </dl>
      </section>

      <StatementAttachments
        account={account}
        onAttach={onAttach}
        attaching={attaching}
      />

      <CreditAccountSheet
        open={showEdit}
        account={account}
        onClose={() => setShowEdit(false)}
        onSave={onSaveEdit}
        saving={saving}
      />

      <UpdateBalanceSheet
        open={showBalance}
        account={account}
        onClose={() => setShowBalance(false)}
        onUpdate={updateBalance}
        saving={saving}
      />
    </div>
  );
}

function PayoffSection({ account }: { account: CreditAccount }) {
  const interest = monthlyInterest(account);
  const payment = effectiveMonthlyPayment(account);

  if (account.currentBalance <= 0 || account.apr <= 0) return null;

  const proj = projectAccountPayoff(account);

  return (
    <section className="card p-4 space-y-3">
      <h2 className="section-title">Payoff Projection</h2>

      {!proj.feasible ? (
        <div className="notice-error text-sm" role="alert">
          <p className="font-medium">Minimum payment trap</p>
          <p className="mt-1">
            At {formatUsd(payment)}/mo this balance won&apos;t be paid off —
            roughly {formatUsd(interest)} of that goes to interest each month.
            Increase the payment to make progress.
          </p>
        </div>
      ) : null}

      <dl className="space-y-2 text-sm">
        <div className="flex justify-between gap-4">
          <dt className="text-muted">Interest this month</dt>
          <dd className="font-mono">{formatUsd(interest)}</dd>
        </div>
        {proj.feasible ? (
          <>
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Debt-free</dt>
              <dd>
                {proj.payoffDateMs != null
                  ? `${formatPayoffDate(proj.payoffDateMs)} · ${formatMonths(proj.months)}`
                  : "—"}
              </dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-muted">Total interest</dt>
              <dd className="font-mono">{formatUsd(proj.totalInterest)}</dd>
            </div>
          </>
        ) : null}
      </dl>
      <p className="text-xs text-muted">
        Estimated at the current {formatUsd(payment)}/mo payment.
      </p>
    </section>
  );
}
