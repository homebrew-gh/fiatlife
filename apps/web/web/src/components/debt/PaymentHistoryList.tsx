import type { BillPayment } from "../../lib/bill";
import { formatUsd } from "../../lib/format";

function formatPaymentDate(ms: number): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(ms));
}

export function PaymentHistoryList({
  payments,
}: {
  payments: BillPayment[];
}) {
  const sorted = [...payments].sort((a, b) => b.date - a.date);

  if (sorted.length === 0) {
    return (
      <p className="text-sm text-muted">
        No payments recorded yet. Payments are logged when you lower the balance
        or mark the linked bill paid on Android.
      </p>
    );
  }

  return (
    <ul className="divide-y divide-outline">
      {sorted.map((payment, index) => (
        <li
          key={`${payment.date}-${payment.amount}-${index}`}
          className="flex items-center justify-between py-2.5 text-sm"
        >
          <span className="text-muted">{formatPaymentDate(payment.date)}</span>
          <span className="font-mono font-medium text-money">
            {formatUsd(payment.amount)}
          </span>
        </li>
      ))}
    </ul>
  );
}
