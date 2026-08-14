import { useMemo, useState } from "react";
import clsx from "clsx";
import type { CreditAccount } from "../../lib/creditAccount";
import { formatUsd } from "../../lib/format";
import {
  formatMortgageDate,
  scheduleForMortgageAccount,
  type MortgageScheduleRow,
} from "../../lib/mortgage";

function ScheduleTable({
  rows,
  currentPayment,
  showAll,
}: {
  rows: MortgageScheduleRow[];
  currentPayment: number;
  showAll: boolean;
}) {
  const visible = showAll ? rows : rows.slice(0, 12);

  return (
    <div className="overflow-x-auto -mx-1">
      <table className="w-full text-sm min-w-[32rem]">
        <thead>
          <tr className="text-left text-muted border-b border-outline">
            <th className="py-2 pr-2 font-medium">#</th>
            <th className="py-2 pr-2 font-medium">Date</th>
            <th className="py-2 pr-2 font-medium text-right">Payment</th>
            <th className="py-2 pr-2 font-medium text-right">Principal</th>
            <th className="py-2 pr-2 font-medium text-right">Interest</th>
            <th className="py-2 font-medium text-right">Balance</th>
          </tr>
        </thead>
        <tbody>
          {visible.map((row) => {
            const isCurrent = row.paymentNumber === currentPayment;
            const isPaid = row.paymentNumber < currentPayment;
            return (
              <tr
                key={row.paymentNumber}
                className={clsx(
                  "border-b border-outline/50",
                  isCurrent && "bg-primaryContainer/30",
                  isPaid && "opacity-60",
                )}
              >
                <td className="py-2 pr-2 font-mono">{row.paymentNumber}</td>
                <td className="py-2 pr-2">{formatMortgageDate(row.dateMs)}</td>
                <td className="py-2 pr-2 text-right font-mono">
                  {formatUsd(row.payment)}
                </td>
                <td className="py-2 pr-2 text-right font-mono text-success">
                  {formatUsd(row.principal + row.extraPrincipal)}
                </td>
                <td className="py-2 pr-2 text-right font-mono text-warn">
                  {formatUsd(row.interest)}
                </td>
                <td className="py-2 text-right font-mono">
                  {formatUsd(row.balance)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function MortgageScheduleSection({
  account,
}: {
  account: CreditAccount;
}) {
  const [showAll, setShowAll] = useState(false);

  const schedule = useMemo(
    () => scheduleForMortgageAccount(account),
    [account],
  );

  if (!schedule) {
    return (
      <section className="card p-4 space-y-2">
        <h2 className="section-title">Mortgage Schedule</h2>
        <p className="text-sm text-muted">
          Add loan amount, term, and interest rate to generate an amortization
          schedule. Edit this account to fill in mortgage details.
        </p>
      </section>
    );
  }

  const { summary, rows } = schedule;
  const currentPayment = Math.min(
    summary.paymentsElapsed + 1,
    rows.length,
  );
  const payoffLabel = summary.payoffDateMs
    ? formatMortgageDate(summary.payoffDateMs)
    : "—";

  return (
    <section className="card p-4 space-y-4">
      <div>
        <h2 className="section-title">Mortgage Schedule</h2>
        <p className="text-sm text-muted mt-1">
          Principal &amp; interest amortization based on your loan terms.
        </p>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
        <div className="rounded-lg bg-surfaceVariant/60 p-3">
          <p className="text-muted">Monthly P&amp;I</p>
          <p className="font-mono font-semibold mt-1">
            {formatUsd(summary.monthlyPayment)}
          </p>
        </div>
        <div className="rounded-lg bg-surfaceVariant/60 p-3">
          <p className="text-muted">Total interest</p>
          <p className="font-mono font-semibold mt-1">
            {formatUsd(summary.totalInterest)}
          </p>
        </div>
        <div className="rounded-lg bg-surfaceVariant/60 p-3">
          <p className="text-muted">Payoff date</p>
          <p className="font-semibold mt-1">{payoffLabel}</p>
        </div>
        <div className="rounded-lg bg-surfaceVariant/60 p-3">
          <p className="text-muted">Payments left</p>
          <p className="font-semibold mt-1">{summary.paymentsRemaining}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 text-sm">
        <div className="flex justify-between gap-2">
          <span className="text-muted">Loan amount</span>
          <span className="font-mono">{formatUsd(summary.loanAmount)}</span>
        </div>
        <div className="flex justify-between gap-2">
          <span className="text-muted">Rate</span>
          <span>{(account.apr * 100).toFixed(3)}%</span>
        </div>
        <div className="flex justify-between gap-2">
          <span className="text-muted">Principal paid (est.)</span>
          <span className="font-mono">{formatUsd(summary.principalPaid)}</span>
        </div>
        <div className="flex justify-between gap-2">
          <span className="text-muted">Interest paid (est.)</span>
          <span className="font-mono">{formatUsd(summary.interestPaid)}</span>
        </div>
      </div>

      <ScheduleTable
        rows={rows}
        currentPayment={currentPayment}
        showAll={showAll}
      />

      {rows.length > 12 ? (
        <button
          type="button"
          className="btn-ghost text-sm w-full"
          onClick={() => setShowAll((v) => !v)}
        >
          {showAll ? "Show fewer payments" : `Show all ${rows.length} payments`}
        </button>
      ) : null}
    </section>
  );
}
