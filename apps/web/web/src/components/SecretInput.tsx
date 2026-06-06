import { useState } from "react";
import clsx from "clsx";

export function SecretInput({
  id,
  value,
  onChange,
  placeholder,
  autoComplete,
  autoFocus,
  className,
}: {
  id: string;
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  autoComplete?: string;
  autoFocus?: boolean;
  className?: string;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="relative">
      <input
        id={id}
        className={clsx("input pr-10", className)}
        type={visible ? "text" : "password"}
        autoComplete={autoComplete}
        autoFocus={autoFocus}
        placeholder={placeholder}
        value={value}
        onChange={onChange}
      />
      <button
        type="button"
        className="absolute inset-y-0 right-0 flex items-center px-3 text-muted hover:text-body transition-colors"
        onClick={() => setVisible((show) => !show)}
        aria-label={visible ? "Hide value" : "Show value"}
        aria-pressed={visible}
      >
        {visible ? "Hide" : "Show"}
      </button>
    </div>
  );
}
