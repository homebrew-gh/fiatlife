import { useState } from "react";
import {
  formatIsoDate,
  logEntryFromCalculation,
  parseIsoDate,
  type PaycheckCalculation,
  type PaycheckLogEntry,
  type SalaryConfig,
} from "../../lib/salary";

export function LogPaycheckSheet({
  open,
  onClose,
  config,
  calculation,
  editing,
  onSave,
}: {
  open: boolean;
  onClose: () => void;
  config: SalaryConfig;
  calculation: PaycheckCalculation;
  editing: PaycheckLogEntry | null;
  onSave: (entry: PaycheckLogEntry) => void;
}) {
  const [payDate, setPayDate] = useState(
    editing ? formatIsoDate(editing.payDate) : formatIsoDate(Date.now()),
  );
  const [grossPay, setGrossPay] = useState(
    editing ? String(editing.grossPay) : String(calculation.grossPay),
  );
  const [netPay, setNetPay] = useState(
    editing ? String(editing.netPay) : String(calculation.netPay),
  );
  const [totalTaxes, setTotalTaxes] = useState(
    editing
      ? String(editing.totalTaxes ?? calculation.totalTaxes)
      : String(calculation.totalTaxes),
  );
  const [overtimeHours, setOvertimeHours] = useState(
    editing
      ? String(editing.overtimeHours ?? config.overtimeHours)
      : String(config.overtimeHours),
  );
  const [notes, setNotes] = useState(editing?.notes ?? "");
  const [error, setError] = useState<string | null>(null);

  if (!open) return null;

  const fillFromCalculator = () => {
    const entry = logEntryFromCalculation(
      calculation,
      parseIsoDate(payDate) ?? Date.now(),
      Number.parseFloat(overtimeHours) || 0,
      notes.trim() || undefined,
    );
    setGrossPay(String(entry.grossPay));
    setNetPay(String(entry.netPay));
    setTotalTaxes(String(entry.totalTaxes ?? 0));
  };

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const dateMs = parseIsoDate(payDate);
    const gross = Number.parseFloat(grossPay);
    const net = Number.parseFloat(netPay);
    const taxes = Number.parseFloat(totalTaxes);
    const ot = Number.parseFloat(overtimeHours);
    if (!dateMs) {
      setError("Enter a valid pay date (YYYY-MM-DD).");
      return;
    }
    if (!Number.isFinite(gross) || !Number.isFinite(net)) {
      setError("Gross and net pay must be valid numbers.");
      return;
    }
    onSave({
      id: editing?.id ?? crypto.randomUUID(),
      payDate: dateMs,
      grossPay: gross,
      netPay: net,
      totalTaxes: Number.isFinite(taxes) ? taxes : undefined,
      totalPreTaxDeductions: calculation.totalPreTaxDeductions,
      totalPostTaxDeductions: calculation.totalPostTaxDeductions,
      overtimeHours: Number.isFinite(ot) ? ot : undefined,
      notes: notes.trim() || undefined,
    });
    onClose();
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="log-paycheck-title"
    >
      <div className="card w-full max-w-md p-5 max-h-[90vh] overflow-y-auto">
        <h2 id="log-paycheck-title" className="page-title text-xl">
          {editing ? "Edit Paycheck" : "Log Paycheck"}
        </h2>
        <p className="text-muted text-sm mt-1">
          Record actual earnings for your year-to-date summary.
        </p>
        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="pay-date">
              Pay date
            </label>
            <input
              id="pay-date"
              className="input"
              type="date"
              value={payDate}
              onChange={(e) => setPayDate(e.target.value)}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="log-gross">
                Gross pay
              </label>
              <input
                id="log-gross"
                className="input money"
                inputMode="decimal"
                value={grossPay}
                onChange={(e) => setGrossPay(e.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="log-net">
                Net pay
              </label>
              <input
                id="log-net"
                className="input money"
                inputMode="decimal"
                value={netPay}
                onChange={(e) => setNetPay(e.target.value)}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="log-taxes">
                Taxes withheld
              </label>
              <input
                id="log-taxes"
                className="input money"
                inputMode="decimal"
                value={totalTaxes}
                onChange={(e) => setTotalTaxes(e.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="log-ot">
                OT hours
              </label>
              <input
                id="log-ot"
                className="input"
                inputMode="decimal"
                value={overtimeHours}
                onChange={(e) => setOvertimeHours(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="log-notes">
              Notes (optional)
            </label>
            <input
              id="log-notes"
              className="input"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Bonus, adjusted OT…"
            />
          </div>
          <button
            type="button"
            className="btn-ghost text-sm w-full"
            onClick={fillFromCalculator}
          >
            Fill from calculator
          </button>
          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : null}
          <div className="flex gap-2 pt-2">
            <button type="button" className="btn-ghost flex-1" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn-primary flex-1">
              {editing ? "Update" : "Log"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
