import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import {
  generalCategoryForBill,
  monthlyEquivalent,
  type BillGeneralCategory,
} from "../../lib/bill";
import { useBillsData } from "../../lib/billsData";
import {
  computeBudgetSummary,
  setCategoryBudget,
  type BudgetRow,
} from "../../lib/budget";
import { useBudgetData } from "../../lib/budgetData";
import { formatUsd } from "../../lib/format";
import { computeMonthlyTakeHome } from "../../lib/salary";
import { useSalaryData } from "../../lib/salaryData";

function hasSalaryConfigured(c: {
  hourlyRate: number;
  updatedAt: number;
  id: string;
}): boolean {
  return c.hourlyRate > 0 || c.updatedAt > 0 || Boolean(c.id);
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

export function BudgetTab() {
  const { config, loading, error, saving, reload, setConfig } = useBudgetData();
  const { bills, loading: billsLoading, reload: reloadBills } = useBillsData();
  const salary = useSalaryData();
  const [refreshing, setRefreshing] = useState(false);

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
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="page-title">Budget</h1>
          <p className="text-sm text-muted mt-1">
            Set monthly targets and track spending against your take-home pay.
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {saving ? (
            <span className="text-xs text-muted">Saving…</span>
          ) : null}
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => void onRefresh()}
            disabled={refreshing || isLoading}
          >
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
        </div>
      </div>

      {error ? (
        <div className="card-quiet p-4 text-sm text-error" role="alert">
          {error}
        </div>
      ) : null}

      {isLoading ? <p className="text-sm text-muted">Loading budget…</p> : null}

      {!isLoading && !hasAnything ? (
        <section className="card p-6 text-center">
          <p className="text-muted text-sm">
            Set up your{" "}
            <Link to="/app/paycheck" className="text-accent underline">
              paycheck
            </Link>{" "}
            and{" "}
            <Link to="/app/bills" className="text-accent underline">
              bills
            </Link>
            , then set targets below to start budgeting.
          </p>
        </section>
      ) : null}

      {!isLoading && hasAnything ? (
        <>
          <section className="card p-5 bg-dollar-gradient">
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
                    <span className="text-sm text-body font-medium">
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
                        <span className="text-sm text-body font-medium">
                          {row.label}
                        </span>
                        <span className="block text-xs text-muted">
                          {formatUsd(row.actual)}/mo in bills
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
