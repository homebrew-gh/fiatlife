import clsx from "clsx";

export function Logo({ className }: { className?: string }) {
  return (
    <div className={clsx("flex items-center gap-2", className)}>
      <span
        className="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-onPrimary font-serif font-bold text-lg shadow-card"
        aria-hidden
      >
        $
      </span>
      <span className="font-serif font-bold text-xl text-heading tracking-tight">
        FiatLife
      </span>
    </div>
  );
}
