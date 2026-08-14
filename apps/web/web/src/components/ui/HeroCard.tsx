import type { ReactNode } from "react";
import clsx from "clsx";

type HeroCardProps = {
  children: ReactNode;
  className?: string;
  /** Center content (default for summary heroes). */
  center?: boolean;
};

/** Dollar-gradient summary card used across Dashboard, Bills, Debt, Goals, Budget. */
export function HeroCard({
  children,
  className,
  center = false,
}: HeroCardProps) {
  return (
    <section
      className={clsx(
        "card p-5 bg-dollar-gradient",
        center && "text-center",
        className,
      )}
    >
      {children}
    </section>
  );
}
