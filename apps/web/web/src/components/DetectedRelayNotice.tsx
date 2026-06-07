import type { AuthStatus } from "../lib/api";
import { relayPrefillFromStatus } from "../lib/relayUrl";

export function DetectedRelayNotice({ status }: { status: AuthStatus }) {
  const prefill = relayPrefillFromStatus(status);
  if (!prefill) return null;
  const label = status.detected_relay_label?.trim() || "Nostr RS Relay";

  return (
    <div className="notice-panel p-3 text-sm space-y-1" role="status">
      <p className="text-body">
        Found <span className="font-semibold">{label}</span> on this Start9 server.
        The relay URL below was filled in automatically — edit it if you use a different
        relay than Android.
      </p>
      <p className="text-muted text-xs font-mono break-all">{prefill}</p>
    </div>
  );
}

export function hasDetectedRelay(status: AuthStatus | null | undefined): boolean {
  return Boolean(relayPrefillFromStatus(status));
}
