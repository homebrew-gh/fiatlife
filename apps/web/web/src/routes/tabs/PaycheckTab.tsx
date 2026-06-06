import { useEffect, useMemo, useRef, useState } from "react";
import clsx from "clsx";
import { LogPaycheckSheet } from "../../components/paycheck/LogPaycheckSheet";
import { formatUsd } from "../../lib/format";
import { useSalaryData } from "../../lib/salaryData";
import {
  PAY_FREQUENCY_LABELS,
  formatIsoDate,
  formatPercent,
  logsForYear,
  parseIsoDate,
  summarizeYtd,
  type DirectDeposit,
  type PayFrequency,
  type PaycheckLogEntry,
  type TaxOverrides,
} from "../../lib/salary";
import { FILING_STATUS_LABELS, type FilingStatus } from "../../lib/tax";

type PaycheckView = "summary" | "calculator" | "annual";

const VIEWS: { id: PaycheckView; label: string }[] = [
  { id: "summary", label: "Summary" },
  { id: "calculator", label: "Calculator" },
  { id: "annual", label: "Annual" },
];

export function PaycheckTab() {
  const salary = useSalaryData();
  const [view, setView] = useState<PaycheckView>("summary");
  const [year, setYear] = useState(new Date().getFullYear());
  const [showLog, setShowLog] = useState(false);
  const [editingLog, setEditingLog] = useState<PaycheckLogEntry | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const ytd = useMemo(
    () =>
      summarizeYtd(
        salary.config,
        salary.calculation,
        salary.annualProjection,
        year,
      ),
    [salary.config, salary.calculation, salary.annualProjection, year],
  );

  const yearLogs = useMemo(
    () => logsForYear(salary.config, year),
    [salary.config, year],
  );

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await salary.reload();
    } finally {
      setRefreshing(false);
    }
  };

  const openLog = (entry?: PaycheckLogEntry) => {
    setEditingLog(entry ?? null);
    setShowLog(true);
  };

  const onLogSave = (entry: PaycheckLogEntry) => {
    if (editingLog) salary.updatePaycheckLog(entry);
    else salary.addPaycheckLog(entry);
    setEditingLog(null);
  };

  const onSaveAll = async () => {
    try {
      await salary.save();
    } catch {
      /* error shown via salary.error */
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="page-title">Paycheck</h1>
          <p className="text-sm text-muted mt-1">
            Track year-to-date earnings, model paychecks, and annual projections.
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => void onRefresh()}
            disabled={refreshing}
          >
            {refreshing ? "Syncing…" : "Sync"}
          </button>
          {salary.dirty ? (
            <button
              type="button"
              className="btn-primary text-sm"
              onClick={() => void onSaveAll()}
              disabled={salary.saving}
            >
              {salary.saving ? "Saving…" : "Save"}
            </button>
          ) : null}
        </div>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {VIEWS.map((v) => (
          <button
            key={v.id}
            type="button"
            onClick={() => setView(v.id)}
            className={clsx(
              view === v.id ? "filter-chip-active" : "filter-chip",
            )}
          >
            {v.label}
          </button>
        ))}
      </div>

      {salary.loading ? (
        <p className="text-muted text-sm">Loading paycheck data…</p>
      ) : null}

      {salary.error ? (
        <div className="card-quiet p-4 text-sm text-error" role="alert">
          {salary.error}
        </div>
      ) : null}

      {!salary.loading ? (
        <>
          {view === "summary" ? (
            <SummaryView
              year={year}
              onYearChange={setYear}
              ytd={ytd}
              logs={yearLogs}
              onLogPaycheck={() => openLog()}
              onEditLog={(e) => openLog(e)}
              onDeleteLog={(id) => salary.removePaycheckLog(id)}
              annualNet={salary.annualProjection.annualNetPay}
            />
          ) : null}
          {view === "calculator" ? (
            <CalculatorView
              config={salary.config}
              calc={salary.calculation}
              setConfig={salary.setConfig}
              onLogPaycheck={() => openLog()}
            />
          ) : null}
          {view === "annual" ? (
            <AnnualView
              annualOvertimeHours={salary.annualOvertimeHours}
              projection={salary.annualProjection}
              baseline={salary.annualBaseline}
              onOvertimeChange={salary.setAnnualOvertimeHours}
            />
          ) : null}
        </>
      ) : null}

      <LogPaycheckSheet
        key={editingLog?.id ?? "new-log"}
        open={showLog}
        onClose={() => {
          setShowLog(false);
          setEditingLog(null);
        }}
        config={salary.config}
        calculation={salary.calculation}
        editing={editingLog}
        onSave={onLogSave}
      />
    </div>
  );
}

function SummaryView({
  year,
  onYearChange,
  ytd,
  logs,
  onLogPaycheck,
  onEditLog,
  onDeleteLog,
  annualNet,
}: {
  year: number;
  onYearChange: (y: number) => void;
  ytd: ReturnType<typeof summarizeYtd>;
  logs: PaycheckLogEntry[];
  onLogPaycheck: () => void;
  onEditLog: (e: PaycheckLogEntry) => void;
  onDeleteLog: (id: string) => void;
  annualNet: number;
}) {
  const currentYear = new Date().getFullYear();

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="btn-ghost text-sm px-3"
            onClick={() => onYearChange(year - 1)}
            aria-label="Previous year"
          >
            ←
          </button>
          <span className="font-serif text-lg font-semibold text-heading">
            {year}
          </span>
          <button
            type="button"
            className="btn-ghost text-sm px-3"
            onClick={() => onYearChange(year + 1)}
            disabled={year >= currentYear}
            aria-label="Next year"
          >
            →
          </button>
        </div>
        <button type="button" className="btn-primary text-sm" onClick={onLogPaycheck}>
          Log paycheck
        </button>
      </div>

      <section className="card p-5 bg-dollar-gradient">
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs tracking-wider text-muted font-medium">
            Year-To-Date Net
          </p>
          <span
            className={clsx(
              "text-xs px-2 py-0.5 rounded-pill font-medium",
              ytd.source === "logged" ? "badge-success" : "badge-autopay",
            )}
          >
            {ytd.source === "logged" ? "Logged" : "Estimated"}
          </span>
        </div>
        <p className="money text-3xl mt-1">{formatUsd(ytd.netPay)}</p>
        <div className="mt-3 h-2 rounded-full bg-surfaceVariant overflow-hidden">
          <div
            className="h-full bg-primary rounded-full transition-all"
            style={{
              width: `${Math.min(100, ytd.progressPercent)}%`,
            }}
          />
        </div>
        <p className="text-xs text-muted mt-2">
          {ytd.progressPercent.toFixed(0)}% of projected annual net (
          {formatUsd(annualNet)})
        </p>
      </section>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Stat label="Gross YTD" value={formatUsd(ytd.grossPay)} />
        <Stat label="Taxes YTD" value={formatUsd(ytd.totalTaxes)} />
        <Stat label="Deductions YTD" value={formatUsd(ytd.totalDeductions)} />
        <Stat
          label={ytd.source === "logged" ? "Paychecks logged" : "Paychecks est."}
          value={String(ytd.paycheckCount)}
        />
      </div>

      <section className="card-quiet p-4 text-sm text-muted">
        <p>
          <span className="text-body font-medium">
            {ytd.scheduledPaychecksYtd}
          </span>{" "}
          paychecks received so far ·{" "}
          <span className="text-body font-medium">{ytd.remainingPaychecks}</span>{" "}
          remaining this year ({ytd.scheduledPaychecksInYear} total)
        </p>
      </section>

      <section className="card p-5">
        <h2 className="section-title">Paycheck Log</h2>
        <p className="text-sm text-muted mt-1 mb-4">
          {logs.length === 0
            ? "No paychecks logged for this year yet. Log your first paycheck to track actual earnings."
            : `${logs.length} paycheck${logs.length === 1 ? "" : "s"} recorded.`}
        </p>
        {logs.length === 0 ? null : (
          <ul className="space-y-2">
            {logs.map((entry) => (
              <li
                key={entry.id}
                className="flex items-center justify-between gap-3 py-2 divider-line last:border-0"
              >
                <div className="min-w-0">
                  <p className="text-body font-medium">
                    {formatIsoDate(entry.payDate)}
                  </p>
                  <p className="text-xs text-muted truncate">
                    Gross {formatUsd(entry.grossPay)}
                    {entry.overtimeHours
                      ? ` · ${entry.overtimeHours} OT hrs`
                      : ""}
                    {entry.notes ? ` · ${entry.notes}` : ""}
                  </p>
                </div>
                <div className="text-right shrink-0 flex items-center gap-2">
                  <p className="money text-base">{formatUsd(entry.netPay)}</p>
                  <button
                    type="button"
                    className="btn-ghost text-xs py-1 px-2"
                    onClick={() => onEditLog(entry)}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="btn-ghost text-xs py-1 px-2 text-error"
                    onClick={() => onDeleteLog(entry.id)}
                  >
                    Del
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function CalculatorView({
  config,
  calc,
  setConfig,
  onLogPaycheck,
}: {
  config: ReturnType<typeof useSalaryData>["config"];
  calc: ReturnType<typeof useSalaryData>["calculation"];
  setConfig: ReturnType<typeof useSalaryData>["setConfig"];
  onLogPaycheck: () => void;
}) {
  return (
    <div className="space-y-4">
      <section className="card p-5 bg-dollar-gradient text-center">
        <p className="text-xs tracking-wider text-muted font-medium">
          Net Take-Home (This Period)
        </p>
        <p className="money text-3xl mt-1">{formatUsd(calc.netPay)}</p>
        <div className="mt-4 flex justify-center gap-6 text-sm">
          <div>
            <p className="text-muted text-xs">Gross</p>
            <p className="money">{formatUsd(calc.grossPay)}</p>
          </div>
          <div>
            <p className="text-muted text-xs">Taxes</p>
            <p className="money">{formatUsd(calc.totalTaxes)}</p>
          </div>
          <div>
            <p className="text-muted text-xs">Annual net</p>
            <p className="money">{formatUsd(calc.annualizedNet)}</p>
          </div>
        </div>
        <button
          type="button"
          className="btn-primary text-sm mt-4"
          onClick={onLogPaycheck}
        >
          Log this paycheck
        </button>
      </section>

      <section className="card p-5 space-y-4">
        <h2 className="section-title">Pay Rate & Hours</h2>
        <NumberField
          label="Hourly rate"
          value={config.hourlyRate}
          onChange={(v) => setConfig((c) => ({ ...c, hourlyRate: v }))}
          money
        />
        <div className="grid grid-cols-2 gap-3">
          <NumberField
            label="Standard hours"
            value={config.standardHoursPerPeriod}
            onChange={(v) =>
              setConfig((c) => ({ ...c, standardHoursPerPeriod: v }))
            }
          />
          <NumberField
            label="OT hours"
            value={config.overtimeHours}
            onChange={(v) => setConfig((c) => ({ ...c, overtimeHours: v }))}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <NumberField
            label="OT multiplier"
            value={config.overtimeMultiplier}
            onChange={(v) =>
              setConfig((c) => ({ ...c, overtimeMultiplier: v }))
            }
          />
          <div>
            <label className="label" htmlFor="pay-freq">
              Pay frequency
            </label>
            <select
              id="pay-freq"
              className="input"
              value={config.payFrequency}
              onChange={(e) =>
                setConfig((c) => ({
                  ...c,
                  payFrequency: e.target.value as PayFrequency,
                }))
              }
            >
              {(Object.keys(PAY_FREQUENCY_LABELS) as PayFrequency[]).map(
                (f) => (
                  <option key={f} value={f}>
                    {PAY_FREQUENCY_LABELS[f]}
                  </option>
                ),
              )}
            </select>
          </div>
        </div>
        <div>
          <label className="label" htmlFor="first-payday">
            First payday of year
          </label>
          <input
            id="first-payday"
            className="input"
            type="date"
            value={
              config.firstPaydayOfYearMillis
                ? formatIsoDate(config.firstPaydayOfYearMillis)
                : ""
            }
            onChange={(e) =>
              setConfig((c) => ({
                ...c,
                firstPaydayOfYearMillis: parseIsoDate(e.target.value),
              }))
            }
          />
          <p className="text-xs text-muted mt-1">
            Used for YTD paycheck counting and dashboard monthly estimates.
          </p>
        </div>
        {calc.overtimePay > 0 ? (
          <div className="flex justify-between text-sm">
            <span className="text-muted">Regular pay</span>
            <span className="money">{formatUsd(calc.regularPay)}</span>
          </div>
        ) : null}
        {calc.overtimePay > 0 ? (
          <div className="flex justify-between text-sm">
            <span className="text-muted">Overtime pay</span>
            <span className="money text-success">
              {formatUsd(calc.overtimePay)}
            </span>
          </div>
        ) : null}
      </section>

      <section className="card p-5 space-y-3">
        <h2 className="section-title">Tax Configuration</h2>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="filing-status">
              Filing status
            </label>
            <select
              id="filing-status"
              className="input"
              value={config.filingStatus}
              onChange={(e) =>
                setConfig((c) => ({
                  ...c,
                  filingStatus: e.target.value as FilingStatus,
                }))
              }
            >
              {(Object.keys(FILING_STATUS_LABELS) as FilingStatus[]).map(
                (s) => (
                  <option key={s} value={s}>
                    {FILING_STATUS_LABELS[s]}
                  </option>
                ),
              )}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="state-code">
              State
            </label>
            <input
              id="state-code"
              className="input uppercase"
              maxLength={2}
              value={config.state}
              onChange={(e) =>
                setConfig((c) => ({
                  ...c,
                  state: e.target.value.toUpperCase().slice(0, 2),
                }))
              }
              placeholder="CA"
            />
          </div>
        </div>
        <div>
          <label className="label" htmlFor="county-name">
            County
          </label>
          <input
            id="county-name"
            className="input"
            value={config.county}
            onChange={(e) =>
              setConfig((c) => ({ ...c, county: e.target.value }))
            }
            placeholder="Optional — for local tax label"
          />
        </div>
        <p className="text-xs text-muted">
          Override any rate % to match your actual withholding (leave blank for
          estimated defaults).
        </p>
        <EditableTaxLine
          label="Federal income tax"
          amount={calc.federalTax}
          defaultRate={calc.federalMarginalRate}
          customRate={config.taxOverrides.customFederalTaxRate}
          exempt={config.taxOverrides.isExemptFromFederal}
          onExemptChange={(v) => updateTaxOverrides(setConfig, { isExemptFromFederal: v })}
          onRateChange={(v) =>
            updateTaxOverrides(setConfig, { customFederalTaxRate: v })
          }
        />
        <EditableTaxLine
          label="State income tax"
          amount={calc.stateTax}
          defaultRate={calc.stateTaxRate}
          customRate={config.taxOverrides.customStateTaxRate}
          exempt={config.taxOverrides.isExemptFromState}
          onExemptChange={(v) => updateTaxOverrides(setConfig, { isExemptFromState: v })}
          onRateChange={(v) =>
            updateTaxOverrides(setConfig, { customStateTaxRate: v })
          }
        />
        <EditableTaxLine
          label={config.county.trim() ? `${config.county} tax` : "County/local tax"}
          amount={calc.countyTax}
          defaultRate={calc.countyTaxRate}
          customRate={config.taxOverrides.customCountyTaxRate}
          exempt={config.taxOverrides.isExemptFromLocal}
          onExemptChange={(v) => updateTaxOverrides(setConfig, { isExemptFromLocal: v })}
          onRateChange={(v) =>
            updateTaxOverrides(setConfig, { customCountyTaxRate: v })
          }
        />
        <EditableTaxLine
          label="Social Security"
          amount={calc.socialSecurity}
          defaultRate={calc.socialSecurityRate}
          customRate={config.taxOverrides.customSocialSecurityRate}
          onRateChange={(v) =>
            updateTaxOverrides(setConfig, { customSocialSecurityRate: v })
          }
        />
        <EditableTaxLine
          label="Medicare"
          amount={calc.medicare}
          defaultRate={calc.medicareRate}
          customRate={config.taxOverrides.customMedicareRate}
          onRateChange={(v) =>
            updateTaxOverrides(setConfig, { customMedicareRate: v })
          }
        />
        <TaxLine label="Total taxes" amount={calc.totalTaxes} bold />
        <p className="text-xs text-muted">
          Effective rate: {formatPercent(calc.effectiveTaxRate)}
        </p>
      </section>

      <DeductionsSection
        title="Pre-Tax Deductions"
        deductions={config.preTaxDeductions}
        onChange={(list) =>
          setConfig((c) => ({ ...c, preTaxDeductions: list }))
        }
        isPreTax
      />
      <DeductionsSection
        title="Post-Tax Deductions"
        deductions={config.postTaxDeductions}
        onChange={(list) =>
          setConfig((c) => ({ ...c, postTaxDeductions: list }))
        }
        isPreTax={false}
      />

      <DirectDepositsSection
        deposits={config.directDeposits}
        allocations={calc.depositAllocations}
        onChange={(list) => setConfig((c) => ({ ...c, directDeposits: list }))}
      />
    </div>
  );
}

function updateTaxOverrides(
  setConfig: ReturnType<typeof useSalaryData>["setConfig"],
  patch: Partial<TaxOverrides>,
) {
  setConfig((c) => ({
    ...c,
    taxOverrides: { ...c.taxOverrides, ...patch },
  }));
}

function EditableTaxLine({
  label,
  amount,
  defaultRate,
  customRate,
  exempt,
  onExemptChange,
  onRateChange,
}: {
  label: string;
  amount: number;
  defaultRate: number;
  customRate?: number | null;
  exempt?: boolean;
  onExemptChange?: (exempt: boolean) => void;
  onRateChange: (rate: number | null) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-2 text-sm py-1">
      <div className="min-w-0">
        <span className="text-muted">{label}</span>
        {onExemptChange ? (
          <label className="flex items-center gap-1.5 text-xs text-muted mt-0.5 cursor-pointer">
            <input
              type="checkbox"
              checked={Boolean(exempt)}
              onChange={(e) => onExemptChange(e.target.checked)}
            />
            Exempt
          </label>
        ) : null}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <span className="money">{formatUsd(amount)}</span>
        <input
          className="input w-20 text-xs py-1 text-right"
          inputMode="decimal"
          placeholder={`${(defaultRate * 100).toFixed(1)}%`}
          value={customRate != null ? String((customRate * 100).toFixed(2)) : ""}
          disabled={exempt}
          onChange={(e) => {
            const v = e.target.value.trim();
            if (!v) onRateChange(null);
            else {
              const n = Number.parseFloat(v);
              if (Number.isFinite(n)) onRateChange(n / 100);
            }
          }}
          aria-label={`${label} override rate`}
        />
      </div>
    </div>
  );
}

function DirectDepositsSection({
  deposits,
  allocations,
  onChange,
}: {
  deposits: DirectDeposit[];
  allocations: ReturnType<typeof useSalaryData>["calculation"]["depositAllocations"];
  onChange: (list: DirectDeposit[]) => void;
}) {
  const add = () => {
    onChange([
      ...deposits,
      {
        id: crypto.randomUUID(),
        accountName: "",
        bankName: "",
        amount: 0,
        isPercentage: false,
        isRemainder: deposits.length === 0,
        sortOrder: deposits.length,
      },
    ]);
  };

  return (
    <section className="card p-5 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="section-title">Direct Deposits</h2>
        <button type="button" className="btn-ghost text-sm py-1" onClick={add}>
          Add
        </button>
      </div>
      {deposits.length === 0 ? (
        <p className="text-sm text-muted">
          Split take-home pay across accounts (percent, fixed amount, or
          remainder).
        </p>
      ) : (
        <ul className="space-y-3">
          {deposits.map((d) => (
            <li key={d.id} className="space-y-2 pb-3 divider-line last:border-0">
              <input
                className="input"
                value={d.accountName}
                onChange={(e) =>
                  onChange(
                    deposits.map((x) =>
                      x.id === d.id
                        ? { ...x, accountName: e.target.value }
                        : x,
                    ),
                  )
                }
                placeholder="Account nickname"
              />
              <input
                className="input text-sm"
                value={d.bankName}
                onChange={(e) =>
                  onChange(
                    deposits.map((x) =>
                      x.id === d.id ? { ...x, bankName: e.target.value } : x,
                    ),
                  )
                }
                placeholder="Bank name (optional)"
              />
              <div className="grid grid-cols-2 gap-2">
                <DecimalInput
                  className="input money"
                  value={d.amount}
                  onChange={(amount) =>
                    onChange(
                      deposits.map((x) =>
                        x.id === d.id ? { ...x, amount } : x,
                      ),
                    )
                  }
                />
                <label className="flex items-center gap-2 text-sm text-muted px-1">
                  <input
                    type="checkbox"
                    checked={d.isPercentage}
                    onChange={(e) =>
                      onChange(
                        deposits.map((x) =>
                          x.id === d.id
                            ? { ...x, isPercentage: e.target.checked }
                            : x,
                        ),
                      )
                    }
                  />
                  % of net
                </label>
              </div>
              <label className="flex items-center gap-2 text-sm text-muted">
                <input
                  type="checkbox"
                  checked={d.isRemainder}
                  onChange={(e) =>
                    onChange(
                      deposits.map((x) => ({
                        ...x,
                        isRemainder:
                          x.id === d.id
                            ? e.target.checked
                            : e.target.checked
                              ? false
                              : x.isRemainder,
                      })),
                    )
                  }
                />
                Remainder account
              </label>
              <button
                type="button"
                className="btn-ghost text-xs text-error py-1"
                onClick={() => onChange(deposits.filter((x) => x.id !== d.id))}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
      {allocations.length > 0 ? (
        <div className="border-t border-border pt-3 space-y-1">
          <p className="text-xs tracking-wider text-muted font-medium">
            This Paycheck Split
          </p>
          {allocations.map((a) => (
            <div
              key={a.deposit.id}
              className="flex justify-between text-sm gap-2"
            >
              <span className="text-muted truncate">
                {a.deposit.accountName || "Account"}
                {a.deposit.isRemainder ? " (remainder)" : ""}
              </span>
              <span className="money shrink-0">
                {formatUsd(a.calculatedAmount)}
              </span>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function AnnualView({
  annualOvertimeHours,
  projection,
  baseline,
  onOvertimeChange,
}: {
  annualOvertimeHours: number;
  projection: ReturnType<typeof useSalaryData>["annualProjection"];
  baseline: ReturnType<typeof useSalaryData>["annualBaseline"];
  onOvertimeChange: (h: number) => void;
}) {
  const otDelta = projection.annualOvertimePay - baseline.annualOvertimePay;

  return (
    <div className="space-y-4">
      <section className="card p-5 bg-dollar-gradient text-center">
        <p className="text-xs tracking-wider text-muted font-medium">
          Projected Annual Net
        </p>
        <p className="money text-3xl mt-1">
          {formatUsd(projection.annualNetPay)}
        </p>
        <p className="text-sm text-muted mt-2">
          ≈ {formatUsd(projection.perPaycheckNet)} per paycheck
        </p>
      </section>

      <section className="card p-5 space-y-3">
        <h2 className="section-title">Annual Overtime</h2>
        <NumberField
          label="OT hours for the year"
          value={annualOvertimeHours}
          onChange={onOvertimeChange}
        />
        {otDelta > 0 ? (
          <p className="text-sm text-success">
            +{formatUsd(otDelta)} gross from annual OT
          </p>
        ) : null}
      </section>

      <div className="grid grid-cols-2 gap-3">
        <Stat label="Annual gross" value={formatUsd(projection.annualGrossPay)} />
        <Stat label="Annual taxes" value={formatUsd(projection.annualTotalTaxes)} />
        <Stat
          label="Pre-tax deductions"
          value={formatUsd(projection.annualPreTaxDeductions)}
        />
        <Stat
          label="Post-tax deductions"
          value={formatUsd(projection.annualPostTaxDeductions)}
        />
      </div>

      <section className="card-quiet p-4 text-sm text-muted">
        Marginal federal rate: {formatPercent(projection.marginalFederalRate)} ·
        Effective tax rate: {formatPercent(projection.effectiveTaxRate)}
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="card-quiet p-4">
      <p className="text-xs text-muted">{label}</p>
      <p className="money text-xl mt-1">{value}</p>
    </div>
  );
}

function TaxLine({
  label,
  amount,
  bold = false,
}: {
  label: string;
  amount: number;
  bold?: boolean;
}) {
  return (
    <div className="flex justify-between text-sm">
      <span className={bold ? "text-body font-medium" : "text-muted"}>
        {label}
      </span>
      <span className={clsx("money", bold && "font-semibold")}>
        {formatUsd(amount)}
      </span>
    </div>
  );
}

function decimalDisplayValue(n: number): string {
  return n === 0 ? "" : String(n);
}

function DecimalInput({
  value,
  onChange,
  className,
  placeholder,
}: {
  value: number;
  onChange: (n: number) => void;
  className?: string;
  placeholder?: string;
}) {
  const [text, setText] = useState(() => decimalDisplayValue(value));

  useEffect(() => {
    setText((prev) => {
      const parsed = Number.parseFloat(prev);
      if (prev !== "" && prev !== "." && Number.isFinite(parsed) && parsed === value) {
        return prev;
      }
      return decimalDisplayValue(value);
    });
  }, [value]);

  return (
    <input
      className={className}
      inputMode="decimal"
      placeholder={placeholder}
      value={text}
      onChange={(e) => {
        const raw = e.target.value;
        if (raw !== "" && !/^\d*\.?\d*$/.test(raw)) return;
        setText(raw);
        if (raw === "" || raw === ".") {
          onChange(0);
          return;
        }
        const n = Number.parseFloat(raw);
        if (Number.isFinite(n)) onChange(n);
      }}
      onBlur={() => {
        const n = Number.parseFloat(text);
        setText(Number.isFinite(n) ? String(n) : "");
        if (Number.isFinite(n)) onChange(n);
        else onChange(0);
      }}
    />
  );
}

function NumberField({
  label,
  value,
  onChange,
  money = false,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  money?: boolean;
}) {
  return (
    <div>
      <label className="label">{label}</label>
      <DecimalInput
        className={clsx("input", money && "money")}
        value={value}
        onChange={onChange}
      />
    </div>
  );
}

function DeductionRow({
  deduction,
  isPreTax,
  autoFocusName,
  onNameFocused,
  onChange,
  onRemove,
}: {
  deduction: ReturnType<
    typeof useSalaryData
  >["config"]["preTaxDeductions"][number];
  isPreTax: boolean;
  autoFocusName: boolean;
  onNameFocused: () => void;
  onChange: (next: ReturnType<
    typeof useSalaryData
  >["config"]["preTaxDeductions"][number]) => void;
  onRemove: () => void;
}) {
  const nameRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!autoFocusName) return;
    nameRef.current?.focus();
    onNameFocused();
  }, [autoFocusName, onNameFocused]);

  return (
    <li className="space-y-2 pb-3 divider-line last:border-0">
      <input
        ref={nameRef}
        className="input"
        value={deduction.name}
        onChange={(e) => onChange({ ...deduction, name: e.target.value })}
        placeholder={isPreTax ? "e.g. 401(k)" : "e.g. Union dues"}
      />
      <div className="grid grid-cols-2 gap-2">
        <DecimalInput
          className="input money"
          value={deduction.amount}
          placeholder="0.00"
          onChange={(amount) => onChange({ ...deduction, amount })}
        />
        <label className="flex items-center gap-2 text-sm text-muted px-1">
          <input
            type="checkbox"
            checked={deduction.isPercentage}
            onChange={(e) =>
              onChange({ ...deduction, isPercentage: e.target.checked })
            }
          />
          % of gross
        </label>
      </div>
      <button
        type="button"
        className="btn-ghost text-xs text-error py-1"
        onClick={onRemove}
      >
        Remove
      </button>
    </li>
  );
}

function DeductionsSection({
  title,
  deductions,
  onChange,
  isPreTax,
}: {
  title: string;
  deductions: ReturnType<typeof useSalaryData>["config"]["preTaxDeductions"];
  onChange: (
    list: ReturnType<typeof useSalaryData>["config"]["preTaxDeductions"],
  ) => void;
  isPreTax: boolean;
}) {
  const [focusNameId, setFocusNameId] = useState<string | null>(null);

  const add = () => {
    const id = crypto.randomUUID();
    setFocusNameId(id);
    onChange([
      ...deductions,
      {
        id,
        name: "",
        amount: 0,
        isPercentage: false,
        isEnabled: true,
        type: isPreTax ? "PRE_TAX" : "POST_TAX",
      },
    ]);
  };

  return (
    <section className="card p-5 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="section-title">{title}</h2>
        <button type="button" className="btn-ghost text-sm py-1" onClick={add}>
          Add
        </button>
      </div>
      {deductions.length === 0 ? (
        <p className="text-sm text-muted">None configured.</p>
      ) : (
        <ul className="space-y-3">
          {deductions.map((d) => (
            <DeductionRow
              key={d.id}
              deduction={d}
              isPreTax={isPreTax}
              autoFocusName={d.id === focusNameId}
              onNameFocused={() => setFocusNameId(null)}
              onChange={(next) =>
                onChange(deductions.map((x) => (x.id === d.id ? next : x)))
              }
              onRemove={() => onChange(deductions.filter((x) => x.id !== d.id))}
            />
          ))}
        </ul>
      )}
    </section>
  );
}
