import clsx from "clsx";
import { useSyncStatus } from "../lib/syncStatus";

export function SyncStatusOverlay() {
  const { toasts, dismissToast, pending, failed, retry } = useSyncStatus();

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex flex-col items-center gap-2 px-4">
      {(pending > 0 || failed > 0) && (
        <div
          className={clsx(
            "pointer-events-auto flex items-center gap-3 rounded-pill px-4 py-2 text-sm shadow-lg",
            failed > 0 ? "sync-badge-error" : "sync-badge-pending",
          )}
        >
          {failed > 0 ? (
            <>
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
            </>
          ) : (
            <>
              <span className="sync-spinner" aria-hidden />
              <span>
                Syncing {pending} change{pending === 1 ? "" : "s"}…
              </span>
            </>
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
