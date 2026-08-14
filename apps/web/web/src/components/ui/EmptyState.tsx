import type { ReactNode } from "react";

type EmptyStateProps = {
  title: string;
  description: ReactNode;
  action?: ReactNode;
};

/** Centered empty-state card with optional CTA. */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <section className="card p-8 text-center">
      <p className="font-medium text-body">{title}</p>
      <div className="text-sm text-muted mt-1">{description}</div>
      {action ? <div className="mt-4">{action}</div> : null}
    </section>
  );
}
