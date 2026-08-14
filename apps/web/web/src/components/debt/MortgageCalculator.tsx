import { useEffect, useMemo, useRef, useState } from "react";
import clsx from "clsx";
import { CollapsibleSection } from "../ui";
import { formatUsd } from "../../lib/format";
import type { ConservativeMonthlyTakeHome } from "../../lib/salary";
import {
  computeMortgageCostBreakdown,
  defaultAffordabilityThresholds,
  evaluateMortgageScenario,
  formatMortgageDate,
  loanAmountFromScenario,
  type AffordabilityMode,
  type AffordabilityRating,
  type AffordabilityThresholds,
  type MortgageScenarioInput,
  type MortgageScenarioResult,
} from "../../lib/mortgage";

const TERM_OPTIONS = [10, 15, 20, 25, 30];

const AFFORDABILITY_STYLES: Record<
  AffordabilityRating,
  { label: string; cls: string }
> = {
  comfortable: { label: "Comfortable", cls: "text-success" },
  stretched: { label: "Stretched", cls: "text-warn" },
  risky: { label: "Risky", cls: "text-error" },
};

/**
 * Numeric inputs are stored as raw strings so the user can type intermediate
 * values like "6." while entering a decimal (a number-backed controlled input
 * would strip the trailing dot). They are parsed only when computing.
 */
type ScenarioDraft = {
  label: string;
  homePrice: string;
  downPaymentMode: "amount" | "percent";
  downPaymentPercent: string;
  downPaymentAmount: string;
  annualRate: string;
  termYears: number;
  extraMonthlyPayment: string;
  propertyTaxRate: string;
  annualHomeInsurance: string;
  monthlyHoa: string;
  pmiRate: string;
  closingCostPercent: string;
  affordabilityMode: AffordabilityMode;
  monthlyIncome: string;
  monthlyUtilities: string;
  /** Extra monthly debt the user adds on top of debts tracked in their accounts. */
  additionalDebts: string;
  customizeThresholds: boolean;
  comfortableHousingMax: string;
  stretchedHousingMax: string;
  comfortableTotalMax: string;
  stretchedTotalMax: string;
};

function num(value: string): number {
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function defaultDraft(label: string): ScenarioDraft {
  const thresholds = defaultAffordabilityThresholds("takehome");
  return {
    label,
    homePrice: "400000",
    downPaymentMode: "percent",
    downPaymentPercent: "20",
    downPaymentAmount: "80000",
    annualRate: "6.5",
    termYears: 30,
    extraMonthlyPayment: "",
    propertyTaxRate: "1.1",
    annualHomeInsurance: "1800",
    monthlyHoa: "",
    pmiRate: "0.6",
    closingCostPercent: "3",
    affordabilityMode: "takehome",
    monthlyIncome: "",
    monthlyUtilities: "250",
    additionalDebts: "",
    customizeThresholds: false,
    comfortableHousingMax: String(thresholds.comfortableHousingMax),
    stretchedHousingMax: String(thresholds.stretchedHousingMax),
    comfortableTotalMax: String(thresholds.comfortableTotalMax),
    stretchedTotalMax: String(thresholds.stretchedTotalMax),
  };
}

function thresholdsFromDraft(draft: ScenarioDraft): AffordabilityThresholds {
  const defaults = defaultAffordabilityThresholds(draft.affordabilityMode);
  if (!draft.customizeThresholds) return defaults;
  return {
    comfortableHousingMax: num(draft.comfortableHousingMax) || defaults.comfortableHousingMax,
    stretchedHousingMax: num(draft.stretchedHousingMax) || defaults.stretchedHousingMax,
    comfortableTotalMax: num(draft.comfortableTotalMax) || defaults.comfortableTotalMax,
    stretchedTotalMax: num(draft.stretchedTotalMax) || defaults.stretchedTotalMax,
  };
}

function draftToInput(
  draft: ScenarioDraft,
  trackedMonthlyDebts = 0,
): MortgageScenarioInput {
  const homePrice = num(draft.homePrice);
  let downPayment = num(draft.downPaymentAmount);
  if (draft.downPaymentMode === "percent") {
    downPayment = homePrice * (num(draft.downPaymentPercent) / 100);
  }
  return {
    homePrice,
    downPayment,
    annualRate: num(draft.annualRate),
    termYears: draft.termYears,
    extraMonthlyPayment: num(draft.extraMonthlyPayment),
    propertyTaxRate: num(draft.propertyTaxRate),
    annualHomeInsurance: num(draft.annualHomeInsurance),
    monthlyHoa: num(draft.monthlyHoa),
    pmiRate: num(draft.pmiRate),
    closingCostPercent: num(draft.closingCostPercent),
    monthlyIncome: num(draft.monthlyIncome),
    monthlyDebts: Math.max(0, trackedMonthlyDebts) + num(draft.additionalDebts),
    monthlyUtilities: num(draft.monthlyUtilities),
    affordabilityMode: draft.affordabilityMode,
    affordabilityThresholds: thresholdsFromDraft(draft),
  };
}

function ScenarioForm({
  draft,
  onChange,
  onEvaluate,
  suggestedGrossMonthlyIncome,
  suggestedTakeHomeMonthlyIncome,
  conservativeTakeHome,
  trackedMonthlyDebts = 0,
}: {
  draft: ScenarioDraft;
  onChange: (next: ScenarioDraft) => void;
  onEvaluate: () => void;
  suggestedGrossMonthlyIncome?: number;
  suggestedTakeHomeMonthlyIncome?: number;
  conservativeTakeHome?: ConservativeMonthlyTakeHome;
  trackedMonthlyDebts?: number;
}) {
  const input = draftToInput(draft, trackedMonthlyDebts);
  const loanAmount = loanAmountFromScenario(input);
  const breakdown = computeMortgageCostBreakdown(input);
  const downPercent =
    input.homePrice > 0 ? (input.downPayment / input.homePrice) * 100 : 0;
  const pmiApplies = downPercent < 20;
  const closingCosts = Math.max(
    0,
    input.homePrice * (num(draft.closingCostPercent) / 100),
  );
  const cashToClose = input.downPayment + closingCosts;
  const additionalDebtsValue = num(draft.additionalDebts);
  const isTakeHome = draft.affordabilityMode === "takehome";
  const suggestedIncome = isTakeHome
    ? suggestedTakeHomeMonthlyIncome
    : suggestedGrossMonthlyIncome;
  const defaultThresholds = defaultAffordabilityThresholds(draft.affordabilityMode);

  const setAffordabilityMode = (mode: AffordabilityMode) => {
    const thresholds = defaultAffordabilityThresholds(mode);
    onChange({
      ...draft,
      affordabilityMode: mode,
      customizeThresholds: false,
      comfortableHousingMax: String(thresholds.comfortableHousingMax),
      stretchedHousingMax: String(thresholds.stretchedHousingMax),
      comfortableTotalMax: String(thresholds.comfortableTotalMax),
      stretchedTotalMax: String(thresholds.stretchedTotalMax),
    });
  };

  return (
    <div className="space-y-4">
      <div>
        <label className="label" htmlFor="mc-label">
          Scenario name
        </label>
        <input
          id="mc-label"
          className="input"
          value={draft.label}
          onChange={(e) => onChange({ ...draft, label: e.target.value })}
        />
      </div>
      <div>
        <label className="label" htmlFor="mc-home">
          Home price
        </label>
        <input
          id="mc-home"
          className="input money"
          inputMode="decimal"
          value={draft.homePrice}
          onChange={(e) =>
            onChange({ ...draft, homePrice: e.target.value })
          }
        />
      </div>
      <div>
        <label className="label">Down payment</label>
        <div className="flex gap-2 mb-2">
          <button
            type="button"
            className={clsx(
              "btn-ghost text-sm flex-1",
              draft.downPaymentMode === "percent" && "ring-1 ring-outline",
            )}
            onClick={() => onChange({ ...draft, downPaymentMode: "percent" })}
          >
            Percent
          </button>
          <button
            type="button"
            className={clsx(
              "btn-ghost text-sm flex-1",
              draft.downPaymentMode === "amount" && "ring-1 ring-outline",
            )}
            onClick={() => onChange({ ...draft, downPaymentMode: "amount" })}
          >
            Dollar amount
          </button>
        </div>
        {draft.downPaymentMode === "percent" ? (
          <input
            className="input"
            inputMode="decimal"
            value={draft.downPaymentPercent}
            onChange={(e) =>
              onChange({ ...draft, downPaymentPercent: e.target.value })
            }
            placeholder="20"
          />
        ) : (
          <input
            className="input money"
            inputMode="decimal"
            value={draft.downPaymentAmount}
            onChange={(e) =>
              onChange({ ...draft, downPaymentAmount: e.target.value })
            }
          />
        )}
      </div>
      <p className="text-sm text-muted">
        Loan amount: <span className="font-mono text-body">{formatUsd(loanAmount)}</span>
      </p>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="label" htmlFor="mc-rate">
            Interest rate %
          </label>
          <input
            id="mc-rate"
            className="input"
            inputMode="decimal"
            value={draft.annualRate}
            onChange={(e) =>
              onChange({ ...draft, annualRate: e.target.value })
            }
          />
        </div>
        <div>
          <label className="label" htmlFor="mc-term">
            Term (years)
          </label>
          <select
            id="mc-term"
            className="input"
            value={draft.termYears}
            onChange={(e) =>
              onChange({
                ...draft,
                termYears: Number.parseInt(e.target.value, 10),
              })
            }
          >
            {TERM_OPTIONS.map((y) => (
              <option key={y} value={y}>
                {y} years
              </option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <label className="label" htmlFor="mc-extra">
          Extra monthly (principal)
        </label>
        <input
          id="mc-extra"
          className="input money"
          inputMode="decimal"
          value={draft.extraMonthlyPayment}
          onChange={(e) =>
            onChange({ ...draft, extraMonthlyPayment: e.target.value })
          }
          placeholder="0"
        />
      </div>

      <CollapsibleSection
        title="Taxes, escrow & affordability"
        summary="Property tax, insurance, HOA, PMI, closing costs, DTI"
        bare
      >
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="label" htmlFor="mc-tax-rate">
            Property tax rate %/yr
          </label>
          <input
            id="mc-tax-rate"
            className="input"
            inputMode="decimal"
            value={draft.propertyTaxRate}
            onChange={(e) =>
              onChange({ ...draft, propertyTaxRate: e.target.value })
            }
            placeholder="1.1"
          />
        </div>
        <div>
          <label className="label" htmlFor="mc-insurance">
            Home insurance $/yr
          </label>
          <input
            id="mc-insurance"
            className="input money"
            inputMode="decimal"
            value={draft.annualHomeInsurance}
            onChange={(e) =>
              onChange({ ...draft, annualHomeInsurance: e.target.value })
            }
            placeholder="1800"
          />
        </div>
        <div>
          <label className="label" htmlFor="mc-hoa">
            HOA dues $/mo
          </label>
          <input
            id="mc-hoa"
            className="input money"
            inputMode="decimal"
            value={draft.monthlyHoa}
            onChange={(e) =>
              onChange({ ...draft, monthlyHoa: e.target.value })
            }
            placeholder="0"
          />
        </div>
      </div>
      <div>
        <label className="label" htmlFor="mc-pmi">
          PMI rate %/yr
        </label>
        <input
          id="mc-pmi"
          className="input"
          inputMode="decimal"
          value={draft.pmiRate}
          onChange={(e) => onChange({ ...draft, pmiRate: e.target.value })}
          placeholder="0.6"
        />
        <p className="text-xs text-muted mt-1">
          {pmiApplies
            ? `Applied while equity is under 20% (down payment is ${downPercent.toFixed(
                1,
              )}%).`
            : "Not applied — down payment is 20% or more."}
        </p>
      </div>
      <div className="rounded-lg bg-surfaceVariant p-3 text-sm space-y-1">
        <p className="text-muted font-medium">Estimated escrow / month</p>
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 font-mono text-xs">
          <span className="text-muted">Property tax</span>
          <span className="text-right">
            {formatUsd(breakdown.monthlyPropertyTax)}
          </span>
          <span className="text-muted">Home insurance</span>
          <span className="text-right">
            {formatUsd(breakdown.monthlyHomeInsurance)}
          </span>
          {breakdown.monthlyHoa > 0 ? (
            <>
              <span className="text-muted">HOA</span>
              <span className="text-right">
                {formatUsd(breakdown.monthlyHoa)}
              </span>
            </>
          ) : null}
          {breakdown.monthlyPmi > 0 ? (
            <>
              <span className="text-muted">PMI</span>
              <span className="text-right">
                {formatUsd(breakdown.monthlyPmi)}
              </span>
            </>
          ) : null}
        </div>
      </div>

      <div className="border-t border-outline/60 pt-4 space-y-4">
        <p className="text-sm font-medium text-body">Cash to close</p>
        <div>
          <label className="label" htmlFor="mc-closing">
            Closing costs %
          </label>
          <input
            id="mc-closing"
            className="input"
            inputMode="decimal"
            value={draft.closingCostPercent}
            onChange={(e) =>
              onChange({ ...draft, closingCostPercent: e.target.value })
            }
            placeholder="3"
          />
          <p className="text-xs text-muted mt-1">
            Typically 2–5% of the home price (lender fees, title, escrow).
          </p>
        </div>
        <div className="rounded-lg bg-surfaceVariant p-3 text-sm grid grid-cols-2 gap-x-4 gap-y-1 font-mono text-xs">
          <span className="text-muted">Down payment</span>
          <span className="text-right">{formatUsd(input.downPayment)}</span>
          <span className="text-muted">Closing costs</span>
          <span className="text-right">{formatUsd(closingCosts)}</span>
          <span className="text-body font-semibold">Total cash needed</span>
          <span className="text-right text-body font-semibold">
            {formatUsd(cashToClose)}
          </span>
        </div>
      </div>

      <div className="border-t border-outline/60 pt-4 space-y-4">
        <p className="text-sm font-medium text-body">Affordability check</p>
        <div>
          <label className="label">Income basis</label>
          <div className="flex gap-2">
            <button
              type="button"
              className={clsx(
                "btn-ghost text-sm flex-1",
                isTakeHome && "ring-1 ring-outline",
              )}
              onClick={() => setAffordabilityMode("takehome")}
            >
              Take-home budget
            </button>
            <button
              type="button"
              className={clsx(
                "btn-ghost text-sm flex-1",
                !isTakeHome && "ring-1 ring-outline",
              )}
              onClick={() => setAffordabilityMode("lender")}
            >
              Lender (gross)
            </button>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="mc-income">
              {isTakeHome ? "Take-home pay $/mo" : "Gross income $/mo"}
            </label>
            <input
              id="mc-income"
              className="input money"
              inputMode="decimal"
              value={draft.monthlyIncome}
              onChange={(e) =>
                onChange({ ...draft, monthlyIncome: e.target.value })
              }
              placeholder="0"
            />
          </div>
          <div>
            <label className="label" htmlFor="mc-debts">
              Add'l debts $/mo
            </label>
            <input
              id="mc-debts"
              className="input money"
              inputMode="decimal"
              value={draft.additionalDebts}
              onChange={(e) =>
                onChange({ ...draft, additionalDebts: e.target.value })
              }
              placeholder="0"
            />
          </div>
        </div>
        {isTakeHome ? (
          <div>
            <label className="label" htmlFor="mc-utilities">
              Utilities $/mo
            </label>
            <input
              id="mc-utilities"
              className="input money"
              inputMode="decimal"
              value={draft.monthlyUtilities}
              onChange={(e) =>
                onChange({ ...draft, monthlyUtilities: e.target.value })
              }
              placeholder="250"
            />
            <p className="text-xs text-muted mt-1">
              Electric, gas, water, trash, internet — typical home costs not in
              your mortgage escrow.
            </p>
          </div>
        ) : null}
        {trackedMonthlyDebts > 0 ? (
          <div className="rounded-lg bg-surfaceVariant p-3 text-xs grid grid-cols-2 gap-x-4 gap-y-1 font-mono">
            <span className="text-muted">Tracked debts (your accounts)</span>
            <span className="text-right">{formatUsd(trackedMonthlyDebts)}</span>
            {additionalDebtsValue > 0 ? (
              <>
                <span className="text-muted">Additional</span>
                <span className="text-right">
                  {formatUsd(additionalDebtsValue)}
                </span>
              </>
            ) : null}
            <span className="text-body font-semibold">Total monthly debts</span>
            <span className="text-right text-body font-semibold">
              {formatUsd(input.monthlyDebts ?? 0)}
            </span>
          </div>
        ) : null}
        {(suggestedIncome ?? 0) > 0 ? (
          <button
            type="button"
            className="btn-ghost text-sm w-full"
            onClick={() =>
              onChange({
                ...draft,
                monthlyIncome: String(Math.round(suggestedIncome ?? 0)),
              })
            }
          >
            {isTakeHome
              ? `Use my base take-home (${formatUsd(suggestedIncome ?? 0)}/mo)`
              : `Use my gross income (${formatUsd(suggestedIncome ?? 0)}/mo)`}
          </button>
        ) : null}
        {isTakeHome &&
        conservativeTakeHome &&
        conservativeTakeHome.monthlyTakeHome > 0 ? (
          <p className="text-xs text-muted">
            Base take-home uses {conservativeTakeHome.paychecksPerMonth} paycheck
            {conservativeTakeHome.paychecksPerMonth === 1 ? "" : "s"} at{" "}
            {formatUsd(conservativeTakeHome.perPaycheckNet)} each (regular pay
            only — no overtime or bonuses).
          </p>
        ) : null}
        <div className="space-y-2">
          <button
            type="button"
            className="btn-ghost text-sm w-full"
            onClick={() =>
              onChange({
                ...draft,
                customizeThresholds: !draft.customizeThresholds,
              })
            }
          >
            {draft.customizeThresholds
              ? "Hide threshold settings"
              : "Customize comfort thresholds"}
          </button>
          {draft.customizeThresholds ? (
            <div className="rounded-lg bg-surfaceVariant p-3 space-y-3 text-sm">
              <p className="text-xs text-muted">
                {isTakeHome
                  ? "Ratios compare housing (PITI + utilities) and total costs to your take-home pay."
                  : "Standard lender debt-to-income limits (gross income)."}
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="label" htmlFor="mc-comfort-housing">
                    Comfortable housing ≤ %
                  </label>
                  <input
                    id="mc-comfort-housing"
                    className="input"
                    inputMode="decimal"
                    value={draft.comfortableHousingMax}
                    onChange={(e) =>
                      onChange({
                        ...draft,
                        customizeThresholds: true,
                        comfortableHousingMax: e.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <label className="label" htmlFor="mc-stretch-housing">
                    Stretched housing ≤ %
                  </label>
                  <input
                    id="mc-stretch-housing"
                    className="input"
                    inputMode="decimal"
                    value={draft.stretchedHousingMax}
                    onChange={(e) =>
                      onChange({
                        ...draft,
                        customizeThresholds: true,
                        stretchedHousingMax: e.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <label className="label" htmlFor="mc-comfort-total">
                    Comfortable total ≤ %
                  </label>
                  <input
                    id="mc-comfort-total"
                    className="input"
                    inputMode="decimal"
                    value={draft.comfortableTotalMax}
                    onChange={(e) =>
                      onChange({
                        ...draft,
                        customizeThresholds: true,
                        comfortableTotalMax: e.target.value,
                      })
                    }
                  />
                </div>
                <div>
                  <label className="label" htmlFor="mc-stretch-total">
                    Stretched total ≤ %
                  </label>
                  <input
                    id="mc-stretch-total"
                    className="input"
                    inputMode="decimal"
                    value={draft.stretchedTotalMax}
                    onChange={(e) =>
                      onChange({
                        ...draft,
                        customizeThresholds: true,
                        stretchedTotalMax: e.target.value,
                      })
                    }
                  />
                </div>
              </div>
              <button
                type="button"
                className="btn-ghost text-xs"
                onClick={() => {
                  const thresholds = defaultAffordabilityThresholds(
                    draft.affordabilityMode,
                  );
                  onChange({
                    ...draft,
                    customizeThresholds: false,
                    comfortableHousingMax: String(thresholds.comfortableHousingMax),
                    stretchedHousingMax: String(thresholds.stretchedHousingMax),
                    comfortableTotalMax: String(thresholds.comfortableTotalMax),
                    stretchedTotalMax: String(thresholds.stretchedTotalMax),
                  });
                }}
              >
                Reset to defaults (
                {defaultThresholds.comfortableHousingMax}/
                {defaultThresholds.comfortableTotalMax}%)
              </button>
            </div>
          ) : null}
        </div>
        <p className="text-xs text-muted">
          {isTakeHome ? (
            <>
              Uses a conservative{" "}
              <span className="font-medium">base take-home</span>: your modeled
              net pay after taxes and deductions on regular pay only (no
              overtime or bonuses), times paychecks in a typical month. Extra
              income is treated as savings, not housing budget. Many guides
              suggest keeping housing under{" "}
              {defaultThresholds.comfortableHousingMax}% of take-home to stay
              comfortable.
            </>
          ) : (
            <>
              Uses <span className="font-medium">gross (pre-tax)</span> monthly
              income — that&apos;s what lenders qualify you on.{" "}
              {trackedMonthlyDebts > 0
                ? "Your tracked debt payments are included automatically; add more above if you think that undershoots."
                : "Add any car, student, or card payments above."}{" "}
              Lenders generally want housing under{" "}
              {defaultThresholds.comfortableHousingMax}% and total debts under{" "}
              {defaultThresholds.comfortableTotalMax}% of gross income.
            </>
          )}
        </p>
      </div>
      </CollapsibleSection>

      <button type="button" className="btn-primary w-full" onClick={onEvaluate}>
        Calculate
      </button>
    </div>
  );
}

function ScenarioResultCard({
  result,
  onSaveAsAccount,
  saving,
}: {
  result: MortgageScenarioResult;
  onSaveAsAccount?: (result: MortgageScenarioResult) => void;
  saving?: boolean;
}) {
  const [showSchedule, setShowSchedule] = useState(false);
  const payoff = result.summary.payoffDateMs
    ? formatMortgageDate(result.summary.payoffDateMs)
    : "—";
  const aff = result.affordability;
  const pmiDrop = result.summary.pmiDropDateMs
    ? formatMortgageDate(result.summary.pmiDropDateMs)
    : null;
  const ratingStyle = aff.rating ? AFFORDABILITY_STYLES[aff.rating] : null;

  return (
    <article className="card p-4 space-y-3">
      <h3 className="font-semibold text-body">{result.label}</h3>
      <div className="grid grid-cols-2 gap-2 text-sm">
        <div>
          <p className="text-muted">Down payment</p>
          <p className="font-mono">
            {formatUsd(result.downPayment)}{" "}
            <span className="text-muted">
              ({result.downPaymentPercent.toFixed(1)}%)
            </span>
          </p>
        </div>
        <div>
          <p className="text-muted">Loan amount</p>
          <p className="font-mono">{formatUsd(result.summary.loanAmount)}</p>
        </div>
        <div>
          <p className="text-muted">Monthly P&amp;I</p>
          <p className="font-mono font-semibold text-moneyColor">
            {formatUsd(result.summary.monthlyPayment)}
          </p>
        </div>
        <div>
          <p className="text-muted">Est. total / mo</p>
          <p className="font-mono font-semibold">
            {formatUsd(result.summary.estimatedMonthlyTotal)}
          </p>
        </div>
        <div>
          <p className="text-muted">Total interest</p>
          <p className="font-mono">{formatUsd(result.summary.totalInterest)}</p>
        </div>
        <div>
          <p className="text-muted">Payoff</p>
          <p>{payoff}</p>
        </div>
      </div>
      <div className="border-t border-outline/50 pt-2 text-xs space-y-1">
        <p className="text-muted">Monthly breakdown</p>
        <div className="grid grid-cols-2 gap-x-4 gap-y-0.5 font-mono">
          <span className="text-muted">Principal &amp; interest</span>
          <span className="text-right">
            {formatUsd(result.summary.monthlyPayment)}
          </span>
          <span className="text-muted">Property tax</span>
          <span className="text-right">
            {formatUsd(result.breakdown.monthlyPropertyTax)}
          </span>
          <span className="text-muted">Home insurance</span>
          <span className="text-right">
            {formatUsd(result.breakdown.monthlyHomeInsurance)}
          </span>
          {result.breakdown.monthlyHoa > 0 ? (
            <>
              <span className="text-muted">HOA</span>
              <span className="text-right">
                {formatUsd(result.breakdown.monthlyHoa)}
              </span>
            </>
          ) : null}
          {result.breakdown.monthlyPmi > 0 ? (
            <>
              <span className="text-muted">PMI</span>
              <span className="text-right">
                {formatUsd(result.breakdown.monthlyPmi)}
              </span>
            </>
          ) : null}
        </div>
        {result.breakdown.monthlyPmi > 0 ? (
          <p className="text-muted pt-1">
            {pmiDrop
              ? `PMI drops off ~${pmiDrop} (≈${formatUsd(
                  result.summary.totalPmiPaid,
                )} total). After that, your payment falls by ${formatUsd(
                  result.breakdown.monthlyPmi,
                )}/mo.`
              : `PMI continues for the full term (≈${formatUsd(
                  result.summary.totalPmiPaid,
                )} total).`}
          </p>
        ) : null}
      </div>

      <div className="border-t border-outline/50 pt-2 text-xs">
        <div className="grid grid-cols-2 gap-x-4 gap-y-0.5 font-mono">
          <span className="text-muted">Cash to close</span>
          <span className="text-right">{formatUsd(aff.cashToClose)}</span>
          <span className="text-muted">(incl. closing costs)</span>
          <span className="text-right text-muted">
            {formatUsd(aff.closingCosts)}
          </span>
        </div>
      </div>

      {aff.housingDti != null ? (
        <div className="border-t border-outline/50 pt-2 text-xs space-y-1">
          <div className="flex items-center justify-between">
            <p className="text-muted">
              Affordability
              <span className="text-muted/80">
                {" "}
                ({aff.mode === "takehome" ? "take-home" : "lender"})
              </span>
            </p>
            {ratingStyle ? (
              <span className={clsx("font-semibold", ratingStyle.cls)}>
                {ratingStyle.label}
              </span>
            ) : null}
          </div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-0.5 font-mono">
            <span className="text-muted">
              {aff.mode === "takehome"
                ? "Housing + utilities"
                : "Housing ratio (front-end)"}
            </span>
            <span className="text-right">{aff.housingDti.toFixed(0)}%</span>
            <span className="text-muted">
              {aff.mode === "takehome" ? "Total w/ debts" : "Total debt (back-end)"}
            </span>
            <span className="text-right">{aff.totalDti?.toFixed(0)}%</span>
            {aff.monthlyUtilities > 0 ? (
              <>
                <span className="text-muted">Utilities</span>
                <span className="text-right">
                  {formatUsd(aff.monthlyUtilities)}/mo
                </span>
              </>
            ) : null}
            <span className="text-muted">Left after costs</span>
            <span className="text-right">
              {formatUsd(aff.monthlyIncomeAfterHousing ?? 0)}/mo
            </span>
          </div>
          <p className="text-muted pt-1">
            Comfortable when housing ≤ {aff.thresholds.comfortableHousingMax}% and
            total ≤ {aff.thresholds.comfortableTotalMax}%
            {aff.mode === "takehome" ? " of take-home pay" : " of gross income"}.
          </p>
        </div>
      ) : null}
      {onSaveAsAccount ? (
        <button
          type="button"
          className="btn-primary text-sm w-full"
          onClick={() => onSaveAsAccount(result)}
          disabled={saving}
        >
          {saving ? "Saving…" : "Save as mortgage account"}
        </button>
      ) : null}
      <button
        type="button"
        className="btn-ghost text-sm w-full"
        onClick={() => setShowSchedule((v) => !v)}
      >
        {showSchedule ? "Hide schedule" : "View payment schedule"}
      </button>
      {showSchedule ? (
        <div className="overflow-x-auto max-h-64 overflow-y-auto">
          <table className="w-full text-xs min-w-[28rem]">
            <thead>
              <tr className="text-muted border-b border-outline">
                <th className="py-1 pr-2 text-left">#</th>
                <th className="py-1 pr-2 text-left">Date</th>
                <th className="py-1 pr-2 text-right">Payment</th>
                <th className="py-1 pr-2 text-right">Principal</th>
                <th className="py-1 pr-2 text-right">Interest</th>
                <th className="py-1 text-right">Balance</th>
              </tr>
            </thead>
            <tbody>
              {result.rows.slice(0, showSchedule ? result.rows.length : 0).map((row) => (
                <tr key={row.paymentNumber} className="border-b border-outline/40">
                  <td className="py-1 pr-2">{row.paymentNumber}</td>
                  <td className="py-1 pr-2">{formatMortgageDate(row.dateMs)}</td>
                  <td className="py-1 pr-2 text-right font-mono">
                    {formatUsd(row.payment)}
                  </td>
                  <td className="py-1 pr-2 text-right font-mono">
                    {formatUsd(row.principal + row.extraPrincipal)}
                  </td>
                  <td className="py-1 pr-2 text-right font-mono">
                    {formatUsd(row.interest)}
                  </td>
                  <td className="py-1 text-right font-mono">
                    {formatUsd(row.balance)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </article>
  );
}

function CompareTable({ results }: { results: MortgageScenarioResult[] }) {
  if (results.length < 2) return null;

  const rows: { label: string; values: (string | number)[] }[] = [
    {
      label: "Down payment",
      values: results.map(
        (r) => `${formatUsd(r.downPayment)} (${r.downPaymentPercent.toFixed(1)}%)`,
      ),
    },
    {
      label: "Loan amount",
      values: results.map((r) => formatUsd(r.summary.loanAmount)),
    },
    {
      label: "Rate",
      values: results.map((r) => `${r.annualRate.toFixed(3)}%`),
    },
    {
      label: "Monthly P&I",
      values: results.map((r) => formatUsd(r.summary.monthlyPayment)),
    },
    {
      label: "Escrow / mo (tax, ins., HOA, PMI)",
      values: results.map((r) => formatUsd(r.breakdown.monthlyExtraTotal)),
    },
    {
      label: "Total / mo (PITI)",
      values: results.map((r) => formatUsd(r.summary.estimatedMonthlyTotal)),
    },
    {
      label: "Cash to close",
      values: results.map((r) => formatUsd(r.affordability.cashToClose)),
    },
    {
      label: "Housing ratio (front-end)",
      values: results.map((r) =>
        r.affordability.housingDti != null
          ? `${r.affordability.housingDti.toFixed(0)}%`
          : "—",
      ),
    },
    {
      label: "Total debt ratio (back-end)",
      values: results.map((r) =>
        r.affordability.totalDti != null
          ? `${r.affordability.totalDti.toFixed(0)}%`
          : "—",
      ),
    },
    {
      label: "Total interest",
      values: results.map((r) => formatUsd(r.summary.totalInterest)),
    },
    {
      label: "Total PMI",
      values: results.map((r) => formatUsd(r.summary.totalPmiPaid)),
    },
    {
      label: "Total cost",
      values: results.map((r) => formatUsd(r.summary.totalPaid)),
    },
  ];

  return (
    <section className="card p-4 overflow-x-auto">
      <h2 className="section-title mb-3">Compare Scenarios</h2>
      <table className="w-full text-sm min-w-[32rem]">
        <thead>
          <tr className="border-b border-outline text-muted">
            <th className="py-2 pr-3 text-left font-medium">Metric</th>
            {results.map((r) => (
              <th key={r.label} className="py-2 pr-3 text-left font-medium">
                {r.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className="border-b border-outline/50">
              <td className="py-2 pr-3 text-muted">{row.label}</td>
              {row.values.map((value, i) => (
                <td key={i} className="py-2 pr-3 font-mono">
                  {value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

export function MortgageCalculator({
  suggestedGrossMonthlyIncome,
  suggestedTakeHomeMonthlyIncome,
  conservativeTakeHome,
  trackedMonthlyDebts = 0,
  onSaveAsAccount,
  savingAccount,
}: {
  suggestedGrossMonthlyIncome?: number;
  suggestedTakeHomeMonthlyIncome?: number;
  conservativeTakeHome?: ConservativeMonthlyTakeHome;
  trackedMonthlyDebts?: number;
  onSaveAsAccount?: (result: MortgageScenarioResult) => void;
  savingAccount?: boolean;
} = {}) {
  const [draft, setDraft] = useState(() => defaultDraft("Scenario A"));
  const [saved, setSaved] = useState<MortgageScenarioResult[]>([]);
  const incomePrefilled = useRef(false);

  useEffect(() => {
    if (incomePrefilled.current) return;
    const suggested =
      draft.affordabilityMode === "takehome"
        ? suggestedTakeHomeMonthlyIncome
        : suggestedGrossMonthlyIncome;
    if (!suggested || suggested <= 0) return;
    incomePrefilled.current = true;
    setDraft((prev) =>
      num(prev.monthlyIncome) > 0
        ? prev
        : { ...prev, monthlyIncome: String(Math.round(suggested)) },
    );
  }, [
    suggestedGrossMonthlyIncome,
    suggestedTakeHomeMonthlyIncome,
    draft.affordabilityMode,
  ]);

  const preview = useMemo(
    () =>
      evaluateMortgageScenario(
        "Preview",
        draftToInput(draft, trackedMonthlyDebts),
      ),
    [draft, trackedMonthlyDebts],
  );

  const addScenario = () => {
    const result = evaluateMortgageScenario(
      draft.label || "Scenario",
      draftToInput(draft, trackedMonthlyDebts),
    );
    if (!result) return;
    setSaved((prev) => {
      const without = prev.filter((s) => s.label !== result.label);
      return [...without, result].slice(-4);
    });
  };

  const clearScenarios = () => setSaved([]);

  return (
    <div className="space-y-5">
      <div className="grid lg:grid-cols-2 gap-5">
        <section className="card p-5">
          <h2 className="section-title">Mortgage Calculator</h2>
          <p className="text-sm text-muted mt-1 mb-4">
            Model different down payments, rates, and terms. Scenarios are local
            only — they do not sync to your relay until you create an account.
          </p>
          <ScenarioForm
            draft={draft}
            onChange={setDraft}
            onEvaluate={addScenario}
            suggestedGrossMonthlyIncome={suggestedGrossMonthlyIncome}
            suggestedTakeHomeMonthlyIncome={suggestedTakeHomeMonthlyIncome}
            conservativeTakeHome={conservativeTakeHome}
            trackedMonthlyDebts={trackedMonthlyDebts}
          />
        </section>

        <section className="card p-5 space-y-3">
          <h2 className="section-title">Live Preview</h2>
          {preview ? (
            <ScenarioResultCard
              result={{ ...preview, label: "Live preview" }}
              onSaveAsAccount={onSaveAsAccount}
              saving={savingAccount}
            />
          ) : (
            <p className="text-sm text-muted">
              Enter a home price, down payment, and term to see estimates.
            </p>
          )}
          {saved.length > 0 ? (
            <button
              type="button"
              className="btn-ghost text-sm w-full"
              onClick={clearScenarios}
            >
              Clear saved scenarios
            </button>
          ) : null}
        </section>
      </div>

      {saved.length > 0 ? (
        <div className="space-y-3">
          <h2 className="section-title">Saved Scenarios</h2>
          <div className="grid md:grid-cols-2 gap-3">
            {saved.map((result) => (
              <ScenarioResultCard
                key={result.label}
                result={result}
                onSaveAsAccount={onSaveAsAccount}
                saving={savingAccount}
              />
            ))}
          </div>
        </div>
      ) : null}

      <CompareTable results={saved} />
    </div>
  );
}
