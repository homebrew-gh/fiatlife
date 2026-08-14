import clsx from "clsx";
import { useSyncStatus } from "../lib/syncStatus";

/** Human label for a FiatLife `d` tag so the error names the affected data. */
function labelForDTag(dTag: string): string {
  if (dTag === "fiatlife/salary") return "Paycheck";
  if (dTag === "fiatlife/budget") return "Budget";
  if (dTag.startsWith("fiatlife/bill/")) return "Bill";
  if (dTag.startsWith("fiatlife/goal/")) return "Goal";
  if (dTag === "subscription" || dTag.startsWith("subscription:")) return "Subscription";
  if (dTag === "deletion") return "Deletion";
  return dTag;
}

export function SyncStatusOverlay() {
  const { toasts, dismissToast, pending, failed, failedItems, retry, clearFailed } =
    useSyncStatus();

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4">
      {(pending > 0 || failed > 0) && (
        <div
          className={clsx(
            "pointer-events-auto flex flex-col gap-2 rounded-2xl px-4 py-2 text-sm shadow-lg",
            failed > 0 ? "sync-badge-error" : "sync-badge-pending",
          )}
        >
          {failed > 0 ? (
            <>
              <div className="flex items-center gap-3">
                <span>
                  {failed} change{failed === 1 ? "" : "s"} not synced
                </span>
                <button
                  type="button"
                  className="font-semibold underline underline-offset-2"
                  onClick={() => void retry()}
                >
                  Retry
                </button>
                <button
                  type="button"
                  className="font-semibold underline underline-offset-2"
                  onClick={() => void clearFailed()}
                >
                  Dismiss
                </button>
              </div>
              {failedItems.length > 0 && (
                <ul className="max-w-sm space-y-1 text-xs opacity-90">
                  {failedItems.slice(0, 4).map((item) => (
                    <li key={item.id} className="break-words">
                      <span className="font-medium">
                        {labelForDTag(item.label)}:
                      </span>{" "}
                      {item.error}
                    </li>
                  ))}
                </ul>
              )}
            </>
          ) : (
            <div className="flex items-center gap-3">
              <span className="sync-spinner" aria-hidden />
              <span>
                Syncing {pending} change{pending === 1 ? "" : "s"}…
              </span>
            </div>
          )}
        </div>
      )}

      {toasts.map((toast) => (
        <button
          key={toast.id}
          type="button"
          onClick={() => dismissToast(toast.id)}
          className={clsx(
            "pointer-events-auto max-w-sm rounded-2xl px-4 py-3 text-sm text-left shadow-lg",
            toast.kind === "error" && "toast-error",
            toast.kind === "success" && "toast-success",
            toast.kind === "info" && "toast-info",
          )}
        >
          {toast.message}
        </button>
      ))}
    </div>
  );
}
