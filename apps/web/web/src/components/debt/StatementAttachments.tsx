import { useEffect, useRef, useState } from "react";
import {
  BlossomNotConfiguredError,
  downloadBlob,
  fetchBlossomStatus,
  type BlossomStatus,
} from "../../lib/blossom";
import type { CreditAccount } from "../../lib/creditAccount";
import { ApiError } from "../../lib/api";

function formatStatementDate(ms: number): string {
  if (ms <= 0) return "—";
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(ms));
}

export function StatementAttachments({
  account,
  onAttach,
  attaching,
}: {
  account: CreditAccount;
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

  const statements = [...account.statementEntries].sort(
    (a, b) => b.addedAt - a.addedAt,
  );

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
    setError(null);
    setViewingHash(hash);
    try {
      const blob = await downloadBlob(hash);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = label || `statement-${hash.slice(0, 8)}`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      if (e instanceof BlossomNotConfiguredError) {
        setError(e.message);
      } else if (e instanceof ApiError) {
        setError(e.message);
      } else {
        setError("Could not download statement.");
      }
    } finally {
      setViewingHash(null);
    }
  };

  return (
    <section className="card p-4 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="section-title">Statements</h2>
        {status?.configured ? (
          <span className="text-xs text-success">Blossom ready</span>
        ) : (
          <span className="text-xs text-muted">Blossom not configured</span>
        )}
      </div>

      {statements.length === 0 ? (
        <p className="text-sm text-muted">No statements attached.</p>
      ) : (
        <ul className="divide-y divide-outline">
          {statements.map((entry) => (
            <li
              key={entry.hash}
              className="flex items-center justify-between gap-3 py-2.5 text-sm"
            >
              <div className="min-w-0">
                <p className="truncate text-body">
                  {entry.label || "Statement"}
                </p>
                <p className="text-xs text-muted">
                  {formatStatementDate(entry.addedAt)}
                </p>
              </div>
              <button
                type="button"
                className="btn-ghost text-sm py-1 shrink-0"
                onClick={() => void onView(entry.hash, entry.label)}
                disabled={viewingHash === entry.hash}
              >
                {viewingHash === entry.hash ? "Loading…" : "Download"}
              </button>
            </li>
          ))}
        </ul>
      )}

      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept="application/pdf,image/*"
        onChange={(e) => void onPickFile(e.target.files?.[0] ?? null)}
      />

      <button
        type="button"
        className="btn-ghost w-full text-sm"
        onClick={() => inputRef.current?.click()}
        disabled={attaching || !status?.configured}
      >
        {attaching ? "Uploading…" : "Attach statement"}
      </button>

      {!status?.configured ? (
        <p className="text-xs text-muted">
          Set a Blossom server URL in the Android app Settings tab — it syncs via{" "}
          <code className="font-mono">fiatlife/settings/app</code> on your relay.
        </p>
      ) : null}

      {error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}
    </section>
  );
}
