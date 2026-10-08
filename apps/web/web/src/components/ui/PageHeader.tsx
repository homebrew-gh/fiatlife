import type { ReactNode } from "react";
import { Link } from "react-router-dom";

type PageHeaderProps = {
  title: string;
  description?: string;
  refreshing?: boolean;
  onRefresh?: () => void;
  refreshDisabled?: boolean;
  actions?: ReactNode;
  /** For pages opened from a tab rather than shown as one, e.g. `{ to: "/app", label: "Home" }`. */
  back?: { to: string; label: string };
};

/** Shared tab header: title, optional Refresh (local reload), and primary actions. */
export function PageHeader({
  title,
  description,
  refreshing = false,
  onRefresh,
  refreshDisabled = false,
  actions,
  back,
}: PageHeaderProps) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="flex items-start gap-3 min-w-0">
        {back ? (
          <Link to={back.to} className="btn-ghost text-sm py-1.5 shrink-0">
            ← {back.label}
          </Link>
        ) : null}
        <div className="min-w-0">
          <h1 className="page-title">{title}</h1>
          {description ? (
            <p className="text-sm text-muted mt-1">{description}</p>
          ) : null}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        {onRefresh ? (
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={onRefresh}
            disabled={refreshing || refreshDisabled}
          >
            {refreshing ? "Refreshing…" : "Refresh"}
          </button>
        ) : null}
        {actions}
      </div>
    </div>
  );
}
