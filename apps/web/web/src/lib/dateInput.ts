/** `YYYY-MM-DD` for `<input type="date">`. */
export function formatDateInputValue(ms: number): string {
  const d = new Date(ms);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function todayDateInputValue(): string {
  return formatDateInputValue(Date.now());
}

/** Parse `YYYY-MM-DD` to local midnight epoch ms. */
export function parseDateInput(value: string): number | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  const d = new Date(`${trimmed}T00:00:00`);
  return Number.isFinite(d.getTime()) ? d.getTime() : null;
}
