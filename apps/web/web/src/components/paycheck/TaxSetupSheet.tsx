import { FILING_STATUS_LABELS, type FilingStatus } from "../../lib/tax";
import type { SalaryConfig } from "../../lib/salary";

export function TaxSetupSheet({
  open,
  onClose,
  config,
  setConfig,
}: {
  open: boolean;
  onClose: () => void;
  config: SalaryConfig;
  setConfig: (updater: (c: SalaryConfig) => SalaryConfig) => void;
}) {
  if (!open) return null;

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="tax-setup-title"
      onClick={onClose}
    >
      <div
        className="card w-full max-w-md p-5 max-h-[92vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="tax-setup-title" className="page-title text-xl">
          Tax setup
        </h2>
        <p className="text-muted text-sm mt-1">
          Filing status and state drive federal and state withholding estimates.
          Override individual rates on the Model tab if your stub differs.
        </p>

        <div className="mt-4 space-y-3">
          <div>
            <label className="label" htmlFor="tax-setup-filing-status">
              Filing status
            </label>
            <select
              id="tax-setup-filing-status"
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
            <label className="label" htmlFor="tax-setup-state">
              State
            </label>
            <input
              id="tax-setup-state"
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
            <p className="text-xs text-muted mt-1">
              Required for state income tax estimates.
            </p>
          </div>
          <div>
            <label className="label" htmlFor="tax-setup-county">
              County
            </label>
            <input
              id="tax-setup-county"
              className="input"
              value={config.county}
              onChange={(e) =>
                setConfig((c) => ({ ...c, county: e.target.value }))
              }
              placeholder="Optional — for local tax label"
            />
          </div>
        </div>

        <button
          type="button"
          className="btn-primary w-full mt-5"
          onClick={onClose}
        >
          Done
        </button>
      </div>
    </div>
  );
}
