import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        bg: "var(--fl-bg)",
        surface: "var(--fl-surface)",
        surfaceVariant: "var(--fl-surface-variant)",
        primary: "var(--fl-primary)",
        onPrimary: "var(--fl-on-primary)",
        primaryContainer: "var(--fl-primary-container)",
        onPrimaryContainer: "var(--fl-on-primary-container)",
        secondary: "var(--fl-secondary)",
        dollarBill: "var(--fl-dollar-bill)",
        heading: "var(--fl-heading)",
        moneyColor: "var(--fl-money)",
        onSurface: "var(--fl-on-surface)",
        onSurfaceVariant: "var(--fl-on-surface-variant)",
        outline: "var(--fl-outline)",
        error: "var(--fl-error)",
        success: "var(--fl-success)",
        warn: "var(--fl-warn)",
      },
      fontFamily: {
        sans: ["DM Sans", "system-ui", "sans-serif"],
        serif: ["Source Serif 4", "Georgia", "serif"],
        mono: [
          "IBM Plex Mono",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "monospace",
        ],
      },
      borderRadius: {
        card: "12px",
        pill: "9999px",
      },
      boxShadow: {
        card: "var(--fl-shadow-card)",
        bill: "var(--fl-shadow-bill)",
      },
      backgroundImage: {
        "dollar-gradient": "var(--fl-dollar-gradient)",
      },
    },
  },
  plugins: [],
} satisfies Config;
