import clsx from "clsx";

type SegmentedControlProps<T extends string> = {
  options: { id: T; label: string }[];
  value: T;
  onChange: (id: T) => void;
  ariaLabel: string;
  className?: string;
  /** `bar` = surface track (Paycheck); `pill` = bordered pills (Budget). */
  variant?: "bar" | "pill";
};

/** Shared segmented control for Summary/Model, chart metrics, etc. */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
  className,
  variant = "bar",
}: SegmentedControlProps<T>) {
  if (variant === "pill") {
    return (
      <div
        className={clsx(
          "flex shrink-0 rounded-pill border border-outline p-0.5 text-xs",
          className,
        )}
        role="tablist"
        aria-label={ariaLabel}
      >
        {options.map((option) => (
          <button
            key={option.id}
            type="button"
            role="tab"
            aria-selected={value === option.id}
            onClick={() => onChange(option.id)}
            className={clsx(
              "rounded-pill px-3 py-1 transition-colors",
              value === option.id
                ? "bg-primary text-onPrimary font-semibold"
                : "text-muted",
            )}
          >
            {option.label}
          </button>
        ))}
      </div>
    );
  }

  return (
    <div
      className={clsx(
        "flex gap-1 p-1 rounded-lg bg-surfaceVariant/80",
        className,
      )}
      role="tablist"
      aria-label={ariaLabel}
    >
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          role="tab"
          aria-selected={value === option.id}
          onClick={() => onChange(option.id)}
          className={clsx(
            "flex-1 text-xs sm:text-sm py-2 px-2 rounded-md font-medium transition-colors",
            value === option.id
              ? "bg-surface text-heading shadow-sm"
              : "text-muted hover:text-body",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
