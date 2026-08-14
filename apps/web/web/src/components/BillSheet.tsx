import { useEffect, useMemo, useState } from "react";
import { CollapsibleSection } from "./ui";
import {
  ALL_GENERAL_CATEGORIES,
  GENERAL_CATEGORY_LABELS,
  effectiveSubcategory,
  generalCategoryForBill,
  type Bill,
  type BillFrequency,
  type BillGeneralCategory,
  type BillRecurrenceUnit,
  type BillSubcategory,
  type BillWithSource,
  type CreditCardMinPaymentType,
} from "../lib/bill";
import {
  subcategoriesForGeneral,
  subcategoryLabel,
} from "../lib/billSubcategory";
import type { BankAccount } from "../lib/bankAccount";
import type { Biller } from "../lib/biller";
import type { CreditAccount } from "../lib/creditAccount";

const FREQUENCIES: BillFrequency[] = [
  "WEEKLY",
  "BIWEEKLY",
  "MONTHLY",
  "BIMONTHLY",
  "QUARTERLY",
  "SEMIANNUALLY",
  "ANNUALLY",
];

export type BillSheetInput = {
  name: string;
  amount: number;
  frequency: BillFrequency;
  subcategory: BillSubcategory;
  dueDay: number;
  notes: string;
  autoPay: boolean;
  isRecurring: boolean;
  billerName: string;
  linkedBillerId: string | null;
  renewalDateMillis: number | null;
  initialPurchaseDateMillis: number | null;
  recurrenceUnit: BillRecurrenceUnit | null;
  recurrenceIntervalCount: number;
  rateValidUntilMillis: number | null;
  accountName: string;
  payFromBankAccountId: string | null;
  payFromCreditAccountId: string | null;
  showInCypherLog: boolean;
  creditCardDetails: Bill["creditCardDetails"];
};

function creditMinimumDue(cc: NonNullable<Bill["creditCardDetails"]>): number {
  const balance = cc.currentBalance ?? 0;
  const value = cc.minimumPaymentValue ?? 2;
  switch (cc.minimumPaymentType) {
    case "FIXED":
      return Math.max(0, value);
    case "FULL_BALANCE":
      return Math.max(0, balance);
    default:
      return Math.max(0, balance * (value / 100));
  }
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
  const d = new Date(value + "T00:00:00");
  return Number.isFinite(d.getTime()) ? d.getTime() : null;
}

export function BillSheet({
  open,
  editing,
  billers,
  bankAccounts,
  creditAccounts,
  onClose,
  onSave,
  saving,
}: {
  open: boolean;
  editing: BillWithSource | null;
  billers: Biller[];
  bankAccounts: BankAccount[];
  creditAccounts: CreditAccount[];
  onClose: () => void;
  onSave: (input: BillSheetInput) => Promise<void>;
  saving: boolean;
}) {
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState<BillFrequency>("MONTHLY");
  const [generalCategory, setGeneralCategory] =
    useState<BillGeneralCategory>("HOME");
  const [subcategory, setSubcategory] = useState<BillSubcategory>("MORTGAGE_RENT");
  const [dueDay, setDueDay] = useState("1");
  const [notes, setNotes] = useState("");
  const [autoPay, setAutoPay] = useState(false);
  const [isRecurring, setIsRecurring] = useState(true);
  const [billerName, setBillerName] = useState("");
  const [oneTimeDueDate, setOneTimeDueDate] = useState("");
  const [initialPurchaseDate, setInitialPurchaseDate] = useState("");
  const [annualYearsPerCycle, setAnnualYearsPerCycle] = useState("1");
  const [rateValidUntil, setRateValidUntil] = useState("");
  const [accountName, setAccountName] = useState("");
  const [payFromBankId, setPayFromBankId] = useState("");
  const [payFromCreditId, setPayFromCreditId] = useState("");
  const [showInCypherLog, setShowInCypherLog] = useState(false);
  const [ccBalance, setCcBalance] = useState("");
  const [ccApr, setCcApr] = useState("");
  const [ccMinType, setCcMinType] =
    useState<CreditCardMinPaymentType>("PERCENT_OF_BALANCE");
  const [ccMinValue, setCcMinValue] = useState("2");
  const [error, setError] = useState<string | null>(null);

  const isCreditCard = subcategory === "CREDIT_CARD";

  const generalCategoryOptions = useMemo(
    () =>
      editing
        ? ALL_GENERAL_CATEGORIES
        : ALL_GENERAL_CATEGORIES.filter((c) => c !== "CREDIT_LOANS"),
    [editing],
  );

  const subcategoryOptions = useMemo(
    () => subcategoriesForGeneral(generalCategory),
    [generalCategory],
  );

  const showCypherLogToggle = !editing && generalCategory === "SUBSCRIPTION";

  const billerSuggestions = useMemo(() => {
    const q = billerName.trim().toLowerCase();
    if (!q) return billers.slice(0, 8);
    return billers
      .filter((b) => b.name.toLowerCase().includes(q))
      .slice(0, 8);
  }, [billerName, billers]);

  useEffect(() => {
    if (!open) return;
    const bill = editing?.bill;
    setName(bill?.name ?? "");
    setAmount(
      bill?.amount != null && bill.amount > 0 ? String(bill.amount) : "",
    );
    setFrequency(bill?.frequency ?? "MONTHLY");
    const gen = bill ? generalCategoryForBill(bill) : "HOME";
    setGeneralCategory(gen);
    const sub = bill ? effectiveSubcategory(bill) : subcategoriesForGeneral(gen)[0];
    setSubcategory(sub);
    setDueDay(String(bill?.dueDay ?? 1));
    setNotes(bill?.notes ?? "");
    setAutoPay(Boolean(bill?.autoPay));
    setIsRecurring(bill?.isRecurring !== false);
    setBillerName(bill?.billerName ?? "");
    setOneTimeDueDate(
      bill && bill.isRecurring === false
        ? dateInputValue(bill.renewalDateMillis)
        : "",
    );
    setInitialPurchaseDate(dateInputValue(bill?.initialPurchaseDateMillis));
    setAnnualYearsPerCycle(
      bill?.recurrenceUnit === "YEAR" && (bill.recurrenceIntervalCount ?? 1) > 1
        ? String(bill.recurrenceIntervalCount)
        : "1",
    );
    setRateValidUntil(dateInputValue(bill?.rateValidUntilMillis));
    setAccountName(bill?.accountName ?? "");
    setPayFromBankId(bill?.payFromBankAccountId ?? "");
    setPayFromCreditId(bill?.payFromCreditAccountId ?? "");
    setShowInCypherLog(false);
    const cc = bill?.creditCardDetails;
    setCcBalance(cc?.currentBalance != null ? String(cc.currentBalance) : "");
    setCcApr(cc?.apr != null ? String((cc.apr * 100).toFixed(2)) : "");
    setCcMinType(cc?.minimumPaymentType ?? "PERCENT_OF_BALANCE");
    setCcMinValue(
      cc?.minimumPaymentValue != null ? String(cc.minimumPaymentValue) : "2",
    );
    setError(null);
  }, [open, editing]);

  useEffect(() => {
    if (!subcategoryOptions.includes(subcategory)) {
      setSubcategory(subcategoryOptions[0]);
    }
  }, [subcategoryOptions, subcategory]);

  if (!open) return null;

  const isEdit = editing != null;
  const linkedToDebt = Boolean(editing?.bill.linkedCreditAccountId);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError("Name is required.");
      return;
    }

    let creditCardDetails: Bill["creditCardDetails"] = null;
    let resolvedAmount: number;
    if (isCreditCard) {
      const balance = Number.parseFloat(ccBalance);
      const aprPct = Number.parseFloat(ccApr);
      const minVal = Number.parseFloat(ccMinValue);
      creditCardDetails = {
        currentBalance: Number.isFinite(balance) ? Math.max(0, balance) : 0,
        apr: Number.isFinite(aprPct) ? Math.max(0, aprPct / 100) : 0,
        minimumPaymentType: ccMinType,
        minimumPaymentValue: Number.isFinite(minVal) ? Math.max(0, minVal) : 2,
        interestChargedLastPeriod:
          editing?.bill.creditCardDetails?.interestChargedLastPeriod ?? 0,
      };
      resolvedAmount = creditMinimumDue(creditCardDetails);
    } else {
      resolvedAmount = Number.parseFloat(amount);
      if (!Number.isFinite(resolvedAmount) || resolvedAmount <= 0) {
        setError("Enter a valid amount.");
        return;
      }
    }

    const day = Number.parseInt(dueDay, 10);
    if (isRecurring && (!Number.isFinite(day) || day < 1 || day > 31)) {
      setError("Due day must be 1–31.");
      return;
    }

    const oneTimeMillis = !isRecurring ? parseDateInput(oneTimeDueDate) : null;
    if (!isRecurring && oneTimeMillis == null) {
      setError("Enter a due date for this one-time bill.");
      return;
    }

    const annualInterval = Math.max(
      Number.parseInt(annualYearsPerCycle, 10) || 1,
      1,
    );
    const recurrenceUnit: BillRecurrenceUnit | null =
      isRecurring && frequency === "ANNUALLY" && annualInterval > 1
        ? "YEAR"
        : null;
    const recurrenceIntervalCount =
      isRecurring && frequency === "ANNUALLY" ? annualInterval : 1;

    const matchedBiller = billers.find(
      (b) => b.name.toLowerCase() === billerName.trim().toLowerCase(),
    );

    try {
      await onSave({
        name: name.trim(),
        amount: resolvedAmount,
        frequency,
        subcategory,
        dueDay: Number.isFinite(day) ? day : 1,
        notes: notes.trim(),
        autoPay,
        isRecurring,
        billerName: billerName.trim(),
        linkedBillerId: matchedBiller?.id ?? editing?.bill.linkedBillerId ?? null,
        renewalDateMillis: isRecurring
          ? (editing?.bill.renewalDateMillis ?? null)
          : oneTimeMillis,
        initialPurchaseDateMillis: isRecurring
          ? parseDateInput(initialPurchaseDate)
          : null,
        recurrenceUnit,
        recurrenceIntervalCount,
        rateValidUntilMillis: parseDateInput(rateValidUntil),
        accountName: accountName.trim(),
        payFromBankAccountId: payFromBankId || null,
        payFromCreditAccountId: payFromCreditId || null,
        showInCypherLog: showCypherLogToggle && showInCypherLog,
        creditCardDetails,
      });
      onClose();
    } catch {
      setError("Could not save bill.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="bill-sheet-title"
    >
      <div className="card w-full max-w-lg p-5 max-h-[90vh] overflow-y-auto">
        <h2 id="bill-sheet-title" className="page-title text-xl">
          {isEdit ? "Edit Bill" : "Add Bill"}
        </h2>
        {linkedToDebt ? (
          <p className="text-sm text-muted mt-1">
            Linked to a debt account — amount, due day, and APR are managed
            there.
          </p>
        ) : null}
        {editing?.source === "CYPHERLOG" ? (
          <p className="text-sm text-muted mt-1">Synced from CypherLog.</p>
        ) : null}
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="bill-name">
              Name
            </label>
            <input
              id="bill-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
            />
          </div>
          <div>
            <label className="label" htmlFor="bill-biller">
              Biller / company
            </label>
            <input
              id="bill-biller"
              className="input"
              value={billerName}
              onChange={(e) => setBillerName(e.target.value)}
              list="biller-suggestions"
              placeholder="Optional"
            />
            <datalist id="biller-suggestions">
              {billerSuggestions.map((b) => (
                <option key={b.id} value={b.name} />
              ))}
            </datalist>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="bill-cat">
                Category
              </label>
              <select
                id="bill-cat"
                className="input"
                value={generalCategory}
                onChange={(e) =>
                  setGeneralCategory(e.target.value as BillGeneralCategory)
                }
                disabled={linkedToDebt}
              >
                {generalCategoryOptions.map((c) => (
                  <option key={c} value={c}>
                    {GENERAL_CATEGORY_LABELS[c]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="bill-sub">
                Subcategory
              </label>
              <select
                id="bill-sub"
                className="input"
                value={subcategory}
                onChange={(e) =>
                  setSubcategory(e.target.value as BillSubcategory)
                }
                disabled={linkedToDebt}
              >
                {subcategoryOptions.map((s) => (
                  <option key={s} value={s}>
                    {subcategoryLabel(s)}
                  </option>
                ))}
              </select>
            </div>
          </div>
          {isCreditCard && !linkedToDebt ? (
            <div className="card-quiet p-3 space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="cc-balance">
                    Current balance
                  </label>
                  <input
                    id="cc-balance"
                    className="input money"
                    inputMode="decimal"
                    value={ccBalance}
                    onChange={(e) => setCcBalance(e.target.value)}
                  />
                </div>
                <div>
                  <label className="label" htmlFor="cc-apr">
                    APR %
                  </label>
                  <input
                    id="cc-apr"
                    className="input"
                    inputMode="decimal"
                    value={ccApr}
                    onChange={(e) => setCcApr(e.target.value)}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="cc-min-type">
                    Min payment
                  </label>
                  <select
                    id="cc-min-type"
                    className="input"
                    value={ccMinType}
                    onChange={(e) =>
                      setCcMinType(e.target.value as CreditCardMinPaymentType)
                    }
                  >
                    <option value="PERCENT_OF_BALANCE">% of balance</option>
                    <option value="FIXED">Fixed $</option>
                    <option value="FULL_BALANCE">Pay in full</option>
                  </select>
                </div>
                <div>
                  <label className="label" htmlFor="cc-min-val">
                    Value
                  </label>
                  <input
                    id="cc-min-val"
                    className="input"
                    inputMode="decimal"
                    value={ccMinValue}
                    onChange={(e) => setCcMinValue(e.target.value)}
                    disabled={ccMinType === "FULL_BALANCE"}
                  />
                </div>
              </div>
            </div>
          ) : (
            <div>
              <label className="label" htmlFor="bill-amount">
                Amount
              </label>
              <input
                id="bill-amount"
                className="input money"
                inputMode="decimal"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                disabled={linkedToDebt}
              />
            </div>
          )}
          <div className="flex flex-wrap gap-4">
            <label className="flex items-center gap-2 cursor-pointer text-sm">
              <input
                type="checkbox"
                checked={autoPay}
                onChange={(e) => setAutoPay(e.target.checked)}
              />
              Autopay
            </label>
            <label className="flex items-center gap-2 cursor-pointer text-sm">
              <input
                type="checkbox"
                checked={isRecurring}
                onChange={(e) => setIsRecurring(e.target.checked)}
                disabled={linkedToDebt}
              />
              Recurring
            </label>
            {showCypherLogToggle ? (
              <label className="flex items-center gap-2 cursor-pointer text-sm">
                <input
                  type="checkbox"
                  checked={showInCypherLog}
                  onChange={(e) => setShowInCypherLog(e.target.checked)}
                />
                Show in CypherLog
              </label>
            ) : null}
          </div>
          {isRecurring ? (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="bill-freq">
                    Frequency
                  </label>
                  <select
                    id="bill-freq"
                    className="input"
                    value={frequency}
                    onChange={(e) =>
                      setFrequency(e.target.value as BillFrequency)
                    }
                    disabled={linkedToDebt}
                  >
                    {FREQUENCIES.map((f) => (
                      <option key={f} value={f}>
                        {f.charAt(0) + f.slice(1).toLowerCase()}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="label" htmlFor="bill-due">
                    Due day
                  </label>
                  <input
                    id="bill-due"
                    className="input"
                    inputMode="numeric"
                    value={dueDay}
                    onChange={(e) => setDueDay(e.target.value)}
                    disabled={linkedToDebt}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="bill-start">
                    Initial purchase date
                  </label>
                  <input
                    id="bill-start"
                    type="date"
                    className="input"
                    value={initialPurchaseDate}
                    onChange={(e) => setInitialPurchaseDate(e.target.value)}
                  />
                </div>
                {frequency === "ANNUALLY" ? (
                  <div>
                    <label className="label" htmlFor="bill-years">
                      Years per cycle
                    </label>
                    <input
                      id="bill-years"
                      className="input"
                      inputMode="numeric"
                      value={annualYearsPerCycle}
                      onChange={(e) => setAnnualYearsPerCycle(e.target.value)}
                    />
                  </div>
                ) : null}
              </div>
            </>
          ) : (
            <div>
              <label className="label" htmlFor="bill-onetime">
                Due date
              </label>
              <input
                id="bill-onetime"
                type="date"
                className="input"
                value={oneTimeDueDate}
                onChange={(e) => setOneTimeDueDate(e.target.value)}
              />
            </div>
          )}
          <CollapsibleSection
            title="More options"
            summary="Pay-from accounts, labels, and notes"
            bare
          >
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label" htmlFor="bill-rate-valid">
                  Rate valid until
                </label>
                <input
                  id="bill-rate-valid"
                  type="date"
                  className="input"
                  value={rateValidUntil}
                  onChange={(e) => setRateValidUntil(e.target.value)}
                />
              </div>
              <div>
                <label className="label" htmlFor="bill-account-name">
                  Account label
                </label>
                <input
                  id="bill-account-name"
                  className="input"
                  value={accountName}
                  onChange={(e) => setAccountName(e.target.value)}
                  placeholder="Optional"
                />
              </div>
            </div>
            {bankAccounts.length > 0 || creditAccounts.length > 0 ? (
              <div className="grid grid-cols-2 gap-3">
                {bankAccounts.length > 0 ? (
                  <div>
                    <label className="label" htmlFor="pay-bank">
                      Pay from bank
                    </label>
                    <select
                      id="pay-bank"
                      className="input"
                      value={payFromBankId}
                      onChange={(e) => setPayFromBankId(e.target.value)}
                    >
                      <option value="">—</option>
                      {bankAccounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
                {creditAccounts.length > 0 ? (
                  <div>
                    <label className="label" htmlFor="pay-credit">
                      Pay from credit
                    </label>
                    <select
                      id="pay-credit"
                      className="input"
                      value={payFromCreditId}
                      onChange={(e) => setPayFromCreditId(e.target.value)}
                    >
                      <option value="">—</option>
                      {creditAccounts.map((a) => (
                        <option key={a.id} value={a.id}>
                          {a.name}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
              </div>
            ) : null}
            <div>
              <label className="label" htmlFor="bill-notes">
                Notes
              </label>
              <textarea
                id="bill-notes"
                className="input min-h-[4rem]"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </CollapsibleSection>
          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : linkedToDebt ? (
            <p className="notice-panel p-3 text-sm">
              Balance, APR, and statement amount are managed by the linked Debt
              account. This Bill stores the due-date reminder and payments.
            </p>
          ) : null}
          <div className="flex gap-2 pt-2">
            <button
              type="button"
              className="btn-ghost flex-1"
              onClick={onClose}
              disabled={saving}
            >
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1" disabled={saving}>
              {saving ? "Saving…" : isEdit ? "Save changes" : "Add bill"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
