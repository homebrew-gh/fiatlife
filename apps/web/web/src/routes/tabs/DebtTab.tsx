import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { CreditAccountSheet } from "../../components/debt/CreditAccountSheet";
import {
  UpdateBalanceSheet,
  type StatementUpdateInput,
} from "../../components/debt/UpdateBalanceSheet";
import {
  EmptyState,
  ErrorBanner,
  HeroCard,
  PageHeader,
} from "../../components/ui";
import {
  CREDIT_ACCOUNT_TYPE_LABELS,
  dueUrgency,
  effectiveMonthlyPayment,
  isPromotionActive,
  isRevolvingType,
  monthsUntilPromotionEnds,
  sortAccountsByUrgency,
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
import { findLinkedBill } from "../../lib/creditBillLink";
import { isPaidForCurrentCycle } from "../../lib/bill";
import { useBillsData } from "../../lib/billsData";
import { useDebtData } from "../../lib/debtData";
import { formatUsd } from "../../lib/format";
import { MortgageGoalsPrompt } from "../../components/debt/MortgageGoalsPrompt";
import { useGoalsData } from "../../lib/goalsData";

function DebtAccountCard({
  account,
  paidThisCycle,
  onUpdateStatement,
}: {
  account: CreditAccount;
  paidThisCycle: boolean;
  onUpdateStatement: () => void;
}) {
  const util = utilizationPercent(account);
  const monthly = effectiveMonthlyPayment(account);
  const interest = monthlyInterest(account);
  const trap = isMinimumPaymentTrap(account);
  const urgency = dueUrgency(account, paidThisCycle);

  return (
    <article className="card p-4">
      <div className="flex items-start gap-3">
        <Link to={`/app/debt/${account.id}`} className="min-w-0 flex-1">
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
          <p
            className={clsx(
              "text-xs mt-1",
              urgency.overdue
                ? "text-error"
                : urgency.days <= 7
                  ? "text-warn"
                  : "text-muted",
            )}
          >
            {urgency.overdue
              ? `${formatUsd(monthly)} overdue · ${urgency.days} day${
                  urgency.days === 1 ? "" : "s"
                }`
              : `${formatUsd(monthly)} due in ${urgency.days} day${
                  urgency.days === 1 ? "" : "s"
                } · day ${account.dueDay}`}
          </p>
          {isPromotionActive(account) ? (
            <span className="badge-success inline-block rounded-pill px-2 py-0.5 text-xs mt-2">
              {((account.promotionalApr ?? 0) * 100).toFixed(2)}% promo ·{" "}
              {monthsUntilPromotionEnds(account)} mo left
            </span>
          ) : null}
        </div>
        </Link>
        <div className="text-right shrink-0">
          <p className="money font-semibold">
            {formatUsd(account.currentBalance)}
          </p>
          <button
            type="button"
            className="btn-ghost text-xs py-1 px-2 mt-2"
            onClick={onUpdateStatement}
          >
            Update
          </button>
        </div>
      </div>

      {trap ? (
        <p className="mt-2 text-xs text-error flex items-center gap-1">
          <span aria-hidden>⚠</span> Minimum payment barely covers interest
        </p>
      ) : null}

      {util != null ? (
        <div className="mt-3">
          <div className="h-1.5 rounded-full bg-surfaceVariant overflow-hidden">
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
    </article>
  );
}

export function DebtTab() {
  const navigate = useNavigate();
  const {
    accounts,
    loading,
    error,
    saving,
    reload,
    addAccount,
    updateStatement,
  } = useDebtData();
  const { bills } = useBillsData();
  const [showSheet, setShowSheet] = useState(false);
  const [statementAccount, setStatementAccount] =
    useState<CreditAccount | null>(null);
  const [goalsPromptAccount, setGoalsPromptAccount] =
    useState<CreditAccount | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const { goals, saveGoal, saving: goalsSaving } = useGoalsData();

  const summary = useMemo(() => summarizeDebt(accounts), [accounts]);
  const payoff = useMemo(() => summarizeDebtPayoff(accounts), [accounts]);
  const hasRevolving = accounts.some((a) => isRevolvingType(a.type));
  const paidThisCycleById = useMemo(() => {
    const paid: Record<string, boolean> = {};
    for (const account of accounts) {
      const linked = findLinkedBill(account, bills);
      paid[account.id] = linked
        ? isPaidForCurrentCycle(linked.bill)
        : false;
    }
    return paid;
  }, [accounts, bills]);
  const urgencySortedAccounts = useMemo(
    () => sortAccountsByUrgency(accounts, paidThisCycleById),
    [accounts, paidThisCycleById],
  );

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
    if (saved.type === "MORTGAGE") {
      setGoalsPromptAccount(saved);
    } else {
      navigate(`/app/debt/${saved.id}`);
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Debt"
        description="Credit cards and loans synced with Android via your Nostr relay."
        refreshing={refreshing}
        onRefresh={() => void onRefresh()}
        refreshDisabled={loading}
        actions={
          <button
            type="button"
            className="btn-primary text-sm"
            onClick={() => setShowSheet(true)}
            disabled={saving}
          >
            Add account
          </button>
        }
      />

      {error ? <ErrorBanner message={error} /> : null}

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
          <HeroCard className="p-6">
            <h2 className="text-center text-sm font-medium opacity-80">
              Debt Summary
            </h2>
            <div className="mt-4 grid grid-cols-2 gap-4 text-center">
              <div>
                <p className="text-sm opacity-70">Total debt</p>
                <p className="money text-xl mt-1">
                  {formatUsd(summary.totalDebt)}
                </p>
              </div>
              <div>
                <p className="text-sm opacity-70">Monthly payment</p>
                <p className="money text-xl mt-1">
                  {formatUsd(summary.totalMonthlyPayment)}
                </p>
              </div>
            </div>
            {hasRevolving && summary.totalCreditAvailable > 0 ? (
              <div className="mt-4 grid grid-cols-2 gap-4 text-center text-sm">
                <div>
                  <p className="opacity-70">Credit available</p>
                  <p className="money mt-1">
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
                  <p className="money mt-1">
                    {payoff.allFeasible
                      ? formatUsd(payoff.totalInterest)
                      : `${formatUsd(payoff.monthlyInterest)}/mo`}
                  </p>
                </div>
              </div>
            ) : null}
            {!payoff.allFeasible && payoff.infeasibleCount > 0 ? (
              <p className="text-center text-xs mt-3 text-error">
                ⚠ {payoff.infeasibleCount} account
                {payoff.infeasibleCount === 1 ? "" : "s"} won&apos;t pay off at
                the current payment.
              </p>
            ) : null}
            <p className="text-center text-sm opacity-70 mt-4">
              {summary.accountCount} account
              {summary.accountCount === 1 ? "" : "s"}
            </p>
          </HeroCard>

          {accounts.length === 0 ? (
            <EmptyState
              title="No debt accounts yet"
              description="Add credit cards and loans here. FiatLife creates a Bills reminder for each due date so you can pay without re-entering the balance."
              action={
                <button
                  type="button"
                  className="btn-primary"
                  onClick={() => setShowSheet(true)}
                >
                  Add your first account
                </button>
              }
            />
          ) : (
            <div className="space-y-3">
              {urgencySortedAccounts.map((account) => (
                <DebtAccountCard
                  key={account.id}
                  account={account}
                  paidThisCycle={paidThisCycleById[account.id] === true}
                  onUpdateStatement={() => setStatementAccount(account)}
                />
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
      <UpdateBalanceSheet
        open={statementAccount != null}
        account={statementAccount}
        onClose={() => setStatementAccount(null)}
        onUpdate={async (accountId: string, input: StatementUpdateInput) => {
          await updateStatement(accountId, input);
          setStatementAccount(null);
        }}
        saving={saving}
      />
      {goalsPromptAccount ? (
        <MortgageGoalsPrompt
          account={goalsPromptAccount}
          existingGoals={goals}
          saving={goalsSaving}
          onSkip={() => {
            const id = goalsPromptAccount.id;
            setGoalsPromptAccount(null);
            navigate(`/app/debt/${id}`);
          }}
          onApply={async (nextGoals) => {
            const id = goalsPromptAccount.id;
            for (const goal of nextGoals) {
              await saveGoal(goal);
            }
            setGoalsPromptAccount(null);
            navigate(`/app/debt/${id}`);
          }}
        />
      ) : null}
    </div>
  );
}
