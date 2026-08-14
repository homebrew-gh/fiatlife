import { useState, type ReactNode } from "react";
import clsx from "clsx";

type CollapsibleSectionProps = {
  title: string;
  children: ReactNode;
  defaultOpen?: boolean;
  summary?: string;
  className?: string;
  /** When true, header is a quiet bar and children keep their own cards. */
  bare?: boolean;
};

/** Progressive-disclosure card: collapsed by default for advanced content. */
export function CollapsibleSection({
  title,
  children,
  defaultOpen = false,
  summary,
  className,
  bare = false,
}: CollapsibleSectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <section
      className={clsx(
        bare ? "space-y-3" : "card overflow-hidden",
        className,
      )}
    >
      <button
        type="button"
        className={clsx(
          "w-full flex items-center justify-between gap-3 text-left",
          bare ? "card-quiet px-4 py-3 rounded-card" : "p-5",
        )}
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <div className="min-w-0">
          <h2 className={bare ? "font-serif text-base font-semibold text-heading" : "section-title"}>
            {title}
          </h2>
          {!open && summary ? (
            <p className="text-sm text-muted mt-0.5 truncate">{summary}</p>
          ) : null}
        </div>
        <span className="text-muted shrink-0 text-sm" aria-hidden>
          {open ? "▾" : "▸"}
        </span>
      </button>
      {open ? (
        <div className={clsx(bare ? "space-y-4" : "px-5 pb-5 space-y-4")}>
          {children}
        </div>
      ) : null}
    </section>
  );
}
