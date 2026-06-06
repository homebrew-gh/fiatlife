import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import {
  ALL_GENERAL_CATEGORIES,
  GENERAL_CATEGORY_LABELS,
  generalCategoryForBill,
} from "../../lib/bill";
import { useBillsData } from "../../lib/billsData";
import {
  billDisplayAmount,
  billDueLabel,
  computeDashboardState,
} from "../../lib/dashboard";
import { formatUsd } from "../../lib/format";
import { goalProgressPercent } from "../../lib/goal";
import { useGoalsData } from "../../lib/goalsData";
import { useSalaryData } from "../../lib/salaryData";
import { formatPercent } from "../../lib/salary";

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
  const [refreshing, setRefreshing] = useState(false);

  const loading = billsLoading || salary.loading || goalsLoading;
  const error = billsError ?? salary.error ?? goalsError;

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
      }),
    [salary.config, salary.calculation, bills, goals],
  );

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await Promise.all([reloadBills(), salary.reload(), reloadGoals()]);
    } finally {
      setRefreshing(false);
    }
  };

  const categoryEntries = ALL_GENERAL_CATEGORIES.filter(
    (cat) => (dash.billCategoryTotals[cat] ?? 0) > 0,
  );

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p className="text-sm text-muted mt-1">
            Monthly take-home, bills, and what&apos;s left over.
          </p>
        </div>
        <button
          type="button"
          className="btn-ghost text-sm shrink-0"
          onClick={() => void onRefresh()}
          disabled={refreshing}
        >
          {refreshing ? "Syncing…" : "Sync"}
        </button>
      </div>

      {loading ? (
        <p className="text-muted text-sm">Loading dashboard…</p>
      ) : null}

      {error ? (
        <div className="card-quiet p-4 text-sm text-error" role="alert">
          {error}
        </div>
      ) : null}

      {!loading && !error && !dash.hasData ? (
        <section className="card p-6 text-center">
          <p className="text-muted text-sm">
            Set up your{" "}
            <Link to="/app/paycheck" className="text-accent underline">
              paycheck
            </Link>{" "}
            and{" "}
            <Link to="/app/bills" className="text-accent underline">
              bills
            </Link>{" "}
            to see your financial overview.
          </p>
        </section>
      ) : null}

      {!loading && !error && dash.hasData ? (
        <>
          <section className="card p-5 bg-dollar-gradient text-center">
            <p className="text-xs tracking-wider text-muted font-medium">
              Take Home This Month
            </p>
            <p className="money text-3xl mt-1">
              {formatUsd(dash.takeHomePay)}
            </p>
            <p className="text-xs text-muted mt-1">
              {dash.hasSalary
                ? "Based on paycheck schedule this month"
                : "Add paycheck info for take-home estimate"}
            </p>
            <div className="mt-4 flex justify-center gap-6 text-sm">
              <div>
                <p className="text-muted text-xs">Monthly gross</p>
                <p className="money">{formatUsd(dash.grossPay)}</p>
              </div>
              <div>
                <p className="text-muted text-xs">Tax rate</p>
                <p className="money">{formatPercent(dash.effectiveTaxRate)}</p>
              </div>
              {dash.ytdSource !== "none" ? (
                <div>
                  <p className="text-muted text-xs">YTD net</p>
                  <p className="money">{formatUsd(dash.ytdNetPay)}</p>
                </div>
              ) : null}
            </div>
          </section>

          <div className="grid grid-cols-2 gap-3">
            <QuickStat
              label="Monthly taxes"
              value={formatUsd(dash.totalTaxes)}
              tone="error"
            />
            <QuickStat
              label="Monthly deductions"
              value={formatUsd(dash.totalDeductions)}
              tone="warn"
            />
          </div>

          <section className="card p-5 space-y-4">
            <h2 className="section-title">Monthly Overview</h2>
            <div className="flex justify-between gap-4 text-sm">
              <div>
                <p className="text-muted text-xs">Monthly bills</p>
                <p className="money text-lg">{formatUsd(dash.monthlyBills)}</p>
              </div>
              <div className="text-right">
                <p className="text-muted text-xs">After bills</p>
                <p
                  className={clsx(
                    "money text-lg",
                    dash.monthlyDisposable >= 0
                      ? "text-success"
                      : "text-error",
                  )}
                >
                  {formatUsd(dash.monthlyDisposable)}
                </p>
              </div>
            </div>

            {categoryEntries.length > 0 ? (
              <div>
                <p className="text-xs text-muted font-medium mb-2">
                  Bills by category
                </p>
                <ul className="space-y-1">
                  {categoryEntries.map((cat) => (
                    <li
                      key={cat}
                      className="flex justify-between text-sm py-1 divider-line last:border-0"
                    >
                      <span className="text-body">
                        {GENERAL_CATEGORY_LABELS[cat]}
                      </span>
                      <span className="money">
                        {formatUsd(dash.billCategoryTotals[cat] ?? 0)}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}

            {dash.overdueBillCount > 0 || dash.billsComingDueCount > 0 ? (
              <div className="flex flex-wrap gap-2 pt-1">
                {dash.overdueBillCount > 0 ? (
                  <Link
                    to="/app/bills"
                    className="badge-error text-xs px-3 py-1 rounded-pill font-medium"
                  >
                    {dash.overdueBillCount} overdue bill
                    {dash.overdueBillCount === 1 ? "" : "s"}
                  </Link>
                ) : null}
                {dash.billsComingDueCount > 0 ? (
                  <Link
                    to="/app/bills"
                    className="badge-autopay text-xs px-3 py-1 rounded-pill font-medium"
                  >
                    {dash.billsComingDueCount} coming due soon
                  </Link>
                ) : null}
              </div>
            ) : null}
          </section>

          {dash.upcomingBills.length > 0 ? (
            <section className="card p-5">
              <h2 className="section-title">Upcoming Bills</h2>
              <ul className="mt-3 space-y-2">
                {dash.upcomingBills.map((bill) => {
                  const due = billDueLabel(bill);
                  const pastDue = due.includes("Overdue");
                  return (
                    <li
                      key={bill.id}
                      className="flex items-start justify-between gap-3 py-2 divider-line last:border-0"
                    >
                      <div className="min-w-0">
                        <p className="text-body font-medium truncate">
                          {bill.name}
                        </p>
                        <p className="text-xs text-muted">
                          {GENERAL_CATEGORY_LABELS[generalCategoryForBill(bill)]}
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
                        {formatUsd(billDisplayAmount(bill))}
                      </p>
                    </li>
                  );
                })}
              </ul>
            </section>
          ) : null}

          <section className="card p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="section-title">Financial Goals</h2>
              <Link to="/app/goals" className="text-sm text-accent">
                View all
              </Link>
            </div>
            {dash.goalCount === 0 ? (
              <p className="text-sm text-muted mt-2">
                No goals yet.{" "}
                <Link to="/app/goals" className="text-accent underline">
                  Add your first goal
                </Link>{" "}
                on the Goals tab.
              </p>
            ) : (
              <div className="mt-3 space-y-3">
                <div className="flex justify-between text-sm">
                  <div>
                    <p className="text-muted text-xs">Total saved</p>
                    <p className="money">{formatUsd(dash.totalSaved)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-muted text-xs">Target</p>
                    <p className="money">{formatUsd(dash.totalGoalTarget)}</p>
                  </div>
                </div>
                <div className="h-2 rounded-full bg-surfaceVariant overflow-hidden">
                  <div
                    className="h-full bg-success rounded-full"
                    style={{
                      width: `${Math.min(100, dash.goalsProgress)}%`,
                    }}
                  />
                </div>
                <p className="text-xs text-muted">
                  {dash.goalsProgress.toFixed(1)}% complete
                </p>
                <ul className="space-y-2">
                  {dash.topGoals.map((goal) => (
                    <li
                      key={goal.id}
                      className="flex justify-between gap-2 py-2 divider-line last:border-0 text-sm"
                    >
                      <div className="min-w-0">
                        <p className="text-body font-medium truncate">
                          {goal.name}
                        </p>
                        <p className="text-xs text-muted">
                          {formatUsd(goal.currentAmount)} /{" "}
                          {formatUsd(goal.targetAmount)}
                        </p>
                      </div>
                      <p className="money text-success shrink-0">
                        {goalProgressPercent(goal).toFixed(1)}%
                      </p>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}

function QuickStat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: "error" | "warn";
}) {
  return (
    <div className="card-quiet p-4 text-center">
      <p className="text-xs text-muted">{label}</p>
      <p
        className={clsx(
          "money text-xl mt-1",
          tone === "error" ? "text-error" : "text-warn",
        )}
      >
        {value}
      </p>
    </div>
  );
}
