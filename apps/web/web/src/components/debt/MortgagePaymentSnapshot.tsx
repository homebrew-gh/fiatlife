import { formatUsd } from "../../lib/format";
import {
  currentMortgagePaymentSnapshot,
  formatMortgageDate,
  type MortgagePaymentSnapshot,
} from "../../lib/mortgage";
import type { CreditAccount } from "../../lib/creditAccount";

export function snapshotForAccount(
  account: CreditAccount | undefined | null,
): MortgagePaymentSnapshot | null {
  if (!account || account.type !== "MORTGAGE") return null;
  return currentMortgagePaymentSnapshot(account);
}

export function MortgagePaymentSnapshotCard({
  snapshot,
  compact = false,
}: {
  snapshot: MortgagePaymentSnapshot;
  compact?: boolean;
}) {
  if (compact) {
    return (
      <p className="text-xs text-muted mt-0.5">
        This payment {formatUsd(snapshot.principal + snapshot.extraPrincipal)}{" "}
        principal · {formatUsd(snapshot.interest)} interest
        {snapshot.remainingBalance > 0
          ? ` · bal ${formatUsd(snapshot.remainingBalance)}`
          : ""}
      </p>
    );
  }

  const payoff = snapshot.payoffDateMs
    ? formatMortgageDate(snapshot.payoffDateMs)
    : "—";

  return (
    <section className="card p-4 space-y-3">
      <h2 className="font-medium">This mortgage payment</h2>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted">Principal</dt>
        <dd className="money text-success">
          {formatUsd(snapshot.principal + snapshot.extraPrincipal)}
        </dd>
        <dt className="text-muted">Interest</dt>
        <dd className="money text-warn">{formatUsd(snapshot.interest)}</dd>
        {snapshot.escrow > 0 ? (
          <>
            <dt className="text-muted">Escrow</dt>
            <dd className="money">{formatUsd(snapshot.escrow)}</dd>
          </>
        ) : null}
        {snapshot.pmi > 0 ? (
          <>
            <dt className="text-muted">PMI</dt>
            <dd className="money">{formatUsd(snapshot.pmi)}</dd>
          </>
        ) : null}
        <dt className="text-muted">Servicer draft</dt>
        <dd className="money font-semibold">{formatUsd(snapshot.servicerDraft)}</dd>
        <dt className="text-muted">Balance</dt>
        <dd className="money">{formatUsd(snapshot.remainingBalance)}</dd>
        <dt className="text-muted">Principal paid (est.)</dt>
        <dd className="money">{formatUsd(snapshot.principalPaid)}</dd>
        <dt className="text-muted">Interest paid (est.)</dt>
        <dd className="money">{formatUsd(snapshot.interestPaid)}</dd>
        <dt className="text-muted">Payments left</dt>
        <dd>{snapshot.paymentsRemaining}</dd>
        <dt className="text-muted">Payoff</dt>
        <dd>{payoff}</dd>
      </dl>
    </section>
  );
}
