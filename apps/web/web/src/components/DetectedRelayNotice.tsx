import type { AuthStatus } from "../lib/api";

export function DetectedRelayNotice({ status }: { status: AuthStatus }) {
  const url = status.detected_relay_url?.trim();
  if (!url) return null;
  const label = status.detected_relay_label?.trim() || "Nostr RS Relay";

  return (
    <div
      className="notice-panel p-3 text-sm space-y-1"
      role="status"
    >
      <p className="text-body">
        This Start9 server has <span className="font-semibold">{label}</span> installed.
        FiatLife will sync through it automatically — no relay URL to enter.
      </p>
      <p className="text-muted text-xs font-mono break-all">{url}</p>
    </div>
  );
}

export function hasDetectedRelay(status: AuthStatus | null | undefined): boolean {
  return Boolean(status?.detected_relay_url?.trim());
}
