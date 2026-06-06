import { useEffect, useState } from "react";

/**
 * Returns a timestamp that only changes on the given interval (default 60s),
 * so it stays stable across unrelated re-renders. This keeps `useMemo`
 * dependencies stable while still refreshing due-date/countdown calculations
 * periodically. Mirrors Android's `monthAnchor` ticking behavior.
 */
export function useNow(intervalMs = 60_000): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);

  return now;
}
