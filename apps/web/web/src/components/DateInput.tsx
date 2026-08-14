import { useId, useRef } from "react";

type DateInputProps = {
  id?: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
};

export function DateInput({
  id: idProp,
  label,
  value,
  onChange,
  required,
  disabled,
}: DateInputProps) {
  const autoId = useId();
  const id = idProp ?? autoId;
  const inputRef = useRef<HTMLInputElement>(null);

  const openPicker = () => {
    const input = inputRef.current;
    if (!input || disabled) return;
    input.focus();
    if (typeof input.showPicker === "function") {
      try {
        input.showPicker();
      } catch {
        /* showPicker can throw if not triggered by user gesture */
      }
    }
  };

  return (
    <div>
      <label className="label" htmlFor={id}>
        {label}
      </label>
      <div className="relative flex items-center">
        <input
          ref={inputRef}
          id={id}
          type="date"
          className="input pr-10"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required={required}
          disabled={disabled}
        />
        <button
          type="button"
          className="absolute right-2 inline-flex h-8 w-8 items-center justify-center rounded-full text-muted hover:bg-surfaceVariant hover:text-body disabled:opacity-50"
          onClick={openPicker}
          disabled={disabled}
          aria-label={`Choose ${label.toLowerCase()}`}
          tabIndex={-1}
        >
          <CalendarIcon />
        </button>
      </div>
    </div>
  );
}

function CalendarIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      className="h-4 w-4"
      aria-hidden
    >
      <rect x="3" y="4" width="18" height="18" rx="2" />
      <path d="M16 2v4M8 2v4M3 10h18" />
    </svg>
  );
}
