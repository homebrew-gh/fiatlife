import { useMemo } from "react";
import { formatUsd } from "../../lib/format";

type Props = {
  /** Total remaining balance at each month, index 0 = today. */
  timeline: number[];
  /** Optional labels for the endpoints (e.g. "Now" and a payoff date). */
  endLabel?: string;
};

const WIDTH = 320;
const HEIGHT = 120;
const PAD_X = 4;
const PAD_TOP = 8;
const PAD_BOTTOM = 4;

/** Lightweight SVG area chart of total debt declining to zero. */
export function PayoffChart({ timeline, endLabel }: Props) {
  const { areaPath, linePath, maxBalance } = useMemo(() => {
    const points = timeline.length >= 2 ? timeline : [...timeline, ...timeline];
    const max = Math.max(...points, 1);
    const n = points.length;
    const innerW = WIDTH - PAD_X * 2;
    const innerH = HEIGHT - PAD_TOP - PAD_BOTTOM;

    const xy = points.map((bal, i) => {
      const x = PAD_X + (n === 1 ? 0 : (i / (n - 1)) * innerW);
      const y = PAD_TOP + innerH - (bal / max) * innerH;
      return [x, y] as const;
    });

    const line = xy
      .map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`)
      .join(" ");
    const last = xy[xy.length - 1]!;
    const first = xy[0]!;
    const area =
      `${line} L${last[0].toFixed(1)},${(PAD_TOP + innerH).toFixed(1)}` +
      ` L${first[0].toFixed(1)},${(PAD_TOP + innerH).toFixed(1)} Z`;

    return { areaPath: area, linePath: line, maxBalance: max };
  }, [timeline]);

  return (
    <div>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="w-full h-auto"
        preserveAspectRatio="none"
        role="img"
        aria-label="Projected debt balance over time"
      >
        <defs>
          <linearGradient id="payoffFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="currentColor" stopOpacity="0.25" />
            <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
          </linearGradient>
        </defs>
        <path d={areaPath} fill="url(#payoffFill)" className="text-accent" />
        <path
          d={linePath}
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
          className="text-accent"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <div className="mt-1 flex justify-between text-xs text-muted">
        <span>Now · {formatUsd(maxBalance)}</span>
        <span>{endLabel ?? "Paid off"}</span>
      </div>
    </div>
  );
}
