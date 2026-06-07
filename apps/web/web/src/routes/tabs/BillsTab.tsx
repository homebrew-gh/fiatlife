import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { BillSheet, type BillSheetInput } from "../../components/BillSheet";
import { PastDueAutopayDialog } from "../../components/bills/PastDueAutopayDialog";
import { PayBillDialog } from "../../components/bills/PayBillDialog";
import {
  ALL_GENERAL_CATEGORIES,
  GENERAL_CATEGORY_LABELS,
  dueAmountInMonth,
  dueAmountInYear,
  effectiveAmountDue,
  formatDueCountdown,
  frequencyLabel,
  generalCategoryForBill,
  isCreditOrLoan,
  isPaidForCurrentCycle,
  isPastDue,
  subcategoryLabel,
  effectiveSubcategory,
  type BillGeneralCategory,
  type BillWithSource,
} from "../../lib/bill";
import {
  billsDueInNext7Days,
  categoryTotals,
  computePaymentBreakdown,
  costBasisBills,
  otherBillsByCategory,
  pastDueAutopayBills,
  visibleBills,
} from "../../lib/billsAggregation";
import { useBankAccountsData } from "../../lib/bankAccountsData";
import { useBillersData } from "../../lib/billersData";
import { useBillsData } from "../../lib/billsData";
import type { CreditAccount } from "../../lib/creditAccount";
import { useDebtData } from "../../lib/debtData";
import { formatUsd } from "../../lib/format";
import { useRecordBillPayment } from "../../lib/useBillPayment";
import { useSaveBill } from "../../lib/useSaveBill";
import { useNow } from "../../lib/useNow";

export function BillsTab() {
  const navigate = useNavigate();
  const {
    bills: activeBills,
    loading,
    error,
    saving,
    reload,
    deleteBill,
  } = useBillsData();
  const recordBillPayment = useRecordBillPayment();
  const saveBillWithBiller = useSaveBill();
  const { billers } = useBillersData();
  const { accounts: bankAccounts } = useBankAccountsData();
  const { accounts: creditAccounts } = useDebtData();

  const [annualView, setAnnualView] = useState(false);
  const [filter, setFilter] = useState<BillGeneralCategory | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [editing, setEditing] = useState<BillWithSource | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [payItem, setPayItem] = useState<BillWithSource | null>(null);
  const [showAutopayDialog, setShowAutopayDialog] = useState(false);
  const [autopayDismissed, setAutopayDismissed] = useState(false);
  const [collapsedSubs, setCollapsedSubs] = useState<Set<string>>(new Set());
  const [showCatBreakdown, setShowCatBreakdown] = useState(false);

  const now = useNow();
  const monthAnchor = now;

  const costBasis = useMemo(
    () => costBasisBills(activeBills, creditAccounts),
    [activeBills, creditAccounts],
  );

  const visible = useMemo(
    () => visibleBills(activeBills, creditAccounts, now),
    [activeBills, creditAccounts, now],
  );

  const filteredVisible = useMemo(() => {
    if (!filter) return visible;
    return visible.filter(
      (item) => generalCategoryForBill(item.bill) === filter,
    );
  }, [visible, filter]);

  const dueIn7 = useMemo(
    () => billsDueInNext7Days(visible, now),
    [visible, now],
  );
  const dueIn7Ids = useMemo(
    () => new Set(dueIn7.map((i) => i.bill.id)),
    [dueIn7],
  );

  const byCategory = useMemo(
    () => otherBillsByCategory(filteredVisible, dueIn7Ids, now),
    [filteredVisible, dueIn7Ids, now],
  );

  const pastDueAutopay = useMemo(
    () => pastDueAutopayBills(visible, now),
    [visible, now],
  );

  useEffect(() => {
    if (pastDueAutopay.length > 0 && !autopayDismissed) {
      setShowAutopayDialog(true);
    }
  }, [pastDueAutopay.length, autopayDismissed]);

  const allCostBills = useMemo(
    () => costBasis.map((i) => i.bill),
    [costBasis],
  );

  const monthlyTotal = useMemo(
    () =>
      allCostBills.reduce(
        (sum, b) =>
          sum +
          (annualView
            ? dueAmountInYear(b, monthAnchor)
            : dueAmountInMonth(b, monthAnchor)),
        0,
      ),
    [allCostBills, annualView, monthAnchor],
  );

  const catTotals = useMemo(
    () => categoryTotals(allCostBills, monthAnchor, annualView),
    [allCostBills, monthAnchor, annualView],
  );

  const paymentBreakdown = useMemo(
    () =>
      computePaymentBreakdown(
        costBasis,
        bankAccounts,
        creditAccounts,
        monthAnchor,
        annualView,
      ),
    [costBasis, bankAccounts, creditAccounts, monthAnchor, annualView],
  );

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await reload();
    } finally {
      setRefreshing(false);
    }
  };

  const onSaveBill = async (input: BillSheetInput) => {
    const savedId = await saveBillWithBiller(input, editing);
    if (!editing) navigate(`/app/bills/${savedId}`);
    setSheetOpen(false);
    setEditing(null);
  };

  const onDeleteBill = async (item: BillWithSource) => {
    const linked = item.bill.linkedCreditAccountId;
    const message = linked
      ? `Delete "${item.bill.name}"? This bill is linked to a debt account.`
      : `Delete "${item.bill.name}"? This syncs to your relay.`;
    if (!window.confirm(message)) return;
    setDeletingId(item.bill.id);
    try {
      await deleteBill(item);
    } finally {
      setDeletingId(null);
    }
  };

  const onMarkPaid = (item: BillWithSource) => {
    setPayItem(item);
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="page-title">Bills</h1>
          <p className="text-sm text-muted mt-1">
            Synced from your Nostr relay — changes publish to Android too.
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => void onRefresh()}
            disabled={refreshing}
          >
            {refreshing ? "…" : "Sync"}
          </button>
          <button
            type="button"
            className="btn-primary text-sm"
            onClick={() => {
              setEditing(null);
              setSheetOpen(true);
            }}
          >
            + Add
          </button>
        </div>
      </div>

      <div className="flex gap-2">
        <Link to="/app/bills/companies" className="btn-ghost text-sm">
          Company History
        </Link>
      </div>

      <section className="card p-5 bg-dollar-gradient">
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs tracking-wider text-muted font-medium">
            {annualView ? "Annual Total" : "Monthly Total"}
          </p>
          <button
            type="button"
            className="text-xs text-primary underline"
            onClick={() => setAnnualView((v) => !v)}
          >
            {annualView ? "Show monthly" : "Show annual"}
          </button>
        </div>
        <p className="money text-3xl mt-1">{formatUsd(monthlyTotal)}</p>
        {catTotals.size > 0 ? (
          <button
            type="button"
            className="text-xs text-primary underline mt-1"
            onClick={() => setShowCatBreakdown((v) => !v)}
          >
            {showCatBreakdown ? "Hide breakdown" : "Show breakdown"}
          </button>
        ) : null}
        {showCatBreakdown ? (
          <div className="mt-3 border-t border-border/50 pt-3 space-y-1">
            <p className="text-xs tracking-wider text-muted font-medium">
              By Category
            </p>
            {ALL_GENERAL_CATEGORIES.map((cat) => {
              const total = catTotals.get(cat) ?? 0;
              if (total <= 0) return null;
              return (
                <div key={cat} className="flex justify-between text-sm gap-2">
                  <span className="truncate text-muted">
                    {GENERAL_CATEGORY_LABELS[cat]}
                  </span>
                  <span className="money shrink-0">{formatUsd(total)}</span>
                </div>
              );
            })}
          </div>
        ) : null}
        {paymentBreakdown.rows.length > 0 ? (
          <div className="mt-4 border-t border-border/50 pt-3 space-y-1">
            <p className="text-xs tracking-wider text-muted font-medium">
              By Payment Account
            </p>
            {paymentBreakdown.rows.map((row) => (
              <div
                key={row.id}
                className="flex justify-between text-sm gap-2"
              >
                <span className="truncate text-muted">
                  {row.name}
                  {row.isCredit ? " (credit)" : ""}
                </span>
                <span className="money shrink-0">{formatUsd(row.total)}</span>
              </div>
            ))}
            {(paymentBreakdown.subtotalBanks > 0 ||
              paymentBreakdown.subtotalCredit > 0) && (
              <div className="flex justify-between text-xs text-muted pt-1">
                <span>
                  Banks {formatUsd(paymentBreakdown.subtotalBanks)} · Credit{" "}
                  {formatUsd(paymentBreakdown.subtotalCredit)}
                </span>
              </div>
            )}
          </div>
        ) : null}
      </section>

      <div className="flex gap-2 overflow-x-auto pb-1">
        <FilterChip
          label="All"
          active={filter === null}
          onClick={() => setFilter(null)}
        />
        {ALL_GENERAL_CATEGORIES.map((cat) => {
          const total = catTotals.get(cat) ?? 0;
          if (total <= 0 && filter !== cat) return null;
          return (
            <FilterChip
              key={cat}
              label={GENERAL_CATEGORY_LABELS[cat]}
              active={filter === cat}
              onClick={() => setFilter(filter === cat ? null : cat)}
            />
          );
        })}
      </div>

      {loading ? <p className="text-muted text-sm">Loading bills…</p> : null}
      {error ? (
        <div className="card-quiet p-4 text-sm text-error" role="alert">
          {error}
        </div>
      ) : null}

      {dueIn7.length > 0 ? (
        <section className="space-y-3">
          <h2 className="font-medium text-sm tracking-wider text-muted">
            Due In 7 Days
          </h2>
          <ul className="space-y-3">
            {dueIn7.map((item) => (
              <BillCard
                key={item.bill.id}
                item={item}
                now={now}
                saving={saving}
                deletingId={deletingId}
                onOpen={() => navigate(`/app/bills/${item.bill.id}`)}
                onEdit={() => {
                  setEditing(item);
                  setSheetOpen(true);
                }}
                onDelete={() => void onDeleteBill(item)}
                creditAccounts={creditAccounts}
                onMarkPaid={() => void onMarkPaid(item)}
              />
            ))}
          </ul>
        </section>
      ) : null}

      {Array.from(byCategory.entries()).map(([cat, items]) => {
        if (items.length === 0) return null;
        const isSubscription = cat === "SUBSCRIPTION";
        const grouped = isSubscription
          ? groupBySubcategory(items)
          : new Map([["", items]]);

        return (
          <section key={cat} className="space-y-3">
            <h2 className="font-medium">
              {GENERAL_CATEGORY_LABELS[cat]}
              <span className="text-muted font-normal text-sm ml-2">
                {formatUsd(catTotals.get(cat) ?? 0)}
                {annualView ? "/yr" : "/mo"}
              </span>
            </h2>
            {Array.from(grouped.entries()).map(([sub, subItems]) => {
              const subKey = sub ? `${cat}:${sub}` : "";
              const collapsible = Boolean(sub);
              const collapsed = collapsible && collapsedSubs.has(subKey);
              const subtotal = subItems.reduce(
                (sum, item) =>
                  sum +
                  (annualView
                    ? dueAmountInYear(item.bill, monthAnchor)
                    : dueAmountInMonth(item.bill, monthAnchor)),
                0,
              );
              return (
                <div key={sub || cat} className="space-y-3">
                  {sub ? (
                    <button
                      type="button"
                      className="flex w-full items-center justify-between gap-2 text-sm text-muted"
                      onClick={() =>
                        setCollapsedSubs((prev) => {
                          const next = new Set(prev);
                          if (next.has(subKey)) next.delete(subKey);
                          else next.add(subKey);
                          return next;
                        })
                      }
                    >
                      <span className="flex items-center gap-1">
                        <span aria-hidden>{collapsed ? "▸" : "▾"}</span>
                        {subcategoryLabel(sub)}
                        <span className="text-xs">({subItems.length})</span>
                      </span>
                      <span className="money">
                        {formatUsd(subtotal)}
                        {annualView ? "/yr" : "/mo"}
                      </span>
                    </button>
                  ) : null}
                  {collapsed ? null : (
                    <ul className="space-y-3">
                      {subItems.map((item) => (
                        <BillCard
                          key={item.bill.id}
                          item={item}
                          now={now}
                          saving={saving}
                          deletingId={deletingId}
                          onOpen={() => navigate(`/app/bills/${item.bill.id}`)}
                          onEdit={() => {
                            setEditing(item);
                            setSheetOpen(true);
                          }}
                          onDelete={() => void onDeleteBill(item)}
                          creditAccounts={creditAccounts}
                          onMarkPaid={() => void onMarkPaid(item)}
                        />
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </section>
        );
      })}

      {!loading && visible.length === 0 ? (
        <div className="card p-8 text-center">
          <p className="text-dollarBill text-3xl font-serif mb-2" aria-hidden>
            $
          </p>
          <p className="text-muted text-sm">
            No bills on your relay yet. Add one or create bills in the Android
            app.
          </p>
        </div>
      ) : null}

      <BillSheet
        open={sheetOpen}
        editing={editing}
        billers={billers}
        bankAccounts={bankAccounts}
        creditAccounts={creditAccounts}
        onClose={() => {
          setSheetOpen(false);
          setEditing(null);
        }}
        onSave={onSaveBill}
        saving={saving}
      />

      <PayBillDialog
        item={payItem}
        open={payItem != null}
        onClose={() => setPayItem(null)}
        onConfirm={async (amount, newBalance, paymentDate) => {
          if (payItem) {
            await recordBillPayment(payItem, amount, newBalance, paymentDate);
          }
        }}
        saving={saving}
      />

      <PastDueAutopayDialog
        bills={pastDueAutopay}
        open={showAutopayDialog}
        onClose={() => setShowAutopayDialog(false)}
        onConfirm={async (items) => {
          for (const item of items) {
            await recordBillPayment(item, effectiveAmountDue(item.bill));
          }
        }}
        onDismiss={() => {
          setAutopayDismissed(true);
          setShowAutopayDialog(false);
        }}
        saving={saving}
      />
    </div>
  );
}

function groupBySubcategory(
  items: BillWithSource[],
): Map<string, BillWithSource[]> {
  const map = new Map<string, BillWithSource[]>();
  for (const item of items) {
    const sub = effectiveSubcategory(item.bill);
    const list = map.get(sub) ?? [];
    list.push(item);
    map.set(sub, list);
  }
  return map;
}

function creditBalanceForBill(
  bill: BillWithSource["bill"],
  creditAccounts: CreditAccount[],
): number {
  if (bill.linkedCreditAccountId) {
    const linked = creditAccounts.find((a) => a.id === bill.linkedCreditAccountId);
    return linked?.currentBalance ?? 0;
  }
  return bill.creditCardDetails?.currentBalance ?? 0;
}

function BillCard({
  item,
  now,
  saving,
  deletingId,
  creditAccounts,
  onOpen,
  onEdit,
  onDelete,
  onMarkPaid,
}: {
  item: BillWithSource;
  now: number;
  saving: boolean;
  deletingId: string | null;
  creditAccounts: CreditAccount[];
  onOpen: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onMarkPaid: () => void;
}) {
  const { bill } = item;
  const pastDue = isPastDue(bill, now);
  const paidCycle = isPaidForCurrentCycle(bill, now);
  const cat = generalCategoryForBill(bill);
  const creditBalance = creditBalanceForBill(bill, creditAccounts);
  const showPayButton = isCreditOrLoan(bill)
    ? creditBalance > 0
    : !paidCycle;

  return (
    <li className="card px-4 py-3">
      <div className="flex items-center gap-3">
        {showPayButton ? (
          <button
            type="button"
            className="btn-paid"
            disabled={saving}
            onClick={onMarkPaid}
          >
            Paid
          </button>
        ) : paidCycle ? (
          <span className="badge-success text-xs px-2.5 py-1 rounded-pill font-medium shrink-0">
            Paid
          </span>
        ) : null}
        <button
          type="button"
          className="flex-1 min-w-0 text-left"
          onClick={onOpen}
        >
          <div className="flex items-center gap-2 flex-wrap">
            <p
              className={clsx(
                "font-medium truncate text-base",
                paidCycle ? "text-muted line-through" : "text-body",
              )}
            >
              {bill.name}
            </p>
            {pastDue && !paidCycle ? (
              <span className="badge-error text-xs px-2 py-0.5 rounded-pill font-medium">
                Past due
              </span>
            ) : null}
            {bill.autoPay ? (
              <span className="badge-autopay text-xs px-2 py-0.5 rounded-pill">
                Autopay
              </span>
            ) : null}
            {bill.linkedCreditAccountId ? (
              <span className="text-xs px-2 py-0.5 rounded-pill bg-surface-variant text-muted">
                Debt-linked
              </span>
            ) : null}
          </div>
          <p className="text-xs text-muted mt-0.5 truncate">
            {GENERAL_CATEGORY_LABELS[cat]} · {frequencyLabel(bill.frequency)} ·{" "}
            {formatDueCountdown(bill, now)}
            {item.source === "CYPHERLOG" ? " · CypherLog" : ""}
          </p>
        </button>
        <div className="flex items-center gap-3 shrink-0">
          <p className="money text-lg whitespace-nowrap">
            {formatUsd(effectiveAmountDue(bill))}
          </p>
          <div className="flex items-center gap-0.5">
            <button
              type="button"
              className="btn-ghost text-sm px-2 py-1"
              onClick={onEdit}
              disabled={saving}
              aria-label={`Edit ${bill.name}`}
            >
              Edit
            </button>
            <button
              type="button"
              className="btn-ghost text-sm px-2 py-1 text-error"
              onClick={onDelete}
              disabled={saving || deletingId === bill.id}
              aria-label={`Delete ${bill.name}`}
            >
              {deletingId === bill.id ? "…" : "Delete"}
            </button>
          </div>
        </div>
      </div>
    </li>
  );
}

function FilterChip({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={clsx(
        "shrink-0 rounded-pill border px-3 py-1.5 text-sm transition-colors",
        active ? "filter-chip-active" : "filter-chip",
      )}
    >
      {label}
    </button>
  );
}
