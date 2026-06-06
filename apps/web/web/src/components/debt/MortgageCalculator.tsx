import { useMemo, useState } from "react";
import clsx from "clsx";
import { formatUsd } from "../../lib/format";
import {
  evaluateMortgageScenario,
  formatMortgageDate,
  loanAmountFromScenario,
  type MortgageScenarioInput,
  type MortgageScenarioResult,
} from "../../lib/mortgage";

const TERM_OPTIONS = [10, 15, 20, 25, 30];

type ScenarioDraft = MortgageScenarioInput & {
  label: string;
  downPaymentMode: "amount" | "percent";
  downPaymentPercent: string;
};

function defaultDraft(label: string): ScenarioDraft {
  return {
    label,
    homePrice: 400_000,
    downPayment: 80_000,
    downPaymentMode: "percent",
    downPaymentPercent: "20",
    annualRate: 6.5,
    termYears: 30,
    extraMonthlyPayment: 0,
    monthlyTaxInsurance: 0,
  };
}

function draftToInput(draft: ScenarioDraft): MortgageScenarioInput {
  let downPayment = draft.downPayment;
  if (draft.downPaymentMode === "percent") {
    const pct = Number.parseFloat(draft.downPaymentPercent) || 0;
    downPayment = draft.homePrice * (pct / 100);
  }
  return {
    homePrice: draft.homePrice,
    downPayment,
    annualRate: draft.annualRate,
    termYears: draft.termYears,
    extraMonthlyPayment: draft.extraMonthlyPayment,
    monthlyTaxInsurance: draft.monthlyTaxInsurance,
  };
}

function ScenarioForm({
  draft,
  onChange,
  onEvaluate,
}: {
  draft: ScenarioDraft;
  onChange: (next: ScenarioDraft) => void;
  onEvaluate: () => void;
}) {
  const loanAmount = loanAmountFromScenario(draftToInput(draft));

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
          value={draft.homePrice > 0 ? String(draft.homePrice) : ""}
          onChange={(e) =>
            onChange({
              ...draft,
              homePrice: Number.parseFloat(e.target.value) || 0,
            })
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
            value={draft.downPayment > 0 ? String(draft.downPayment) : ""}
            onChange={(e) =>
              onChange({
                ...draft,
                downPayment: Number.parseFloat(e.target.value) || 0,
              })
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
            value={String(draft.annualRate)}
            onChange={(e) =>
              onChange({
                ...draft,
                annualRate: Number.parseFloat(e.target.value) || 0,
              })
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
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="label" htmlFor="mc-extra">
            Extra monthly (principal)
          </label>
          <input
            id="mc-extra"
            className="input money"
            inputMode="decimal"
            value={
              (draft.extraMonthlyPayment ?? 0) > 0
                ? String(draft.extraMonthlyPayment)
                : ""
            }
            onChange={(e) =>
              onChange({
                ...draft,
                extraMonthlyPayment: Number.parseFloat(e.target.value) || 0,
              })
            }
            placeholder="0"
          />
        </div>
        <div>
          <label className="label" htmlFor="mc-tax">
            Tax + insurance / mo
          </label>
          <input
            id="mc-tax"
            className="input money"
            inputMode="decimal"
            value={
              (draft.monthlyTaxInsurance ?? 0) > 0
                ? String(draft.monthlyTaxInsurance)
                : ""
            }
            onChange={(e) =>
              onChange({
                ...draft,
                monthlyTaxInsurance: Number.parseFloat(e.target.value) || 0,
              })
            }
            placeholder="0"
          />
        </div>
      </div>
      <button type="button" className="btn-primary w-full" onClick={onEvaluate}>
        Calculate
      </button>
    </div>
  );
}

function ScenarioResultCard({ result }: { result: MortgageScenarioResult }) {
  const [showSchedule, setShowSchedule] = useState(false);
  const payoff = result.summary.payoffDateMs
    ? formatMortgageDate(result.summary.payoffDateMs)
    : "—";

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
          <p className="font-mono font-semibold text-money">
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
      label: "Total / mo (w/ tax & ins.)",
      values: results.map((r) => formatUsd(r.summary.estimatedMonthlyTotal)),
    },
    {
      label: "Total interest",
      values: results.map((r) => formatUsd(r.summary.totalInterest)),
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

export function MortgageCalculator() {
  const [draft, setDraft] = useState(() => defaultDraft("Scenario A"));
  const [saved, setSaved] = useState<MortgageScenarioResult[]>([]);

  const preview = useMemo(
    () => evaluateMortgageScenario("Preview", draftToInput(draft)),
    [draft],
  );

  const addScenario = () => {
    const result = evaluateMortgageScenario(draft.label || "Scenario", draftToInput(draft));
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
          />
        </section>

        <section className="card p-5 space-y-3">
          <h2 className="section-title">Live Preview</h2>
          {preview ? (
            <ScenarioResultCard result={{ ...preview, label: "Live preview" }} />
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
              <ScenarioResultCard key={result.label} result={result} />
            ))}
          </div>
        </div>
      ) : null}

      <CompareTable results={saved} />
    </div>
  );
}
