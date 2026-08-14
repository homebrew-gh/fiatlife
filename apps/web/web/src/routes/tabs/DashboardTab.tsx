import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import {
  EmptyState,
  ErrorBanner,
  HeroCard,
  PageHeader,
} from "../../components/ui";
import { GENERAL_CATEGORY_LABELS, generalCategoryForBill, monthlyEquivalent, type BillGeneralCategory } from "../../lib/bill";
import { computeBudgetSummary } from "../../lib/budget";
import { useBudgetData } from "../../lib/budgetData";
import { useBillsData } from "../../lib/billsData";
import { summarizeDebt } from "../../lib/creditAccount";
import { formatPayoffDate, summarizeDebtPayoff } from "../../lib/debtPayoff";
import { useDebtData } from "../../lib/debtData";
import {
  billDisplayAmount,
  billDueLabel,
  computeDashboardState,
} from "../../lib/dashboard";
import { formatUsd } from "../../lib/format";
import { goalProgressPercent } from "../../lib/goal";
import { useGoalsData } from "../../lib/goalsData";
import { useSalaryData } from "../../lib/salaryData";

export function DashboardTab() {
  const { bills, loading: billsLoading, error: billsError, reload: reloadBills } =
    useBillsData();
  const salary = useSalaryData();
  const {
    goals,
    loading: goalsLoading,
    error: goalsError,
    reload: reloadGoals,
  } = useGoalsData();
  const budget = useBudgetData();
  const debt = useDebtData();
  const [refreshing, setRefreshing] = useState(false);

  const loading =
    billsLoading || salary.loading || goalsLoading || budget.loading || debt.loading;
  const error =
    billsError ?? salary.error ?? goalsError ?? budget.error ?? debt.error;

  const dash = useMemo(
    () =>
      computeDashboardState({
        salary:
          salary.config.hourlyRate > 0 ||
          salary.config.updatedAt > 0 ||
          Boolean(salary.config.id)
            ? salary.config
            : null,
        calculation: salary.calculation,
        bills,
        goals,
        creditAccounts: debt.accounts,
      }),
    [salary.config, salary.calculation, bills, goals, debt.accounts],
  );

  const billCategoryTotals = useMemo(() => {
    const totals: Partial<Record<BillGeneralCategory, number>> = {};
    for (const item of bills) {
      if (item.bill.isCancelled) continue;
      const cat = generalCategoryForBill(item.bill);
      totals[cat] = (totals[cat] ?? 0) + monthlyEquivalent(item.bill);
    }
    return totals;
  }, [bills]);

  const budgetSummary = useMemo(
    () =>
      computeBudgetSummary({
        config: budget.config,
        billCategoryTotals,
        takeHome: dash.takeHomePay,
      }),
    [budget.config, billCategoryTotals, dash.takeHomePay],
  );

  const debtSummary = useMemo(
    () => summarizeDebt(debt.accounts),
    [debt.accounts],
  );
  const debtPayoff = useMemo(
    () => summarizeDebtPayoff(debt.accounts),
    [debt.accounts],
  );

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await Promise.all([
        reloadBills(),
        salary.reload(),
        reloadGoals(),
        budget.reload(),
        debt.reload(),
      ]);
    } finally {
      setRefreshing(false);
    }
  };

  const showBudget = dash.takeHomePay > 0 || budgetSummary.totalTarget > 0;
  const showDebt = debtSummary.accountCount > 0;
  const showHousing = dash.housingMonthly > 0;
  const snapshotCount =
    Number(showBudget) + Number(showDebt) + Number(showHousing);

  return (
    <div className="space-y-5">
      <PageHeader
        title="Home"
        description="Leftover cash, bills due soon, and shortcuts."
        refreshing={refreshing}
        onRefresh={() => void onRefresh()}
      />

      {loading ? (
        <p className="text-muted text-sm">Loading home…</p>
      ) : null}

      {error ? <ErrorBanner message={error} /> : null}

      {!loading && !error && !dash.hasData ? (
        <EmptyState
          title="Get started"
          description={
            <>
              Set up your{" "}
              <Link to="/app/paycheck" className="text-accent underline">
                paycheck
              </Link>{" "}
              and{" "}
              <Link to="/app/bills" className="text-accent underline">
                bills
              </Link>{" "}
              to see leftover cash and what's due.
            </>
          }
        />
      ) : null}

      {!loading && !error && dash.hasData ? (
        <>
          {dash.overdueBillCount > 0 ||
          dash.billsComingDueCount > 0 ||
          dash.missingPaycheckCount > 0 ? (
            <div className="flex flex-wrap gap-2">
              {dash.overdueBillCount > 0 ? (
                <Link
                  to="/app/bills"
                  className="badge-error text-xs px-3 py-1.5 rounded-pill font-medium"
                >
                  {dash.overdueBillCount} overdue
                </Link>
              ) : null}
              {dash.billsComingDueCount > 0 ? (
                <Link
                  to="/app/bills"
                  className="badge-autopay text-xs px-3 py-1.5 rounded-pill font-medium"
                >
                  {dash.billsComingDueCount} due in 7 days
                </Link>
              ) : null}
              {dash.missingPaycheckCount > 0 ? (
                <Link
                  to="/app/paycheck"
                  className="badge-autopay text-xs px-3 py-1.5 rounded-pill font-medium"
                >
                  {dash.missingPaycheckCount === 1
                    ? "Log paycheck"
                    : `${dash.missingPaycheckCount} missing paychecks`}
                </Link>
              ) : null}
            </div>
          ) : null}

          <Link to="/app/paycheck" className="block rounded-xl">
            <HeroCard>
              <p className="text-xs tracking-wider text-muted font-medium">
                This month
              </p>
              <div className="mt-3 grid grid-cols-2 gap-4">
                <div>
                  <p className="text-muted text-xs">After bills</p>
                  <p
                    className={clsx(
                      "money text-3xl mt-1",
                      dash.monthlyDisposable >= 0
                        ? "text-success"
                        : "text-error",
                    )}
                  >
                    {formatUsd(dash.monthlyDisposable)}
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-muted text-xs">Take-home</p>
                  <p className="money text-2xl mt-1">
                    {formatUsd(dash.takeHomePay)}
                  </p>
                </div>
              </div>
              {dash.monthlyBills > 0 ? (
                <p className="text-xs text-muted mt-2">
                  Bills {formatUsd(dash.monthlyBills)} this month
                </p>
              ) : null}
              <p className="text-xs text-muted mt-1">
                {!dash.hasSalary
                  ? "Add paycheck info for take-home estimate"
                  : dash.monthlyTakeHomeSource === "logged"
                    ? `From ${dash.monthlyLoggedPaycheckCount} logged paycheck${
                        dash.monthlyLoggedPaycheckCount === 1 ? "" : "s"
                      } this month`
                    : dash.monthlyTakeHomeSource === "mixed"
                      ? "Logged paychecks + projected remainder at base pay"
                      : "Estimated from current pay rate"}
              </p>
              {dash.hasSalary &&
              (dash.monthlyLoggedTakeHome > 0 ||
                dash.monthlyProjectedRemainder > 0) ? (
                <div className="mt-3 space-y-1 text-sm">
                  {dash.monthlyLoggedTakeHome > 0 ? (
                    <div className="flex justify-between gap-2">
                      <span className="text-muted">Paid so far</span>
                      <span className="money">
                        {formatUsd(dash.monthlyLoggedTakeHome)}
                      </span>
                    </div>
                  ) : null}
                  {dash.monthlyProjectedRemainder > 0 ? (
                    <div className="flex justify-between gap-2">
                      <span className="text-muted">
                        Projected remainder
                        {dash.monthlyRemainingPaycheckCount > 0
                          ? ` (${dash.monthlyRemainingPaycheckCount} × ${formatUsd(dash.monthlyPerPaycheckEstimate)})`
                          : ""}
                      </span>
                      <span className="money">
                        {formatUsd(dash.monthlyProjectedRemainder)}
                      </span>
                    </div>
                  ) : null}
                  {dash.monthlyLoggedOvertimeHours > 0 ||
                  dash.monthlyLoggedBonus > 0 ? (
                    <p className="text-xs text-muted pt-1">
                      Includes
                      {dash.monthlyLoggedOvertimeHours > 0
                        ? ` ${dash.monthlyLoggedOvertimeHours.toFixed(1)} OT hrs`
                        : ""}
                      {dash.monthlyLoggedOvertimeHours > 0 &&
                      dash.monthlyLoggedBonus > 0
                        ? " and"
                        : ""}
                      {dash.monthlyLoggedBonus > 0
                        ? ` ${formatUsd(dash.monthlyLoggedBonus)} in bonuses`
                        : ""}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </HeroCard>
          </Link>

          {snapshotCount > 0 ? (
            <div
              className={clsx(
                "grid gap-3",
                snapshotCount >= 3
                  ? "grid-cols-1 sm:grid-cols-3"
                  : "grid-cols-2",
              )}
            >
              {showBudget ? (
                <SnapshotTile
                  to="/app/budget"
                  label={
                    budgetSummary.totalTarget > 0 ? "Unbudgeted" : "Budget"
                  }
                  value={
                    budgetSummary.totalTarget > 0
                      ? formatUsd(budgetSummary.unbudgeted)
                      : "Set targets"
                  }
                />
              ) : null}
              {showDebt ? (
                <SnapshotTile
                  to="/app/debt"
                  label="Debt"
                  value={formatUsd(debtSummary.totalDebt)}
                  detail={
                    debtPayoff.allFeasible && debtPayoff.debtFreeDateMs != null
                      ? `Free ${formatPayoffDate(debtPayoff.debtFreeDateMs)}`
                      : undefined
                  }
                />
              ) : null}
              {showHousing ? (
                <SnapshotTile
                  to={
                    dash.mortgageAccountId
                      ? `/app/debt/${dash.mortgageAccountId}`
                      : "/app/debt"
                  }
                  label="Housing (PITI)"
                  value={formatUsd(dash.housingMonthly)}
                />
              ) : null}
            </div>
          ) : null}

          {dash.upcomingBills.length > 0 ? (
            <section className="card p-5">
              <div className="flex items-center justify-between gap-2">
                <h2 className="section-title">Due soon</h2>
                <Link to="/app/bills" className="text-sm text-accent">
                  View all
                </Link>
              </div>
              <ul className="mt-3 space-y-2">
                {dash.upcomingBills.map((bill) => {
                  const due = billDueLabel(bill);
                  const pastDue = due.includes("Overdue");
                  return (
                    <li key={bill.id} className="divider-line last:border-0">
                      <Link
                        to={`/app/bills/${bill.id}`}
                        className="flex items-start justify-between gap-3 py-2"
                      >
                        <div className="min-w-0">
                          <p className="text-body font-medium truncate">
                            {bill.name}
                          </p>
                          <p className="text-xs text-muted">
                            {
                              GENERAL_CATEGORY_LABELS[
                                generalCategoryForBill(bill)
                              ]
                            }
                          </p>
                          {due ? (
                            <p
                              className={clsx(
                                "text-xs mt-0.5",
                                pastDue ? "text-error" : "text-accent",
                              )}
                            >
                              {due}
                            </p>
                          ) : null}
                        </div>
                        <p className="money text-base shrink-0">
                          {formatUsd(billDisplayAmount(bill, debt.accounts))}
                        </p>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </section>
          ) : null}

          <section className="card p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="section-title">Goal</h2>
              <Link to="/app/goals" className="text-sm text-accent">
                View all
              </Link>
            </div>
            {dash.goalCount === 0 ? (
              <p className="text-sm text-muted mt-2">
                No goals yet.{" "}
                <Link to="/app/goals" className="text-accent underline">
                  Add your first goal
                </Link>
                .
              </p>
            ) : dash.primaryGoal == null ? (
              <p className="text-sm text-muted mt-2">All goals complete.</p>
            ) : (
              <Link to="/app/goals" className="mt-3 block space-y-2">
                <p className="text-body font-medium truncate">
                  {dash.primaryGoal.name}
                </p>
                <p className="text-xs text-muted">
                  {formatUsd(dash.primaryGoal.currentAmount)} /{" "}
                  {formatUsd(dash.primaryGoal.targetAmount)}
                </p>
                <div className="h-2 rounded-full bg-surfaceVariant overflow-hidden">
                  <div
                    className="h-full bg-success rounded-full"
                    style={{
                      width: `${Math.min(100, goalProgressPercent(dash.primaryGoal))}%`,
                    }}
                  />
                </div>
                <p className="text-xs text-muted">
                  {goalProgressPercent(dash.primaryGoal).toFixed(1)}% complete
                </p>
              </Link>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}

function SnapshotTile({
  to,
  label,
  value,
  detail,
}: {
  to: string;
  label: string;
  value: string;
  detail?: string;
}) {
  return (
    <Link to={to} className="card-quiet p-4 min-w-0">
      <p className="text-xs text-muted">{label}</p>
      <p className="money text-base mt-1 truncate">{value}</p>
      {detail ? (
        <p className="text-xs text-muted mt-0.5 truncate">{detail}</p>
      ) : null}
    </Link>
  );
}
