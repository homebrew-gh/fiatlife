import { useMemo } from "react";

export type DonutSlice = {
  label: string;
  value: number;
  color: string;
};

type DonutChartProps = {
  data: DonutSlice[];
  /** Outer diameter in pixels. */
  size?: number;
  /** Ring thickness in pixels. */
  thickness?: number;
  /** Large value rendered in the center (e.g. total). */
  centerLabel?: string;
  /** Small caption under the center label. */
  centerSubLabel?: string;
};

/**
 * Dependency-free SVG donut chart. Slices are drawn as arcs on a single circle
 * using stroke-dasharray, starting at the top and going clockwise. A thin
 * background ring keeps the shape readable when there's a single small slice.
 */
export function DonutChart({
  data,
  size = 200,
  thickness = 26,
  centerLabel,
  centerSubLabel,
}: DonutChartProps) {
  const radius = (size - thickness) / 2;
  const circumference = 2 * Math.PI * radius;
  const center = size / 2;

  const slices = useMemo(() => {
    const total = data.reduce((sum, s) => sum + Math.max(0, s.value), 0);
    if (total <= 0) return [];
    let offset = 0;
    return data
      .filter((s) => s.value > 0)
      .map((s) => {
        const fraction = s.value / total;
        const dash = fraction * circumference;
        const seg = {
          ...s,
          fraction,
          dashArray: `${dash} ${circumference - dash}`,
          // Negative offset advances the arc clockwise from the top.
          dashOffset: -offset,
        };
        offset += dash;
        return seg;
      });
  }, [data, circumference]);

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      role="img"
      aria-label="Spending breakdown donut chart"
    >
      <g transform={`rotate(-90 ${center} ${center})`}>
        <circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke="var(--fl-surface-variant)"
          strokeWidth={thickness}
        />
        {slices.map((s) => (
          <circle
            key={s.label}
            cx={center}
            cy={center}
            r={radius}
            fill="none"
            stroke={s.color}
            strokeWidth={thickness}
            strokeDasharray={s.dashArray}
            strokeDashoffset={s.dashOffset}
            strokeLinecap="butt"
          >
            <title>{`${s.label}: ${(s.fraction * 100).toFixed(0)}%`}</title>
          </circle>
        ))}
      </g>
      {centerLabel ? (
        <text
          x={center}
          y={centerSubLabel ? center - 4 : center}
          textAnchor="middle"
          dominantBaseline="central"
          className="fill-[var(--fl-money)] font-mono font-semibold"
          style={{ fontSize: size * 0.13 }}
        >
          {centerLabel}
        </text>
      ) : null}
      {centerSubLabel ? (
        <text
          x={center}
          y={center + size * 0.1}
          textAnchor="middle"
          dominantBaseline="central"
          className="fill-[var(--fl-on-surface-variant)]"
          style={{ fontSize: size * 0.06 }}
        >
          {centerSubLabel}
        </text>
      ) : null}
    </svg>
  );
}

/**
 * A pleasant categorical palette that reads well on both light and dark
 * surfaces. Colors repeat if there are more categories than entries.
 */
export const CHART_PALETTE = [
  "#2e9e5b",
  "#3b82f6",
  "#f59e0b",
  "#8b5cf6",
  "#ec4899",
  "#14b8a6",
  "#ef4444",
  "#eab308",
  "#6366f1",
  "#06b6d4",
  "#f97316",
  "#84cc16",
];

export function paletteColor(index: number): string {
  return CHART_PALETTE[index % CHART_PALETTE.length]!;
}
