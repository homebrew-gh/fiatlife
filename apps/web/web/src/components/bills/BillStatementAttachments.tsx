import { useEffect, useRef, useState } from "react";
import {
  BlossomNotConfiguredError,
  downloadBlob,
  fetchBlossomStatus,
  type BlossomStatus,
} from "../../lib/blossom";
import { statementsOrderedByDate, type Bill } from "../../lib/bill";
import { ApiError } from "../../lib/api";

function formatStatementDate(ms: number): string {
  if (ms <= 0) return "—";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(ms));
}

export function BillStatementAttachments({
  bill,
  onAttach,
  attaching,
}: {
  bill: Bill;
  onAttach: (file: File) => Promise<void>;
  attaching: boolean;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<BlossomStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [viewingHash, setViewingHash] = useState<string | null>(null);

  useEffect(() => {
    void fetchBlossomStatus()
      .then(setStatus)
      .catch(() => setStatus({ configured: false, url: null }));
  }, []);

  const statements = statementsOrderedByDate(bill);

  const onPickFile = async (file: File | null) => {
    if (!file) return;
    setError(null);
    try {
      await onAttach(file);
    } catch (e) {
      if (e instanceof BlossomNotConfiguredError) {
        setError(e.message);
      } else if (e instanceof ApiError) {
        setError(e.message);
      } else {
        setError("Could not attach statement.");
      }
    }
  };

  const onView = async (hash: string, label: string) => {
    setViewingHash(hash);
    setError(null);
    try {
      const blob = await downloadBlob(hash);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = label || "statement";
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not download.");
    } finally {
      setViewingHash(null);
    }
  };

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h3 className="font-medium">Statements</h3>
        <button
          type="button"
          className="btn-ghost text-sm"
          disabled={attaching || status?.configured === false}
          onClick={() => inputRef.current?.click()}
        >
          {attaching ? "Uploading…" : "+ Attach"}
        </button>
        <input
          ref={inputRef}
          type="file"
          className="sr-only"
          accept="image/*,application/pdf"
          onChange={(e) => void onPickFile(e.target.files?.[0] ?? null)}
        />
      </div>
      {status && !status.configured ? (
        <p className="text-xs text-muted">
          Configure Blossom in Settings to attach statements.
        </p>
      ) : null}
      {error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}
      {statements.length === 0 ? (
        <p className="text-sm text-muted">No statements attached.</p>
      ) : (
        <ul className="space-y-2">
          {statements.map((stmt) => (
            <li
              key={stmt.hash}
              className="flex items-center justify-between gap-2 card-quiet p-3"
            >
              <div className="min-w-0">
                <p className="text-sm font-medium truncate">{stmt.label}</p>
                <p className="text-xs text-muted">
                  {formatStatementDate(stmt.addedAt)}
                </p>
              </div>
              <button
                type="button"
                className="btn-ghost text-sm shrink-0"
                disabled={viewingHash === stmt.hash}
                onClick={() => void onView(stmt.hash, stmt.label)}
              >
                {viewingHash === stmt.hash ? "…" : "View"}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
