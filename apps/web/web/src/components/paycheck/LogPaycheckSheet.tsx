import { useMemo, useRef, useState } from "react";
import { DateInput } from "../DateInput";
import {
  formatDateInputValue,
  parseDateInput,
  todayDateInputValue,
} from "../../lib/dateInput";
import { formatUsd } from "../../lib/format";
import {
  BlossomNotConfiguredError,
  downloadBlob,
  uploadBlob,
} from "../../lib/blossom";
import { ApiError } from "../../lib/api";
import {
  EARNINGS_CATEGORIES,
  lineItemsFromCalculation,
  type PaycheckCalculation,
  type PaycheckLineItem,
  type PaycheckLogEntry,
  type SalaryConfig,
} from "../../lib/salary";

type LineKind = "earning" | "tax" | "preTax" | "postTax";

function newLine(label = ""): PaycheckLineItem {
  return { id: crypto.randomUUID(), label, amount: 0 };
}

function sumLines(lines: PaycheckLineItem[]): number {
  return lines.reduce(
    (s, l) => s + (Number.isFinite(l.amount) ? l.amount : 0),
    0,
  );
}

/** Synthesize structured lines for an older entry that only had totals. */
function linesFromEntry(entry: PaycheckLogEntry): {
  earnings: PaycheckLineItem[];
  taxes: PaycheckLineItem[];
  preTax: PaycheckLineItem[];
  postTax: PaycheckLineItem[];
} {
  const earnings =
    entry.earnings && entry.earnings.length > 0
      ? entry.earnings.map((l) => ({ ...l }))
      : [{ id: crypto.randomUUID(), label: "Regular", amount: entry.grossPay }];
  const taxes =
    entry.taxes && entry.taxes.length > 0
      ? entry.taxes.map((l) => ({ ...l }))
      : entry.totalTaxes
        ? [{ id: crypto.randomUUID(), label: "Taxes", amount: entry.totalTaxes }]
        : [];
  const preTax =
    entry.preTaxDeductions && entry.preTaxDeductions.length > 0
      ? entry.preTaxDeductions.map((l) => ({ ...l }))
      : entry.totalPreTaxDeductions
        ? [
            {
              id: crypto.randomUUID(),
              label: "Pre-tax",
              amount: entry.totalPreTaxDeductions,
            },
          ]
        : [];
  const postTax =
    entry.postTaxDeductions && entry.postTaxDeductions.length > 0
      ? entry.postTaxDeductions.map((l) => ({ ...l }))
      : entry.totalPostTaxDeductions
        ? [
            {
              id: crypto.randomUUID(),
              label: "Post-tax",
              amount: entry.totalPostTaxDeductions,
            },
          ]
        : [];
  return { earnings, taxes, preTax, postTax };
}

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
  const initial = useMemo(
    () =>
      editing
        ? linesFromEntry(editing)
        : { earnings: [], taxes: [], preTax: [], postTax: [] },
    [editing],
  );

  const [payDate, setPayDate] = useState(
    editing ? formatDateInputValue(editing.payDate) : todayDateInputValue(),
  );
  const [earnings, setEarnings] = useState<PaycheckLineItem[]>(initial.earnings);
  const [taxes, setTaxes] = useState<PaycheckLineItem[]>(initial.taxes);
  const [preTax, setPreTax] = useState<PaycheckLineItem[]>(initial.preTax);
  const [postTax, setPostTax] = useState<PaycheckLineItem[]>(initial.postTax);
  const [notes, setNotes] = useState(editing?.notes ?? "");
  const [attachmentHash, setAttachmentHash] = useState(
    editing?.attachmentHash ?? "",
  );
  const [attachmentLabel, setAttachmentLabel] = useState(
    editing?.attachmentLabel ?? "",
  );
  const [attaching, setAttaching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const gross = sumLines(earnings);
  const totalTaxes = sumLines(taxes);
  const totalPre = sumLines(preTax);
  const totalPost = sumLines(postTax);
  const net = gross - totalTaxes - totalPre - totalPost;

  if (!open) return null;

  const hasCalc = calculation.grossPay > 0;
  const lastEntry = (config.paycheckLog ?? [])
    .filter((e) => e.id !== editing?.id)
    .sort((a, b) => b.payDate - a.payDate)[0];

  const prefillFromCalculator = () => {
    const lines = lineItemsFromCalculation(calculation, config.overtimeHours);
    setEarnings(lines.earnings);
    setTaxes(lines.taxes);
    setPreTax(lines.preTaxDeductions);
    setPostTax(lines.postTaxDeductions);
    setError(null);
  };

  const copyFromLast = () => {
    if (!lastEntry) return;
    const lines = linesFromEntry(lastEntry);
    const reid = (l: PaycheckLineItem) => ({ ...l, id: crypto.randomUUID() });
    setEarnings(lines.earnings.map(reid));
    setTaxes(lines.taxes.map(reid));
    setPreTax(lines.preTax.map(reid));
    setPostTax(lines.postTax.map(reid));
    setNotes("");
    setError(null);
  };

  const onPickFile = async (file: File | null) => {
    if (!file) return;
    setError(null);
    setAttaching(true);
    try {
      const blob = await uploadBlob(file);
      setAttachmentHash(blob.sha256);
      setAttachmentLabel(file.name);
    } catch (e) {
      if (e instanceof BlossomNotConfiguredError || e instanceof ApiError) {
        setError(e.message);
      } else {
        setError("Could not attach paystub.");
      }
    } finally {
      setAttaching(false);
    }
  };

  const onViewAttachment = async () => {
    if (!attachmentHash) return;
    try {
      const blob = await downloadBlob(attachmentHash);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = attachmentLabel || "paystub";
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not download paystub.");
    }
  };

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const dateMs = parseDateInput(payDate);
    if (!dateMs) {
      setError("Enter a valid pay date.");
      return;
    }
    if (earnings.length === 0) {
      setError("Add at least one earnings line.");
      return;
    }
    const clean = (lines: PaycheckLineItem[]) =>
      lines
        .map((l) => ({
          ...l,
          label: l.label.trim() || "Other",
          amount: Number.isFinite(l.amount) ? l.amount : 0,
        }))
        .filter((l) => l.amount !== 0 || (l.hours ?? 0) !== 0);
    const otLine = earnings.find((l) => /overtime|^ot\b/i.test(l.label));
    onSave({
      id: editing?.id ?? crypto.randomUUID(),
      payDate: dateMs,
      grossPay: gross,
      netPay: net,
      totalTaxes,
      totalPreTaxDeductions: totalPre,
      totalPostTaxDeductions: totalPost,
      overtimeHours: otLine?.hours,
      notes: notes.trim() || undefined,
      earnings: clean(earnings),
      taxes: clean(taxes),
      preTaxDeductions: clean(preTax),
      postTaxDeductions: clean(postTax),
      attachmentHash: attachmentHash || undefined,
      attachmentLabel: attachmentLabel || undefined,
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
      <div className="card w-full max-w-md p-5 max-h-[92vh] overflow-y-auto">
        <h2 id="log-paycheck-title" className="page-title text-xl">
          {editing ? "Edit Paycheck" : "Log Paycheck"}
        </h2>
        <p className="text-muted text-sm mt-1">
          Enter your paystub line by line, or prefill and adjust.
        </p>

        <div className="flex flex-wrap gap-2 mt-3">
          {hasCalc ? (
            <button
              type="button"
              className="btn-ghost text-xs"
              onClick={prefillFromCalculator}
            >
              Prefill from calculator
            </button>
          ) : null}
          {lastEntry ? (
            <button
              type="button"
              className="btn-ghost text-xs"
              onClick={copyFromLast}
            >
              Copy last paycheck
            </button>
          ) : null}
        </div>

        <form className="mt-4 space-y-4" onSubmit={onSubmit}>
          <DateInput
            label="Pay date"
            value={payDate}
            onChange={setPayDate}
            required
          />

          <section className="rounded-card bg-dollar-gradient p-4">
            <div className="grid grid-cols-2 gap-y-1 text-sm">
              <span className="text-muted">Gross</span>
              <span className="money text-right">{formatUsd(gross)}</span>
              <span className="text-muted">Taxes</span>
              <span className="money text-right text-error">
                −{formatUsd(totalTaxes)}
              </span>
              <span className="text-muted">Deductions</span>
              <span className="money text-right text-error">
                −{formatUsd(totalPre + totalPost)}
              </span>
              <span className="text-body font-medium border-t border-border pt-1 mt-1">
                Net
              </span>
              <span className="money text-right text-lg border-t border-border pt-1 mt-1">
                {formatUsd(net)}
              </span>
            </div>
          </section>

          <LineSection
            title="Earnings"
            lines={earnings}
            onChange={setEarnings}
            kind="earning"
            addLabel="Regular"
          />
          <LineSection
            title="Taxes"
            lines={taxes}
            onChange={setTaxes}
            kind="tax"
            addLabel="Tax"
          />
          <LineSection
            title="Pre-Tax Deductions"
            lines={preTax}
            onChange={setPreTax}
            kind="preTax"
            addLabel="Deduction"
          />
          <LineSection
            title="Post-Tax Deductions"
            lines={postTax}
            onChange={setPostTax}
            kind="postTax"
            addLabel="Deduction"
          />

          <div>
            <label className="label" htmlFor="log-notes">
              Notes (optional)
            </label>
            <input
              id="log-notes"
              className="input"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. quarterly bonus, retro pay…"
            />
          </div>

          <div className="space-y-2">
            <label className="label">Paystub attachment</label>
            {attachmentHash ? (
              <div className="flex items-center justify-between gap-2 card-quiet p-3">
                <span className="text-sm truncate">
                  {attachmentLabel || "Paystub"}
                </span>
                <div className="flex gap-2 shrink-0">
                  <button
                    type="button"
                    className="btn-ghost text-xs"
                    onClick={() => void onViewAttachment()}
                  >
                    View
                  </button>
                  <button
                    type="button"
                    className="btn-ghost text-xs text-error"
                    onClick={() => {
                      setAttachmentHash("");
                      setAttachmentLabel("");
                    }}
                  >
                    Remove
                  </button>
                </div>
              </div>
            ) : (
              <button
                type="button"
                className="btn-ghost text-sm w-full"
                disabled={attaching}
                onClick={() => fileRef.current?.click()}
              >
                {attaching ? "Uploading…" : "+ Attach paystub (image or PDF)"}
              </button>
            )}
            <input
              ref={fileRef}
              type="file"
              className="sr-only"
              accept="image/*,application/pdf"
              onChange={(e) => void onPickFile(e.target.files?.[0] ?? null)}
            />
          </div>

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

function LineSection({
  title,
  lines,
  onChange,
  kind,
  addLabel,
}: {
  title: string;
  lines: PaycheckLineItem[];
  onChange: (lines: PaycheckLineItem[]) => void;
  kind: LineKind;
  addLabel: string;
}) {
  const total = sumLines(lines);
  const update = (id: string, patch: Partial<PaycheckLineItem>) =>
    onChange(lines.map((l) => (l.id === id ? { ...l, ...patch } : l)));
  const remove = (id: string) => onChange(lines.filter((l) => l.id !== id));
  const add = () => onChange([...lines, newLine(addLabel)]);

  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-medium text-heading">{title}</h3>
        <div className="flex items-center gap-2">
          {lines.length > 0 ? (
            <span className="money text-xs text-muted">{formatUsd(total)}</span>
          ) : null}
          <button type="button" className="btn-ghost text-xs py-1" onClick={add}>
            + Add
          </button>
        </div>
      </div>
      {lines.length === 0 ? (
        <p className="text-xs text-muted">None.</p>
      ) : (
        <ul className="space-y-2">
          {lines.map((l) => (
            <li key={l.id} className="flex items-center gap-2">
              <input
                className="input flex-1 text-sm py-1.5"
                value={l.label}
                list={kind === "earning" ? "earnings-categories" : undefined}
                placeholder="Label"
                onChange={(e) => update(l.id, { label: e.target.value })}
              />
              {kind === "earning" ? (
                <DecimalField
                  className="input w-16 text-sm py-1.5 text-right"
                  value={l.hours ?? 0}
                  placeholder="hrs"
                  onChange={(hours) => update(l.id, { hours })}
                />
              ) : null}
              <DecimalField
                className="input money w-24 text-sm py-1.5 text-right"
                value={l.amount}
                placeholder="0.00"
                onChange={(amount) => update(l.id, { amount })}
              />
              <button
                type="button"
                className="btn-ghost text-xs text-error px-1"
                aria-label="Remove line"
                onClick={() => remove(l.id)}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      {kind === "earning" ? (
        <datalist id="earnings-categories">
          {EARNINGS_CATEGORIES.map((c) => (
            <option key={c} value={c} />
          ))}
        </datalist>
      ) : null}
    </section>
  );
}

function DecimalField({
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
  const [text, setText] = useState(value === 0 ? "" : String(value));

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
        if (Number.isFinite(n)) {
          setText(String(n));
          onChange(n);
        } else {
          setText("");
          onChange(0);
        }
      }}
    />
  );
}
