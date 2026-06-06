import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { CreditAccountSheet } from "../../components/debt/CreditAccountSheet";
import {
  CREDIT_ACCOUNT_TYPE_LABELS,
  effectiveMonthlyPayment,
  isRevolvingType,
  summarizeDebt,
  utilizationPercent,
  type CreditAccount,
} from "../../lib/creditAccount";
import {
  formatPayoffDate,
  isMinimumPaymentTrap,
  monthlyInterest,
  summarizeDebtPayoff,
} from "../../lib/debtPayoff";
import { useDebtData } from "../../lib/debtData";
import { formatUsd } from "../../lib/format";

function DebtAccountCard({ account }: { account: CreditAccount }) {
  const util = utilizationPercent(account);
  const monthly = effectiveMonthlyPayment(account);
  const interest = monthlyInterest(account);
  const trap = isMinimumPaymentTrap(account);

  return (
    <Link
      to={`/app/debt/${account.id}`}
      className="card block p-4 hover:ring-1 hover:ring-outline transition-shadow"
    >
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-body truncate">{account.name}</h3>
          <p className="text-sm text-muted">
            {CREDIT_ACCOUNT_TYPE_LABELS[account.type]}
            {account.institution ? ` · ${account.institution}` : ""}
            {account.type === "MORTGAGE" && account.termMonths
              ? ` · ${account.termMonths / 12}yr`
              : ""}
          </p>
          {interest > 0 ? (
            <p className="text-xs text-muted mt-0.5">
              ≈ {formatUsd(interest)}/mo interest
            </p>
          ) : null}
        </div>
        <div className="text-right shrink-0">
          <p className="font-mono font-semibold text-money">
            {formatUsd(account.currentBalance)}
          </p>
          <p className="text-xs text-muted">{formatUsd(monthly)}/mo</p>
        </div>
        <span className="text-muted text-sm shrink-0" aria-hidden>
          ▸
        </span>
      </div>

      {trap ? (
        <p className="mt-2 text-xs text-error flex items-center gap-1">
          <span aria-hidden>⚠</span> Minimum payment barely covers interest
        </p>
      ) : null}

      {util != null ? (
        <div className="mt-3">
          <div className="h-1.5 rounded-full bg-surface-variant overflow-hidden">
            <div
              className={clsx(
                "h-full rounded-full transition-all",
                util >= 90 ? "bg-error" : util >= 50 ? "bg-warn" : "bg-success",
              )}
              style={{ width: `${util}%` }}
            />
          </div>
          <p className="text-xs text-muted mt-1">
            {util.toFixed(0)}% utilized
          </p>
        </div>
      ) : null}
    </Link>
  );
}

export function DebtTab() {
  const navigate = useNavigate();
  const { accounts, loading, error, saving, reload, addAccount } = useDebtData();
  const [showSheet, setShowSheet] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const summary = useMemo(() => summarizeDebt(accounts), [accounts]);
  const payoff = useMemo(() => summarizeDebtPayoff(accounts), [accounts]);
  const hasRevolving = accounts.some((a) => isRevolvingType(a.type));

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await reload();
    } finally {
      setRefreshing(false);
    }
  };

  const onSaveAccount = async (
    input: Omit<CreditAccount, "id" | "createdAt" | "updatedAt">,
  ) => {
    const saved = await addAccount(input);
    setShowSheet(false);
    navigate(`/app/debt/${saved.id}`);
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="page-title">Debt</h1>
          <p className="text-sm text-muted mt-1">
            Credit cards and loans synced with Android via your Nostr relay.
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => void onRefresh()}
            disabled={refreshing || loading}
          >
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
          <button
            type="button"
            className="btn-primary text-sm"
            onClick={() => setShowSheet(true)}
            disabled={saving}
          >
            Add account
          </button>
        </div>
      </div>

      {error ? (
        <p className="notice-error text-sm" role="alert">
          {error}
        </p>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2">
        <Link
          to="/app/debt/planner"
          className="card block p-4 hover:ring-1 hover:ring-outline transition-shadow"
        >
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="font-semibold text-body">Debt Planner</h2>
              <p className="text-sm text-muted mt-1">
                Snowball vs. avalanche payoff with a debt-free date.
              </p>
            </div>
            <span className="text-muted shrink-0" aria-hidden>
              ▸
            </span>
          </div>
        </Link>

        <Link
          to="/app/debt/mortgage-calculator"
          className="card block p-4 hover:ring-1 hover:ring-outline transition-shadow"
        >
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="font-semibold text-body">Mortgage Calculator</h2>
              <p className="text-sm text-muted mt-1">
                Compare down payments, rates, and terms with payment schedules.
              </p>
            </div>
            <span className="text-muted shrink-0" aria-hidden>
              ▸
            </span>
          </div>
        </Link>
      </div>

      {loading ? (
        <p className="text-sm text-muted">Loading accounts…</p>
      ) : (
        <>
          <section className="card p-6 bg-primary-container text-on-primary-container">
            <h2 className="text-center text-sm font-medium opacity-80">
              Debt Summary
            </h2>
            <div className="mt-4 grid grid-cols-2 gap-4 text-center">
              <div>
                <p className="text-sm opacity-70">Total debt</p>
                <p className="font-mono text-xl font-semibold mt-1">
                  {formatUsd(summary.totalDebt)}
                </p>
              </div>
              <div>
                <p className="text-sm opacity-70">Monthly payment</p>
                <p className="font-mono text-xl font-semibold mt-1">
                  {formatUsd(summary.totalMonthlyPayment)}
                </p>
              </div>
            </div>
            {hasRevolving && summary.totalCreditAvailable > 0 ? (
              <div className="mt-4 grid grid-cols-2 gap-4 text-center text-sm">
                <div>
                  <p className="opacity-70">Credit available</p>
                  <p className="font-mono font-semibold mt-1">
                    {formatUsd(
                      summary.totalCreditAvailable - summary.totalCreditUtilized,
                    )}
                  </p>
                </div>
                <div>
                  <p className="opacity-70">Utilization</p>
                  <p className="font-semibold mt-1">
                    {summary.utilizationPercent.toFixed(0)}%
                  </p>
                </div>
              </div>
            ) : null}
            {payoff.hasInterestBearingDebt ? (
              <div className="mt-4 grid grid-cols-2 gap-4 text-center text-sm">
                <div>
                  <p className="opacity-70">Debt-free</p>
                  <p className="font-semibold mt-1">
                    {payoff.allFeasible && payoff.debtFreeDateMs != null
                      ? formatPayoffDate(payoff.debtFreeDateMs)
                      : "Not on track"}
                  </p>
                </div>
                <div>
                  <p className="opacity-70">Projected interest</p>
                  <p className="font-mono font-semibold mt-1">
                    {payoff.allFeasible
                      ? formatUsd(payoff.totalInterest)
                      : `${formatUsd(payoff.monthlyInterest)}/mo`}
                  </p>
                </div>
              </div>
            ) : null}
            {!payoff.allFeasible && payoff.infeasibleCount > 0 ? (
              <p className="text-center text-xs mt-3 text-on-primary-container/90">
                ⚠ {payoff.infeasibleCount} account
                {payoff.infeasibleCount === 1 ? "" : "s"} won&apos;t pay off at
                the current payment.
              </p>
            ) : null}
            <p className="text-center text-sm opacity-70 mt-4">
              {summary.accountCount} account
              {summary.accountCount === 1 ? "" : "s"}
            </p>
          </section>

          {accounts.length === 0 ? (
            <div className="card p-8 text-center">
              <p className="font-medium text-body">No debt accounts yet</p>
              <p className="text-sm text-muted mt-1">
                Add a credit card or loan to start tracking.
              </p>
              <button
                type="button"
                className="btn-primary mt-4"
                onClick={() => setShowSheet(true)}
              >
                Add your first account
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {accounts.map((account) => (
                <DebtAccountCard key={account.id} account={account} />
              ))}
            </div>
          )}
        </>
      )}

      <CreditAccountSheet
        open={showSheet}
        account={null}
        onClose={() => setShowSheet(false)}
        onSave={onSaveAccount}
        saving={saving}
      />
    </div>
  );
}
