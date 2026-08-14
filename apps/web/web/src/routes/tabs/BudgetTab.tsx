import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import {
  ALL_GENERAL_CATEGORIES,
  generalCategoryForBill,
  monthlyEquivalent,
  type BillGeneralCategory,
} from "../../lib/bill";
import { useBillsData } from "../../lib/billsData";
import {
  budgetBreakdown,
  computeBudgetSummary,
  savingsAmount,
  savingsRate,
  setCategoryBudget,
  VARIABLE_CATEGORIES,
  type BudgetRow,
} from "../../lib/budget";
import { useBudgetData } from "../../lib/budgetData";
import { DonutChart, paletteColor, type DonutSlice } from "../../components/DonutChart";
import { formatUsd } from "../../lib/format";
import { computeMonthlyTakeHome } from "../../lib/salary";
import { useSalaryData } from "../../lib/salaryData";
import {
  EmptyState,
  ErrorBanner,
  HeroCard,
  PageHeader,
  SegmentedControl,
} from "../../components/ui";

function hasSalaryConfigured(c: {
  hourlyRate: number;
  updatedAt: number;
  id: string;
}): boolean {
  return c.hourlyRate > 0 || c.updatedAt > 0 || Boolean(c.id);
}

/**
 * Stable color per category key, shared by the chart and the row dots so the
 * legend always lines up with the list below.
 */
const CATEGORY_COLORS: Record<string, string> = (() => {
  const keys = [
    ...VARIABLE_CATEGORIES.map((c) => c.key),
    ...ALL_GENERAL_CATEGORIES,
  ];
  const map: Record<string, string> = {};
  keys.forEach((key, i) => {
    map[key] = paletteColor(i);
  });
  return map;
})();

function colorForKey(key: string): string {
  return CATEGORY_COLORS[key] ?? paletteColor(0);
}

function ColorDot({ color }: { color: string }) {
  return (
    <span
      className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
      style={{ backgroundColor: color }}
      aria-hidden
    />
  );
}

/** Number input bound to a budget amount, tolerant of mid-typing states. */
function AmountField({
  value,
  onCommit,
  ariaLabel,
}: {
  value: number;
  onCommit: (n: number) => void;
  ariaLabel: string;
}) {
  const [text, setText] = useState(value > 0 ? String(value) : "");

  useEffect(() => {
    setText(value > 0 ? String(value) : "");
  }, [value]);

  return (
    <div className="relative w-28 shrink-0">
      <span className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-xs text-muted">
        $
      </span>
      <input
        type="number"
        inputMode="decimal"
        min={0}
        step="0.01"
        aria-label={ariaLabel}
        className="input w-full pl-5 pr-2 py-1 text-right text-sm font-mono"
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          const n = Number(e.target.value);
          onCommit(Number.isFinite(n) && n >= 0 ? n : 0);
        }}
      />
    </div>
  );
}

function ProgressBar({ row }: { row: BudgetRow }) {
  const pct = Math.min(100, row.percentUsed);
  const over = row.target > 0 && row.actual > row.target;
  return (
    <div className="h-1.5 rounded-full bg-surfaceVariant overflow-hidden">
      <div
        className={clsx(
          "h-full rounded-full transition-all",
          over ? "bg-error" : row.percentUsed >= 85 ? "bg-warn" : "bg-success",
        )}
        style={{ width: `${row.target > 0 ? pct : over ? 100 : 0}%` }}
      />
    </div>
  );
}

/** Stacked bar visualizing how take-home is allocated across targets. */
function AllocationBar({
  takeHome,
  budgeted,
}: {
  takeHome: number;
  budgeted: number;
}) {
  if (takeHome <= 0) return null;
  const over = budgeted > takeHome;
  const budgetedPct = Math.min(100, (budgeted / takeHome) * 100);
  const overPct = over ? Math.min(100, ((budgeted - takeHome) / takeHome) * 100) : 0;
  return (
    <div className="mt-4">
      <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-surfaceVariant">
        <div
          className={clsx("h-full", over ? "bg-warn" : "bg-success")}
          style={{ width: `${budgetedPct}%` }}
        />
        {over ? (
          <div className="h-full bg-error" style={{ width: `${overPct}%` }} />
        ) : null}
      </div>
      <div className="mt-1.5 flex justify-between text-[11px] text-muted">
        <span>{Math.round((budgeted / takeHome) * 100)}% of take-home budgeted</span>
        <span>
          {over
            ? `${formatUsd(budgeted - takeHome)} over income`
            : `${formatUsd(takeHome - budgeted)} left to allocate`}
        </span>
      </div>
    </div>
  );
}

export function BudgetTab() {
  const { config, loading, error, saving, reload, setConfig } = useBudgetData();
  const { bills, loading: billsLoading, reload: reloadBills } = useBillsData();
  const salary = useSalaryData();
  const [refreshing, setRefreshing] = useState(false);
  const [chartMetric, setChartMetric] = useState<"actual" | "target">("actual");

  const billCategoryTotals = useMemo(() => {
    const totals: Partial<Record<BillGeneralCategory, number>> = {};
    for (const item of bills) {
      const bill = item.bill;
      if (bill.isCancelled) continue;
      const cat = generalCategoryForBill(bill);
      totals[cat] = (totals[cat] ?? 0) + monthlyEquivalent(bill);
    }
    return totals;
  }, [bills]);

  const takeHome = useMemo(() => {
    if (!hasSalaryConfigured(salary.config)) return 0;
    return computeMonthlyTakeHome(salary.config).totalTakeHome;
  }, [salary.config]);

  const summary = useMemo(
    () => computeBudgetSummary({ config, billCategoryTotals, takeHome }),
    [config, billCategoryTotals, takeHome],
  );

  const breakdown = useMemo(
    () => budgetBreakdown(summary, chartMetric),
    [summary, chartMetric],
  );

  const slices = useMemo<DonutSlice[]>(
    () =>
      breakdown.map((s) => ({
        label: s.label,
        value: s.value,
        color: colorForKey(s.key),
      })),
    [breakdown],
  );

  const breakdownTotal = useMemo(
    () => breakdown.reduce((sum, s) => sum + s.value, 0),
    [breakdown],
  );

  const savedThisMonth = savingsAmount(summary);
  const savedRate = savingsRate(summary);

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await Promise.all([reload(), reloadBills(), salary.reload()]);
    } finally {
      setRefreshing(false);
    }
  };

  const updateTarget = (
    key: string,
    kind: "bill" | "variable",
    target: number,
  ) => {
    setConfig((c) => setCategoryBudget(c, key, kind, { target }));
  };

  const updateSpent = (key: string, manualSpent: number) => {
    setConfig((c) => setCategoryBudget(c, key, "variable", { manualSpent }));
  };

  const isLoading = loading || billsLoading || salary.loading;
  const hasAnything =
    takeHome > 0 ||
    summary.billRows.length > 0 ||
    summary.totalTarget > 0 ||
    summary.totalVariableActual > 0;

  return (
    <div className="space-y-5">
      <PageHeader
        title="Budget"
        description="Set monthly targets and track spending against your take-home pay."
        refreshing={refreshing}
        onRefresh={() => void onRefresh()}
        refreshDisabled={isLoading}
        actions={
          saving ? <span className="text-xs text-muted">Saving…</span> : null
        }
      />

      {error ? <ErrorBanner message={error} /> : null}

      {isLoading ? <p className="text-sm text-muted">Loading budget…</p> : null}

      {!isLoading && !hasAnything ? (
        <EmptyState
          title="Start budgeting"
          description={
            <>
              Set up your{" "}
              <Link to="/app/paycheck" className="text-accent underline">
                paycheck
              </Link>{" "}
              and{" "}
              <Link to="/app/bills" className="text-accent underline">
                bills
              </Link>
              , then set targets below to start budgeting.
            </>
          }
        />
      ) : null}

      {!isLoading && hasAnything ? (
        <>
          <HeroCard>
            <div className="grid grid-cols-3 gap-3 text-center">
              <div>
                <p className="text-xs text-muted">Take-home</p>
                <p className="money text-lg">{formatUsd(summary.takeHome)}</p>
              </div>
              <div>
                <p className="text-xs text-muted">Budgeted</p>
                <p className="money text-lg">{formatUsd(summary.totalTarget)}</p>
              </div>
              <div>
                <p className="text-xs text-muted">Unbudgeted</p>
                <p
                  className={clsx(
                    "money text-lg",
                    summary.unbudgeted >= 0 ? "text-success" : "text-error",
                  )}
                >
                  {formatUsd(summary.unbudgeted)}
                </p>
              </div>
            </div>

            <AllocationBar
              takeHome={summary.takeHome}
              budgeted={summary.totalTarget}
            />

            <div className="mt-4 flex items-center justify-between gap-4 border-t border-outline pt-3 text-sm">
              <span className="text-muted">
                Spent / committed so far
                <span className="text-body font-medium ml-1">
                  {formatUsd(summary.totalActual)}
                </span>
              </span>
              <span
                className={clsx(
                  "money font-medium",
                  summary.remaining >= 0 ? "text-success" : "text-error",
                )}
              >
                {formatUsd(summary.remaining)} left
              </span>
            </div>

            {summary.takeHome > 0 ? (
              <div className="mt-3 flex items-center justify-between gap-2 rounded-card bg-surfaceVariant px-3 py-2 text-sm">
                <span className="text-muted">Saving / investing this month</span>
                <span className="money font-semibold">
                  {formatUsd(savedThisMonth)}
                  <span className="text-muted font-normal ml-1">
                    ({Math.round(savedRate * 100)}% of pay)
                  </span>
                </span>
              </div>
            ) : null}
          </HeroCard>

          <section className="card p-5 space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="section-title">Where your money goes</h2>
                <p className="text-xs text-muted mt-0.5">
                  {chartMetric === "actual"
                    ? "Breakdown of spending and committed bills this month."
                    : "Breakdown of your monthly budget targets."}
                </p>
              </div>
              <SegmentedControl
                variant="pill"
                options={[
                  { id: "actual", label: "Spending" },
                  { id: "target", label: "Budget" },
                ]}
                value={chartMetric}
                onChange={setChartMetric}
                ariaLabel="Chart metric"
              />
            </div>

            {slices.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted">
                {chartMetric === "actual"
                  ? "No spending recorded yet this month."
                  : "No budget targets set yet."}
              </p>
            ) : (
              <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-center sm:gap-6">
                <div className="shrink-0">
                  <DonutChart
                    data={slices}
                    size={200}
                    thickness={28}
                    centerLabel={formatUsd(breakdownTotal)}
                    centerSubLabel={chartMetric === "actual" ? "spent" : "budgeted"}
                  />
                </div>
                <ul className="w-full flex-1 space-y-2">
                  {breakdown.map((s) => {
                    const pct =
                      breakdownTotal > 0 ? (s.value / breakdownTotal) * 100 : 0;
                    return (
                      <li
                        key={`${s.kind}-${s.key}`}
                        className="flex items-center gap-2.5 text-sm"
                      >
                        <ColorDot color={colorForKey(s.key)} />
                        <span className="min-w-0 flex-1 truncate text-body">
                          {s.label}
                        </span>
                        <span className="money text-xs">{formatUsd(s.value)}</span>
                        <span className="w-9 shrink-0 text-right text-xs text-muted">
                          {pct.toFixed(0)}%
                        </span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            )}
          </section>

          <section className="card p-5 space-y-3">
            <div>
              <h2 className="section-title">Spending</h2>
              <p className="text-xs text-muted mt-0.5">
                Variable purchases that aren&apos;t bills. Enter what you&apos;ve
                spent this month.
              </p>
            </div>
            <div className="space-y-4">
              {summary.variableRows.map((row) => (
                <div key={row.key} className="space-y-1.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="flex items-center gap-2 text-sm text-body font-medium">
                      <ColorDot color={colorForKey(row.key)} />
                      {row.label}
                    </span>
                    <div className="flex items-center gap-2">
                      <div className="text-right">
                        <span className="block text-[10px] uppercase tracking-wide text-muted">
                          Spent
                        </span>
                        <AmountField
                          value={row.actual}
                          ariaLabel={`${row.label} spent so far`}
                          onCommit={(n) => updateSpent(row.key, n)}
                        />
                      </div>
                      <div className="text-right">
                        <span className="block text-[10px] uppercase tracking-wide text-muted">
                          Target
                        </span>
                        <AmountField
                          value={row.target}
                          ariaLabel={`${row.label} monthly target`}
                          onCommit={(n) => updateTarget(row.key, "variable", n)}
                        />
                      </div>
                    </div>
                  </div>
                  <ProgressBar row={row} />
                  <div className="flex justify-between text-xs text-muted">
                    <span>
                      {row.target > 0
                        ? `${row.percentUsed.toFixed(0)}% of target`
                        : "No target set"}
                    </span>
                    {row.target > 0 ? (
                      <span
                        className={clsx(
                          row.remaining >= 0 ? "text-success" : "text-error",
                        )}
                      >
                        {row.remaining >= 0
                          ? `${formatUsd(row.remaining)} left`
                          : `${formatUsd(-row.remaining)} over`}
                      </span>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section className="card p-5 space-y-3">
            <div>
              <h2 className="section-title">Bills</h2>
              <p className="text-xs text-muted mt-0.5">
                Pulled automatically from your{" "}
                <Link to="/app/bills" className="text-accent underline">
                  recurring bills
                </Link>
                . Set a target to budget against them.
              </p>
            </div>
            {summary.billRows.length === 0 ? (
              <p className="text-sm text-muted">No bills yet.</p>
            ) : (
              <div className="space-y-4">
                {summary.billRows.map((row) => (
                  <div key={row.key} className="space-y-1.5">
                    <div className="flex items-center justify-between gap-2">
                      <div className="min-w-0">
                        <span className="flex items-center gap-2 text-sm text-body font-medium">
                          <ColorDot color={colorForKey(row.key)} />
                          {row.label}
                        </span>
                        <span className="block text-xs text-muted">
                          {row.actual > 0
                            ? `${formatUsd(row.actual)}/mo in bills`
                            : "No bills yet"}
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="block text-[10px] uppercase tracking-wide text-muted">
                          Target
                        </span>
                        <AmountField
                          value={row.target}
                          ariaLabel={`${row.label} monthly target`}
                          onCommit={(n) => updateTarget(row.key, "bill", n)}
                        />
                      </div>
                    </div>
                    {row.target > 0 ? (
                      <>
                        <ProgressBar row={row} />
                        <div className="flex justify-between text-xs text-muted">
                          <span>{row.percentUsed.toFixed(0)}% of target</span>
                          <span
                            className={clsx(
                              row.remaining >= 0
                                ? "text-success"
                                : "text-error",
                            )}
                          >
                            {row.remaining >= 0
                              ? `${formatUsd(row.remaining)} under`
                              : `${formatUsd(-row.remaining)} over`}
                          </span>
                        </div>
                      </>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
