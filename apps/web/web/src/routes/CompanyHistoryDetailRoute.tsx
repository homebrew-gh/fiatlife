import { useMemo, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  canSkipInterval,
  generalCategoryForBill,
  nextDueDateMillis,
  type BillFrequency,
} from "../lib/bill";
import { buildCompanyHistory } from "../lib/companyHistory";
import { useBillersData } from "../lib/billersData";
import { useBillsData } from "../lib/billsData";
import { formatUsd } from "../lib/format";

const FREQUENCIES: BillFrequency[] = [
  "WEEKLY",
  "MONTHLY",
  "QUARTERLY",
  "SEMIANNUALLY",
  "ANNUALLY",
];

function formatDate(ms: number | null): string {
  if (ms == null || ms <= 0) return "—";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(ms));
}

function dateInputValue(ms: number | null | undefined): string {
  if (ms == null || ms <= 0) return "";
  const d = new Date(ms);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function parseDateInput(value: string): number | null {
  if (!value.trim()) return null;
  const d = new Date(`${value}T12:00:00`);
  return Number.isFinite(d.getTime()) ? d.getTime() : null;
}

export function CompanyHistoryDetailRoute() {
  const { companyKey: encodedKey } = useParams<{ companyKey: string }>();
  const [searchParams] = useSearchParams();
  const companyKey = encodedKey ? decodeURIComponent(encodedKey) : "";
  const companyName = searchParams.get("name") ?? "Company";
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);

  const {
    allBills,
    loading,
    saving,
    deleteBill,
    cancelSubscription,
    skipInterval,
    reactivateSubscriptionWithSchedule,
    attachStatement,
    getBillById,
  } = useBillsData();
  const { billers, setCompanyArchived, deleteBiller } = useBillersData();

  const [selectedBillId, setSelectedBillId] = useState("");
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [activateBillId, setActivateBillId] = useState<string | null>(null);
  const [activateFrequency, setActivateFrequency] =
    useState<BillFrequency>("MONTHLY");
  const [activateDate, setActivateDate] = useState("");
  const [message, setMessage] = useState<string | null>(null);

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

  const companyMeta = history.companies.find((c) => c.key === companyKey);
  const payments = history.paymentsByCompanyKey.get(companyKey) ?? [];
  const statements = history.statementsByCompanyKey.get(companyKey) ?? [];
  const companyBills = history.billsByCompanyKey.get(companyKey) ?? [];
  const subscriptions = companyBills.filter(
    (b) => generalCategoryForBill(b) === "SUBSCRIPTION",
  );

  const effectiveSelectedBillId =
    selectedBillId || companyBills[0]?.id || "";

  if (!companyKey) {
    return <Navigate to="/app/bills/companies" replace />;
  }

  const onUploadStatement = async (file: File) => {
    const targetId = effectiveSelectedBillId;
    const item = getBillById(targetId);
    if (!item) {
      setMessage("No bill found for this company.");
      return;
    }
    try {
      await attachStatement(item, file);
      setMessage(`Statement uploaded to ${item.bill.name}.`);
    } catch {
      setMessage("Upload failed.");
    }
  };

  const onDeleteCompany = async () => {
    for (const bill of companyBills) {
      const item = getBillById(bill.id);
      if (item) await deleteBill(item);
    }
    if (companyKey.startsWith("id:")) {
      const id = companyKey.slice("id:".length);
      const biller = billers.find((b) => b.id === id);
      if (biller) await deleteBiller(biller);
    } else if (companyKey.startsWith("name:")) {
      const normalized = companyKey.slice("name:".length);
      const biller = billers.find((b) => b.normalizedName === normalized);
      if (biller) await deleteBiller(biller);
    }
    navigate("/app/bills/companies", { replace: true });
  };

  const selectBillItem = (billId: string) => getBillById(billId);

  return (
    <div className="space-y-5">
      <div>
        <Link
          to="/app/bills/companies"
          className="btn-ghost text-sm mb-2 inline-block"
        >
          ← Companies
        </Link>
        <h1 className="page-title">{companyName}</h1>
        {companyMeta ? (
          <p className="text-sm text-muted mt-1">
            {companyMeta.billCount} bill
            {companyMeta.billCount === 1 ? "" : "s"} ·{" "}
            {formatUsd(companyMeta.totalPaid)} paid total
          </p>
        ) : null}
      </div>

      {message ? (
        <p className="text-sm text-muted" role="status">
          {message}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="btn-ghost text-sm"
          disabled={saving}
          onClick={() =>
            void setCompanyArchived(
              companyKey,
              companyName,
              !companyMeta?.isArchived,
            )
          }
        >
          {companyMeta?.isArchived ? "Unarchive company" : "Archive company"}
        </button>
        <button
          type="button"
          className="btn-ghost text-sm text-error"
          disabled={saving}
          onClick={() => setShowDeleteConfirm(true)}
        >
          Delete company
        </button>
      </div>
      <p className="text-xs text-muted">
        Deletion is permanent locally. Nostr events may still exist depending on
        relay deletion policy.
      </p>

      {subscriptions.length > 0 ? (
        <section className="space-y-3">
          <h2 className="font-medium text-sm tracking-wider text-muted">
            Subscriptions
          </h2>
          {subscriptions.map((sub) => {
            const item = selectBillItem(sub.id);
            return (
              <div key={sub.id} className="card p-4 space-y-2">
                <p className="font-medium">{sub.name}</p>
                <p className="text-xs text-muted">
                  {sub.isCancelled ? "Cancelled" : "Active"}
                </p>
                <div className="flex flex-wrap gap-2">
                  {!sub.isCancelled && canSkipInterval(sub) && item ? (
                    <button
                      type="button"
                      className="btn-ghost text-sm"
                      disabled={saving}
                      onClick={async () => {
                        await skipInterval(item);
                        setMessage("Skipped one billing interval.");
                      }}
                    >
                      Skip interval
                    </button>
                  ) : null}
                  {!sub.isCancelled && item ? (
                    <button
                      type="button"
                      className="btn-ghost text-sm"
                      disabled={saving}
                      onClick={async () => {
                        await cancelSubscription(item);
                        setMessage("Subscription cancelled.");
                      }}
                    >
                      Cancel
                    </button>
                  ) : null}
                  {sub.isCancelled ? (
                    <button
                      type="button"
                      className="btn-ghost text-sm"
                      disabled={saving}
                      onClick={() => {
                        setActivateBillId(sub.id);
                        setActivateFrequency(sub.frequency);
                        setActivateDate(
                          dateInputValue(nextDueDateMillis(sub, Date.now())),
                        );
                      }}
                    >
                      Activate
                    </button>
                  ) : null}
                  <Link
                    to={`/app/bills/${sub.id}`}
                    className="btn-ghost text-sm"
                  >
                    Open bill
                  </Link>
                </div>
              </div>
            );
          })}
        </section>
      ) : null}

      <section className="space-y-3">
        <h2 className="font-medium text-sm tracking-wider text-muted">
          Paid History
        </h2>
        {loading ? (
          <p className="text-sm text-muted">Loading…</p>
        ) : payments.length === 0 ? (
          <p className="text-sm text-muted">
            No payments recorded for this company yet.
          </p>
        ) : (
          <ul className="space-y-2">
            {payments.map((p) => (
              <li key={p.id} className="card px-4 py-3">
                <div className="flex justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-medium truncate">{p.billName}</p>
                    <p className="text-xs text-muted">
                      {formatDate(p.paidDate)}
                      {p.hasInvoiceOrStatement
                        ? " · Has statement"
                        : ""}
                    </p>
                  </div>
                  <p className="money shrink-0">{formatUsd(p.amount)}</p>
                </div>
                <Link
                  to={`/app/bills/${p.billId}`}
                  className="text-xs text-primary underline mt-1 inline-block"
                >
                  Open bill
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="font-medium text-sm tracking-wider text-muted">
          Statements & Attachments
        </h2>
        {companyBills.length > 0 ? (
          <div className="card p-4 space-y-3">
            <div>
              <label className="label" htmlFor="stmt-bill">
                Upload to bill
              </label>
              <select
                id="stmt-bill"
                className="input"
                value={effectiveSelectedBillId}
                onChange={(e) => setSelectedBillId(e.target.value)}
              >
                {companyBills.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
            <input
              ref={fileRef}
              type="file"
              className="hidden"
              accept="*/*"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) void onUploadStatement(file);
                e.target.value = "";
              }}
            />
            <button
              type="button"
              className="btn-primary text-sm"
              disabled={saving}
              onClick={() => fileRef.current?.click()}
            >
              Upload statement
            </button>
          </div>
        ) : null}
        {statements.length === 0 ? (
          <p className="text-sm text-muted">No statements on file yet.</p>
        ) : (
          <ul className="space-y-2">
            {statements.map((s) => (
              <li key={s.id} className="card px-4 py-3">
                <p className="font-medium">{s.label}</p>
                <p className="text-xs text-muted">
                  {s.billName} · {formatDate(s.addedAt)}
                </p>
                <Link
                  to={`/app/bills/${s.billId}`}
                  className="text-xs text-primary underline mt-1 inline-block"
                >
                  Open bill
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {companyBills.length > 0 ? (
        <section className="space-y-2">
          <h2 className="font-medium text-sm tracking-wider text-muted">
            Bills
          </h2>
          <div className="flex flex-wrap gap-2">
            {companyBills.map((b) => (
              <Link
                key={b.id}
                to={`/app/bills/${b.id}`}
                className="text-xs px-2 py-0.5 rounded-pill bg-surfaceVariant text-body hover:opacity-80"
              >
                {b.name}
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      {showDeleteConfirm ? (
        <div
          className="modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="card w-full max-w-md p-5">
            <h2 className="page-title text-xl">Delete Company?</h2>
            <p className="text-sm text-muted mt-2">
              This will delete all company-associated bills in FiatLife. Relay-side
              Nostr history may still remain.
            </p>
            <div className="flex gap-2 mt-4">
              <button
                type="button"
                className="btn-ghost flex-1"
                onClick={() => setShowDeleteConfirm(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary flex-1 text-error"
                disabled={saving}
                onClick={() => void onDeleteCompany()}
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {activateBillId ? (
        <div
          className="modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4"
          role="dialog"
          aria-modal="true"
        >
          <div className="card w-full max-w-md p-5 space-y-3">
            <h2 className="page-title text-xl">Reactivate Subscription</h2>
            <p className="text-sm text-muted">
              Set the new billing schedule for this subscription.
            </p>
            <div>
              <label className="label" htmlFor="activate-freq">
                Frequency
              </label>
              <select
                id="activate-freq"
                className="input"
                value={activateFrequency}
                onChange={(e) =>
                  setActivateFrequency(e.target.value as BillFrequency)
                }
              >
                {FREQUENCIES.map((f) => (
                  <option key={f} value={f}>
                    {f.charAt(0) + f.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="activate-date">
                Next billing date
              </label>
              <input
                id="activate-date"
                type="date"
                className="input"
                value={activateDate}
                onChange={(e) => setActivateDate(e.target.value)}
              />
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                className="btn-ghost flex-1"
                onClick={() => setActivateBillId(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-primary flex-1"
                disabled={saving || !activateDate}
                onClick={async () => {
                  const item = selectBillItem(activateBillId);
                  const millis = parseDateInput(activateDate);
                  if (!item || millis == null) return;
                  await reactivateSubscriptionWithSchedule(
                    item,
                    activateFrequency,
                    millis,
                  );
                  setActivateBillId(null);
                  setMessage("Subscription reactivated.");
                }}
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
