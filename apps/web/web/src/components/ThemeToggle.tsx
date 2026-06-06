import { useTheme } from "../lib/theme";

export function ThemeToggle() {
  const { mode, toggle } = useTheme();
  const isDark = mode === "dark";

  return (
    <button
      type="button"
      onClick={toggle}
      className="btn-ghost text-sm py-1.5 px-3 gap-2"
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Light mode" : "Dark mode"}
    >
      <span aria-hidden className="text-base leading-none">
        {isDark ? "☀" : "☾"}
      </span>
      <span className="hidden sm:inline">{isDark ? "Light" : "Dark"}</span>
    </button>
  );
}
