import { useEffect, useState } from "react";
import type { BillWithSource } from "../../lib/bill";
import { effectiveAmountDue } from "../../lib/bill";
import { formatUsd } from "../../lib/format";

export function PastDueAutopayDialog({
  bills,
  open,
  onClose,
  onConfirm,
  onDismiss,
  saving,
}: {
  bills: BillWithSource[];
  open: boolean;
  onClose: () => void;
  onConfirm: (items: BillWithSource[]) => Promise<void>;
  onDismiss: () => void;
  saving: boolean;
}) {
  const [selected, setSelected] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (open) setSelected(new Set(bills.map((b) => b.bill.id)));
  }, [open, bills]);

  if (!open || bills.length === 0) return null;

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectedItems = bills.filter((b) => selected.has(b.bill.id));
  const selectedTotal = selectedItems.reduce(
    (sum, item) => sum + effectiveAmountDue(item.bill),
    0,
  );

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
    >
      <div className="card w-full max-w-md p-5 max-h-[80vh] overflow-y-auto">
        <h2 className="page-title text-xl">Autopay Bills Past Due</h2>
        <p className="text-sm text-muted mt-1">
          Select the autopay bills that have been paid.
        </p>
        <ul className="mt-4 space-y-1">
          {bills.map((item) => (
            <li key={item.bill.id}>
              <label className="flex items-center gap-3 cursor-pointer border-b border-outline py-2 last:border-0">
                <input
                  type="checkbox"
                  checked={selected.has(item.bill.id)}
                  onChange={() => toggle(item.bill.id)}
                  disabled={saving}
                />
                <span className="min-w-0 flex-1">
                  <span className="block font-medium truncate">
                    {item.bill.name}
                  </span>
                </span>
                <span className="text-sm text-muted shrink-0">
                  {formatUsd(effectiveAmountDue(item.bill))}
                </span>
              </label>
            </li>
          ))}
        </ul>
        <div className="flex gap-2 mt-4">
          <button
            type="button"
            className="btn-ghost flex-1"
            onClick={onDismiss}
            disabled={saving}
          >
            Not now
          </button>
          <button
            type="button"
            className="btn-primary flex-1"
            onClick={async () => {
              if (selectedItems.length > 0) await onConfirm(selectedItems);
              onClose();
            }}
            disabled={saving || selectedItems.length === 0}
          >
            {selectedItems.length > 0
              ? `Mark ${selectedItems.length} paid (${formatUsd(selectedTotal)})`
              : "Mark paid"}
          </button>
        </div>
      </div>
    </div>
  );
}
