import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import { buildCompanyHistory } from "../lib/companyHistory";
import { useBillersData } from "../lib/billersData";
import { useBillsData } from "../lib/billsData";
import { formatUsd } from "../lib/format";

function formatDate(ms: number | null): string {
  if (ms == null || ms <= 0) return "—";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(ms));
}

export function CompanyHistoryRoute() {
  const { allBills, loading } = useBillsData();
  const { billers } = useBillersData();
  const [showArchived, setShowArchived] = useState(false);

  const history = useMemo(() => {
    const cypherIds = new Set(
      allBills.filter((b) => b.isCypherLog).map((b) => b.bill.id),
    );
    return buildCompanyHistory(
      allBills.map((b) => b.bill),
      billers,
      cypherIds,
    );
  }, [allBills, billers]);

  const companies = history.companies.filter((c) =>
    showArchived ? c.isArchived : !c.isArchived,
  );

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <Link to="/app/bills" className="btn-ghost text-sm mb-2 inline-block">
            ← Bills
          </Link>
          <h1 className="page-title">Company History</h1>
          <p className="text-sm text-muted mt-1">
            Payments and statements grouped by biller.
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm shrink-0 cursor-pointer">
          <input
            type="checkbox"
            checked={showArchived}
            onChange={(e) => setShowArchived(e.target.checked)}
          />
          Show archived
        </label>
      </div>

      {loading ? (
        <p className="text-sm text-muted">Loading…</p>
      ) : companies.length === 0 ? (
        <div className="card p-8 text-center">
          <p className="text-muted text-sm">
            No companies yet. Add biller names to your bills to track history.
          </p>
        </div>
      ) : (
        <ul className="space-y-3">
          {companies.map((company) => {
            const payments = history.paymentsByCompanyKey.get(company.key) ?? [];
            const statements =
              history.statementsByCompanyKey.get(company.key) ?? [];
            const bills = history.billsByCompanyKey.get(company.key) ?? [];

            const detailPath = `/app/bills/companies/${encodeURIComponent(company.key)}?name=${encodeURIComponent(company.name)}`;

            return (
              <li key={company.key} className="card p-4">
                <Link to={detailPath} className="block hover:opacity-90">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium">{company.name}</p>
                    <p className="text-xs text-muted mt-0.5">
                      {company.billCount} bill
                      {company.billCount === 1 ? "" : "s"} ·{" "}
                      {company.paymentCount} payment
                      {company.paymentCount === 1 ? "" : "s"}
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="money text-lg">
                      {formatUsd(company.totalPaid)}
                    </p>
                    <p className="text-xs text-muted">
                      Last paid {formatDate(company.lastPaidDate)}
                    </p>
                  </div>
                </div>

                {payments.length > 0 ? (
                  <div className="mt-3 border-t border-outline pt-3">
                    <p className="text-xs tracking-wider text-muted font-medium mb-2">
                      Recent Payments
                    </p>
                    <ul className="space-y-1">
                      {payments.slice(0, 3).map((p) => (
                        <li
                          key={p.id}
                          className="flex justify-between text-sm gap-2"
                        >
                          <span className="truncate text-muted">
                            {p.billName} · {formatDate(p.paidDate)}
                          </span>
                          <span className="money shrink-0">
                            {formatUsd(p.amount)}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {statements.length > 0 ? (
                  <p className="text-xs text-muted mt-2">
                    {statements.length} statement
                    {statements.length === 1 ? "" : "s"} on file
                  </p>
                ) : null}

                <div className="flex flex-wrap gap-2 mt-3">
                  {bills.slice(0, 4).map((b) => (
                    <span
                      key={b.id}
                      className={clsx(
                        "text-xs px-2 py-0.5 rounded-pill",
                        "bg-surfaceVariant text-body",
                      )}
                    >
                      {b.name}
                    </span>
                  ))}
                </div>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
