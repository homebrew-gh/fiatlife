import { useEffect, useState } from "react";
import { CollapsibleSection } from "../ui";
import type { BillFrequency } from "../../lib/bill";
import { frequencyLabel } from "../../lib/bill";
import {
  ALL_CREDIT_ACCOUNT_TYPES,
  ALL_MIN_PAYMENT_TYPES,
  CREDIT_ACCOUNT_TYPE_LABELS,
  MIN_PAYMENT_TYPE_LABELS,
  formatIsoDate,
  isAmortizingType,
  isRevolvingType,
  parseIsoDate,
  type PromotionAppliesTo,
  type CreditAccount,
  type CreditAccountType,
  type CreditCardMinPaymentType,
} from "../../lib/creditAccount";
import {
  termMonthsFromYears,
  termYearsFromMonths,
} from "../../lib/mortgage";

const MORTGAGE_TERM_YEARS = [10, 15, 20, 25, 30];

type AccountInput = Omit<CreditAccount, "id" | "createdAt" | "updatedAt">;

const FEE_FREQUENCIES: BillFrequency[] = [
  "MONTHLY",
  "QUARTERLY",
  "SEMIANNUALLY",
  "ANNUALLY",
];

export function CreditAccountSheet({
  open,
  account,
  onClose,
  onSave,
  saving,
}: {
  open: boolean;
  account: CreditAccount | null;
  onClose: () => void;
  onSave: (input: AccountInput) => Promise<void>;
  saving: boolean;
}) {
  const [type, setType] = useState<CreditAccountType>("CREDIT_CARD");
  const [name, setName] = useState("");
  const [institution, setInstitution] = useState("");
  const [last4, setLast4] = useState("");
  const [currentBalance, setCurrentBalance] = useState("");
  const [aprPercent, setAprPercent] = useState("");
  const [promotionalAprPercent, setPromotionalAprPercent] = useState("");
  const [promotionalAprEndDate, setPromotionalAprEndDate] = useState("");
  const [promotionAppliesTo, setPromotionAppliesTo] =
    useState<PromotionAppliesTo>("PURCHASES");
  const [deferredInterest, setDeferredInterest] = useState(false);
  const [dueDay, setDueDay] = useState("1");
  const [notes, setNotes] = useState("");
  const [creditLimit, setCreditLimit] = useState("");
  const [minPaymentType, setMinPaymentType] =
    useState<CreditCardMinPaymentType>("PERCENT_OF_BALANCE");
  const [minPaymentValue, setMinPaymentValue] = useState("2");
  const [originalPrincipal, setOriginalPrincipal] = useState("");
  const [termMonths, setTermMonths] = useState("");
  const [termYears, setTermYears] = useState("30");
  const [loanStartDate, setLoanStartDate] = useState("");
  const [monthlyPayment, setMonthlyPayment] = useState("");
  const [annualFeeAmount, setAnnualFeeAmount] = useState("");
  const [annualFeeDate, setAnnualFeeDate] = useState("");
  const [annualFeeFrequency, setAnnualFeeFrequency] =
    useState<BillFrequency>("ANNUALLY");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const acc = account;
    setType(acc?.type ?? "CREDIT_CARD");
    setName(acc?.name ?? "");
    setInstitution(acc?.institution ?? "");
    setLast4(acc?.accountNumberLast4 ?? "");
    setCurrentBalance(
      acc?.currentBalance != null && acc.currentBalance > 0
        ? String(acc.currentBalance)
        : "",
    );
    setAprPercent(
      (acc?.standardApr ?? acc?.apr ?? 0) > 0
        ? ((acc?.standardApr ?? acc?.apr ?? 0) * 100).toFixed(2)
        : "",
    );
    setPromotionalAprPercent(
      acc?.promotionalApr != null
        ? (acc.promotionalApr * 100).toFixed(2)
        : "",
    );
    setPromotionalAprEndDate(
      acc?.promotionalAprEndDate != null
        ? formatIsoDate(acc.promotionalAprEndDate)
        : "",
    );
    setPromotionAppliesTo(acc?.promotionAppliesTo ?? "PURCHASES");
    setDeferredInterest(acc?.deferredInterest ?? false);
    setDueDay(String(acc?.dueDay ?? 1));
    setNotes(acc?.notes ?? "");
    setCreditLimit(
      acc?.creditLimit != null && acc.creditLimit > 0
        ? String(acc.creditLimit)
        : "",
    );
    setMinPaymentType(acc?.minimumPaymentType ?? "PERCENT_OF_BALANCE");
    setMinPaymentValue(
      acc != null
        ? acc.minimumPaymentType === "FIXED"
          ? acc.minimumPaymentValue.toFixed(2)
          : acc.minimumPaymentType === "PERCENT_OF_BALANCE"
            ? String(acc.minimumPaymentValue)
            : "25"
        : "2",
    );
    setOriginalPrincipal(
      acc?.originalPrincipal != null && acc.originalPrincipal > 0
        ? String(acc.originalPrincipal)
        : "",
    );
    setTermMonths(acc?.termMonths != null ? String(acc.termMonths) : "");
    setTermYears(
      acc?.termMonths != null
        ? String(termYearsFromMonths(acc.termMonths))
        : "30",
    );
    setLoanStartDate(
      acc?.startDate != null ? formatIsoDate(acc.startDate) : "",
    );
    setMonthlyPayment(
      acc?.monthlyPaymentAmount != null
        ? String(acc.monthlyPaymentAmount)
        : "",
    );
    setAnnualFeeAmount(
      acc?.annualFeeAmount != null && acc.annualFeeAmount > 0
        ? String(acc.annualFeeAmount)
        : "",
    );
    setAnnualFeeDate(
      acc?.annualFeeRenewalDateMillis != null
        ? formatIsoDate(acc.annualFeeRenewalDateMillis)
        : "",
    );
    setAnnualFeeFrequency(acc?.annualFeeFrequency ?? "ANNUALLY");
    setError(null);
  }, [open, account]);

  if (!open) return null;

  const revolving = isRevolvingType(type);
  const amortizing = isAmortizingType(type);
  const isCreditCard = type === "CREDIT_CARD";
  const isMortgage = type === "MORTGAGE";

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError("Account name is required.");
      return;
    }

    const balance = Number.parseFloat(currentBalance) || 0;
    const apr = (Number.parseFloat(aprPercent) || 0) / 100;
    const promotionalApr =
      promotionalAprPercent.trim() === ""
        ? null
        : Math.max(0, (Number.parseFloat(promotionalAprPercent) || 0) / 100);
    const promotionalEnd = parseIsoDate(promotionalAprEndDate);
    if (
      revolving &&
      promotionalApr != null &&
      promotionalEnd == null
    ) {
      setError("Enter the promotional APR end date.");
      return;
    }
    const day = Number.parseInt(dueDay, 10);
    if (!Number.isFinite(day) || day < 1 || day > 31) {
      setError("Due day must be 1–31.");
      return;
    }

    let minVal = Number.parseFloat(minPaymentValue);
    if (!Number.isFinite(minVal)) {
      minVal =
        minPaymentType === "FIXED"
          ? 25
          : minPaymentType === "PERCENT_OF_BALANCE"
            ? 2
            : 0;
    }

    const feeAmount = Number.parseFloat(annualFeeAmount) || 0;
    const feeDateMillis = parseIsoDate(annualFeeDate);

    try {
      await onSave({
        name: name.trim(),
        type,
        institution: institution.trim(),
        accountNumberLast4: last4.replace(/\D/g, "").slice(0, 4),
        apr:
          revolving &&
          promotionalApr != null &&
          promotionalEnd != null &&
          promotionalEnd >= Date.now()
            ? promotionalApr
            : Math.max(0, apr),
        standardApr: Math.max(0, apr),
        promotionalApr: revolving ? promotionalApr : null,
        promotionalAprEndDate: revolving ? promotionalEnd : null,
        promotionAppliesTo:
          revolving && promotionalApr != null ? promotionAppliesTo : null,
        deferredInterest:
          revolving && promotionalApr != null ? deferredInterest : false,
        currentBalance: Math.max(0, balance),
        statementBalanceAsOfMillis:
          account?.statementBalanceAsOfMillis ?? null,
        statementAmountDue: account?.statementAmountDue ?? null,
        dueDay: day,
        linkedBillId: account?.linkedBillId ?? null,
        annualFeeLinkedBillId: account?.annualFeeLinkedBillId ?? null,
        notes: notes.trim(),
        statementEntries: account?.statementEntries ?? [],
        attachmentHashes: account?.attachmentHashes ?? [],
        creditLimit: revolving
          ? Math.max(0, Number.parseFloat(creditLimit) || 0)
          : 0,
        minimumPaymentType: minPaymentType,
        minimumPaymentValue: Math.max(0, minVal),
        originalPrincipal: amortizing
          ? Math.max(0, Number.parseFloat(originalPrincipal) || 0)
          : 0,
        termMonths: amortizing
          ? isMortgage
            ? termMonthsFromYears(Number.parseFloat(termYears) || 30)
            : Number.parseInt(termMonths, 10) || null
          : null,
        monthlyPaymentAmount: amortizing
          ? Number.parseFloat(monthlyPayment) || null
          : null,
        startDate: isMortgage ? parseIsoDate(loanStartDate) : account?.startDate ?? null,
        endDate: account?.endDate ?? null,
        annualFeeAmount: isCreditCard ? Math.max(0, feeAmount) : 0,
        annualFeeRenewalDateMillis:
          isCreditCard && feeAmount > 0 ? feeDateMillis : null,
        annualFeeFrequency: isCreditCard ? annualFeeFrequency : "ANNUALLY",
      });
      onClose();
    } catch {
      setError("Could not save account.");
    }
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="credit-account-title"
    >
      <div className="card w-full max-w-md p-5 max-h-[90vh] overflow-y-auto">
        <h2 id="credit-account-title" className="page-title text-xl">
          {account ? "Edit Account" : "Add Account"}
        </h2>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="acct-type">
              Account type
            </label>
            <select
              id="acct-type"
              className="input"
              value={type}
              onChange={(e) => setType(e.target.value as CreditAccountType)}
            >
              {ALL_CREDIT_ACCOUNT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {CREDIT_ACCOUNT_TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="acct-name">
              Account name
            </label>
            <input
              id="acct-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Chase Sapphire"
              autoFocus
            />
          </div>
          <div>
            <label className="label" htmlFor="acct-institution">
              Institution
            </label>
            <input
              id="acct-institution"
              className="input"
              value={institution}
              onChange={(e) => setInstitution(e.target.value)}
              placeholder="Chase"
            />
          </div>
          <div>
            <label className="label" htmlFor="acct-last4">
              Last 4 digits
            </label>
            <input
              id="acct-last4"
              className="input"
              inputMode="numeric"
              maxLength={4}
              value={last4}
              onChange={(e) =>
                setLast4(e.target.value.replace(/\D/g, "").slice(0, 4))
              }
            />
          </div>
          <div>
            <label className="label" htmlFor="acct-balance">
              Current balance
            </label>
            <input
              id="acct-balance"
              className="input money"
              inputMode="decimal"
              value={currentBalance}
              onChange={(e) => setCurrentBalance(e.target.value)}
              placeholder="0.00"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="acct-apr">
                APR %
              </label>
              <input
                id="acct-apr"
                className="input"
                inputMode="decimal"
                value={aprPercent}
                onChange={(e) => setAprPercent(e.target.value)}
                placeholder="19.99"
              />
            </div>
            <div>
              <label className="label" htmlFor="acct-due">
                Due day
              </label>
              <input
                id="acct-due"
                className="input"
                inputMode="numeric"
                value={dueDay}
                onChange={(e) => setDueDay(e.target.value)}
              />
            </div>
          </div>

          {revolving ? (
            <>
              <div>
                <label className="label" htmlFor="acct-limit">
                  Credit limit
                </label>
                <input
                  id="acct-limit"
                  className="input money"
                  inputMode="decimal"
                  value={creditLimit}
                  onChange={(e) => setCreditLimit(e.target.value)}
                  placeholder="0.00"
                />
              </div>
              <div>
                <label className="label" htmlFor="acct-min-type">
                  Minimum payment
                </label>
                <select
                  id="acct-min-type"
                  className="input"
                  value={minPaymentType}
                  onChange={(e) => {
                    const next = e.target.value as CreditCardMinPaymentType;
                    setMinPaymentType(next);
                    if (next === "FIXED") setMinPaymentValue("25");
                    if (next === "PERCENT_OF_BALANCE") setMinPaymentValue("2");
                  }}
                >
                  {ALL_MIN_PAYMENT_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {MIN_PAYMENT_TYPE_LABELS[t]}
                    </option>
                  ))}
                </select>
              </div>
              {minPaymentType !== "FULL_BALANCE" ? (
                <div>
                  <label className="label" htmlFor="acct-min-val">
                    {minPaymentType === "FIXED"
                      ? "Minimum $ amount"
                      : "Percent (e.g. 2)"}
                  </label>
                  <input
                    id="acct-min-val"
                    className="input"
                    inputMode="decimal"
                    value={minPaymentValue}
                    onChange={(e) => setMinPaymentValue(e.target.value)}
                  />
                </div>
              ) : null}
            </>
          ) : null}

          {revolving ? (
            <fieldset className="space-y-3 border-t border-outline pt-3">
              <legend className="text-sm font-medium text-muted">
                Promotional APR (optional)
              </legend>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="acct-promo-apr">
                    Promotional APR %
                  </label>
                  <input
                    id="acct-promo-apr"
                    className="input"
                    inputMode="decimal"
                    value={promotionalAprPercent}
                    onChange={(e) => setPromotionalAprPercent(e.target.value)}
                    placeholder="0"
                  />
                </div>
                <div>
                  <label className="label" htmlFor="acct-promo-end">
                    Ends on
                  </label>
                  <input
                    id="acct-promo-end"
                    className="input"
                    type="date"
                    value={promotionalAprEndDate}
                    onChange={(e) => setPromotionalAprEndDate(e.target.value)}
                  />
                </div>
              </div>
              {promotionalAprPercent.trim() ? (
                <>
                  <div>
                    <label className="label" htmlFor="acct-promo-applies">
                      Applies to
                    </label>
                    <select
                      id="acct-promo-applies"
                      className="input"
                      value={promotionAppliesTo}
                      onChange={(e) =>
                        setPromotionAppliesTo(
                          e.target.value as PromotionAppliesTo,
                        )
                      }
                    >
                      <option value="PURCHASES">Purchases</option>
                      <option value="BALANCE_TRANSFER">Balance transfers</option>
                      <option value="BOTH">Purchases and balance transfers</option>
                    </select>
                  </div>
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={deferredInterest}
                      onChange={(e) => setDeferredInterest(e.target.checked)}
                    />
                    Deferred interest may apply if not paid by the end date
                  </label>
                </>
              ) : null}
            </fieldset>
          ) : null}

          {isCreditCard ? (
            <CollapsibleSection
              title="Membership fee"
              summary="Optional annual or recurring card fee"
              bare
            >
              <fieldset className="space-y-3">
                <div>
                  <label className="label" htmlFor="acct-fee">
                    Fee amount
                  </label>
                  <input
                    id="acct-fee"
                    className="input money"
                    inputMode="decimal"
                    value={annualFeeAmount}
                    onChange={(e) => setAnnualFeeAmount(e.target.value)}
                    placeholder="0.00"
                  />
                </div>
                <div>
                  <label className="label" htmlFor="acct-fee-date">
                    Renewal date (YYYY-MM-DD)
                  </label>
                  <input
                    id="acct-fee-date"
                    className="input"
                    value={annualFeeDate}
                    onChange={(e) =>
                      setAnnualFeeDate(e.target.value.slice(0, 10))
                    }
                    placeholder="2026-01-15"
                  />
                </div>
                <div>
                  <label className="label" htmlFor="acct-fee-freq">
                    Fee frequency
                  </label>
                  <select
                    id="acct-fee-freq"
                    className="input"
                    value={annualFeeFrequency}
                    onChange={(e) =>
                      setAnnualFeeFrequency(e.target.value as BillFrequency)
                    }
                  >
                    {FEE_FREQUENCIES.map((f) => (
                      <option key={f} value={f}>
                        {frequencyLabel(f)}
                      </option>
                    ))}
                  </select>
                </div>
              </fieldset>
            </CollapsibleSection>
          ) : null}

          {amortizing ? (
            <fieldset className="space-y-3 border-t border-outline pt-3">
              <legend className="text-sm font-medium text-muted">
                {isMortgage ? "Mortgage details" : "Loan details"}
              </legend>
              <div>
                <label className="label" htmlFor="acct-principal">
                  {isMortgage ? "Loan amount" : "Original principal"}
                </label>
                <input
                  id="acct-principal"
                  className="input money"
                  inputMode="decimal"
                  value={originalPrincipal}
                  onChange={(e) => setOriginalPrincipal(e.target.value)}
                  placeholder="0.00"
                />
              </div>
              {isMortgage ? (
                <div>
                  <label className="label" htmlFor="acct-start">
                    Loan start date
                  </label>
                  <input
                    id="acct-start"
                    className="input"
                    value={loanStartDate}
                    onChange={(e) =>
                      setLoanStartDate(e.target.value.slice(0, 10))
                    }
                    placeholder="2020-06-01"
                  />
                </div>
              ) : null}
              <div>
                <label className="label" htmlFor="acct-term">
                  {isMortgage ? "Term (years)" : "Term (months)"}
                </label>
                {isMortgage ? (
                  <select
                    id="acct-term"
                    className="input"
                    value={termYears}
                    onChange={(e) => setTermYears(e.target.value)}
                  >
                    {MORTGAGE_TERM_YEARS.map((y) => (
                      <option key={y} value={y}>
                        {y} years
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    id="acct-term"
                    className="input"
                    inputMode="numeric"
                    value={termMonths}
                    onChange={(e) => setTermMonths(e.target.value)}
                    placeholder="60"
                  />
                )}
              </div>
              <div>
                <label className="label" htmlFor="acct-monthly">
                  {isMortgage ? "Monthly P&I payment" : "Monthly payment"}
                </label>
                <input
                  id="acct-monthly"
                  className="input money"
                  inputMode="decimal"
                  value={monthlyPayment}
                  onChange={(e) => setMonthlyPayment(e.target.value)}
                  placeholder="Leave blank to calculate from rate & term"
                />
              </div>
            </fieldset>
          ) : null}

          <CollapsibleSection title="Notes" summary="Optional details" bare>
            <div>
              <label className="label" htmlFor="acct-notes">
                Notes
              </label>
              <textarea
                id="acct-notes"
                className="input min-h-[4rem] resize-y"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </CollapsibleSection>

          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
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
              {saving ? "Saving…" : account ? "Save changes" : "Add account"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
