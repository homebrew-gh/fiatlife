import type { ReactNode } from "react";

type PageHeaderProps = {
  title: string;
  description?: string;
  refreshing?: boolean;
  onRefresh?: () => void;
  refreshDisabled?: boolean;
  actions?: ReactNode;
};

/** Shared tab header: title, optional Refresh (local reload), and primary actions. */
export function PageHeader({
  title,
  description,
  refreshing = false,
  onRefresh,
  refreshDisabled = false,
  actions,
}: PageHeaderProps) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div>
        <h1 className="page-title">{title}</h1>
        {description ? (
          <p className="text-sm text-muted mt-1">{description}</p>
        ) : null}
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
