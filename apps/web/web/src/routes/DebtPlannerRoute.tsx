import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import { PayoffChart } from "../components/debt/PayoffChart";
import {
  CREDIT_ACCOUNT_TYPE_LABELS,
  sortCreditAccounts,
} from "../lib/creditAccount";
import { useDebtData } from "../lib/debtData";
import { formatUsd } from "../lib/format";
import { formatMonths, formatPayoffDate } from "../lib/debtPayoff";
import { HeroCard } from "../components/ui";
import {
  STRATEGY_DESCRIPTIONS,
  STRATEGY_LABELS,
  buildDebtPlan,
  promoExpiryWarnings,
  type PayoffStrategy,
} from "../lib/debtPlanner";

const EXTRA_PRESETS = [0, 50, 100, 250, 500];

export function DebtPlannerRoute() {
  const { accounts, loading } = useDebtData();
  const [strategy, setStrategy] = useState<PayoffStrategy>("AVALANCHE");
  const [extra, setExtra] = useState(100);
  const [perAccountExtra, setPerAccountExtra] = useState<
    Record<string, number>
  >({});

  const payable = useMemo(
    () => sortCreditAccounts(accounts.filter((a) => a.currentBalance > 0.005)),
    [accounts],
  );

  const plan = useMemo(
    () => buildDebtPlan(accounts, strategy, extra, perAccountExtra),
    [accounts, strategy, extra, perAccountExtra],
  );
  const promoWarnings = useMemo(
    () => promoExpiryWarnings(accounts, plan),
    [accounts, plan],
  );

  const setAccountExtra = (id: string, value: number) =>
    setPerAccountExtra((prev) => ({
      ...prev,
      [id]: Number.isFinite(value) && value > 0 ? value : 0,
    }));

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-3">
        <Link to="/app/debt" className="btn-ghost text-sm py-1.5 shrink-0">
          ← Debt
        </Link>
        <div>
          <h1 className="page-title">Debt Planner</h1>
          <p className="text-sm text-muted mt-1">
            See how an extra monthly payment and a payoff strategy get you
            debt-free faster.
          </p>
        </div>
      </div>

      {loading ? (
        <p className="text-sm text-muted">Loading accounts…</p>
      ) : payable.length === 0 ? (
        <div className="card p-8 text-center">
          <p className="font-medium text-body">No interest-bearing debt</p>
          <p className="text-sm text-muted mt-1">
            Add a credit card or loan with an APR and balance to build a payoff
            plan.
          </p>
          <Link to="/app/debt" className="btn-primary mt-4 inline-block">
            Back to Debt
          </Link>
        </div>
      ) : (
        <>
          <section className="card p-4 space-y-4">
            <div>
              <h2 className="section-title">Strategy</h2>
              <div className="mt-2 grid grid-cols-2 gap-2">
                {(["AVALANCHE", "SNOWBALL"] as PayoffStrategy[]).map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setStrategy(s)}
                    className={clsx(
                      "rounded-xl border px-3 py-2 text-left transition-colors",
                      strategy === s
                        ? "border-primary bg-primary/10"
                        : "border-outline hover:bg-surfaceVariant/50",
                    )}
                  >
                    <span className="font-medium text-body block">
                      {STRATEGY_LABELS[s]}
                    </span>
                    <span className="text-xs text-muted">
                      {STRATEGY_DESCRIPTIONS[s]}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <h2 className="section-title">Extra Per Month</h2>
                <span className="font-mono font-semibold text-moneyColor">
                  {formatUsd(extra)}
                </span>
              </div>
              <input
                type="range"
                min={0}
                max={2000}
                step={25}
                value={extra}
                onChange={(e) => setExtra(Number(e.target.value))}
                className="w-full mt-3 accent-accent"
              />
              <div className="mt-2 flex flex-wrap gap-2">
                {EXTRA_PRESETS.map((amount) => (
                  <button
                    key={amount}
                    type="button"
                    onClick={() => setExtra(amount)}
                    className={clsx(
                      "rounded-full px-3 py-1 text-xs border transition-colors",
                      extra === amount
                        ? "border-primary bg-primary/10 text-body"
                        : "border-outline text-muted hover:bg-surfaceVariant/50",
                    )}
                  >
                    {amount === 0 ? "Minimums" : `+${formatUsd(amount)}`}
                  </button>
                ))}
              </div>
              <p className="text-xs text-muted mt-2">
                Total budget: {formatUsd(plan.monthlyBudget)}/mo
              </p>
            </div>
          </section>

          <HeroCard className="p-6">
            <h2 className="text-center text-sm font-medium opacity-80">
              Plan Result
            </h2>
            {plan.feasible ? (
              <>
                <div className="mt-4 grid grid-cols-2 gap-4 text-center">
                  <div>
                    <p className="text-sm opacity-70">Debt-free</p>
                    <p className="text-xl font-semibold mt-1">
                      {plan.debtFreeDateMs != null
                        ? formatPayoffDate(plan.debtFreeDateMs)
                        : "—"}
                    </p>
                    <p className="text-xs opacity-70 mt-1">
                      {formatMonths(plan.months)}
                    </p>
                  </div>
                  <div>
                    <p className="text-sm opacity-70">Total interest</p>
                    <p className="font-mono text-xl font-semibold mt-1">
                      {formatUsd(plan.totalInterest)}
                    </p>
                  </div>
                </div>
                {plan.interestSaved > 0 || plan.monthsSaved > 0 ? (
                  <div className="mt-4 rounded-xl bg-on-primary-container/10 p-3 text-center text-sm">
                    {plan.interestSaved > 0 ? (
                      <span>
                        Saves{" "}
                        <strong>{formatUsd(plan.interestSaved)}</strong> in
                        interest
                      </span>
                    ) : null}
                    {plan.interestSaved > 0 && plan.monthsSaved > 0 ? " · " : ""}
                    {plan.monthsSaved > 0 ? (
                      <span>
                        <strong>{formatMonths(plan.monthsSaved)}</strong> sooner
                      </span>
                    ) : null}
                    <span className="block text-xs opacity-70 mt-1">
                      vs. paying minimums only
                    </span>
                  </div>
                ) : null}
                {plan.timeline.length > 1 ? (
                  <div className="mt-5">
                    <PayoffChart
                      timeline={plan.timeline}
                      endLabel={
                        plan.debtFreeDateMs != null
                          ? formatPayoffDate(plan.debtFreeDateMs)
                          : "Paid off"
                      }
                    />
                  </div>
                ) : null}
              </>
            ) : (
              <p className="text-center text-sm mt-4">
                These balances won&apos;t be paid off within 100 years at the
                current budget. Increase the extra monthly payment.
              </p>
            )}
          </HeroCard>

          {promoWarnings.length > 0 ? (
            <section className="card p-4 space-y-2">
              <h2 className="section-title">Promotional APR</h2>
              <ul className="space-y-2">
                {promoWarnings.map((warning) => (
                  <li key={warning.accountId} className="text-sm text-body">
                    <span className="font-medium">{warning.name}</span> won&apos;t
                    be paid off before the promo ends in{" "}
                    {warning.monthsUntilExpiry} month
                    {warning.monthsUntilExpiry === 1 ? "" : "s"}.
                    {warning.deferredInterest
                      ? " Deferred interest may be charged if the balance is not cleared."
                      : ""}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          <section className="card p-4 space-y-3">
            <h2 className="section-title">Extra Per Account</h2>
            <p className="text-xs text-muted">
              Commit a fixed extra payment to specific accounts. Whatever is
              left of your monthly extra still funds the {STRATEGY_LABELS[strategy]}{" "}
              target.
            </p>
            <ul className="space-y-2">
              {payable.map((account) => (
                <li
                  key={account.id}
                  className="flex items-center gap-3 rounded-lg bg-surfaceVariant/40 p-3"
                >
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-body truncate">
                      {account.name}
                    </p>
                    <p className="text-xs text-muted">
                      {CREDIT_ACCOUNT_TYPE_LABELS[account.type]} ·{" "}
                      {formatUsd(account.currentBalance)}
                      {account.apr > 0
                        ? ` · ${(account.apr * 100).toFixed(2)}%`
                        : ""}
                    </p>
                  </div>
                  <label className="flex items-center gap-1 shrink-0">
                    <span className="text-sm text-muted">+$</span>
                    <input
                      type="number"
                      min={0}
                      step={25}
                      inputMode="decimal"
                      value={perAccountExtra[account.id] || ""}
                      placeholder="0"
                      onChange={(e) =>
                        setAccountExtra(account.id, Number(e.target.value))
                      }
                      className="w-20 rounded-lg border border-outline bg-surface px-2 py-1 text-right text-sm"
                    />
                    <span className="text-xs text-muted">/mo</span>
                  </label>
                </li>
              ))}
            </ul>
          </section>

          <section className="card p-4 space-y-3">
            <h2 className="section-title">Payoff Order</h2>
            <ol className="space-y-2">
              {plan.accounts.map((a) => (
                <li
                  key={a.accountId}
                  className="flex items-center gap-3 rounded-lg bg-surfaceVariant/40 p-3"
                >
                  <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/15 text-sm font-semibold text-accent">
                    {a.order}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-body truncate">{a.name}</p>
                    <p className="text-xs text-muted">
                      {formatUsd(a.startingBalance)} ·{" "}
                      {formatUsd(a.totalInterest)} interest
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-medium">
                      {a.payoffDateMs != null
                        ? formatPayoffDate(a.payoffDateMs)
                        : "—"}
                    </p>
                    <p className="text-xs text-muted">
                      {Number.isFinite(a.payoffMonths)
                        ? formatMonths(a.payoffMonths)
                        : "Not on track"}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </section>
        </>
      )}
    </div>
  );
}
