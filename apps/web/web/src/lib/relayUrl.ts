export const RELAY_URL_POLICY =
  "Use wss:// for internet or LAN relays. On StartOS, use ws://your-relay-package.startos:PORT for container-to-container sync (e.g. ws://nostr-rs-relay.startos:8080).";

export function isAllowedRelayUrl(url: string): boolean {
  if (url.startsWith("wss://")) return true;
  if (!url.startsWith("ws://")) return false;
  try {
    const parsed = new URL(url);
    if (parsed.hostname.endsWith(".startos")) return true;
    return ["127.0.0.1", "localhost", "[::1]", "::1"].includes(parsed.hostname);
  } catch {
    return false;
  }
}

export function relayPrefillFromStatus(status: {
  relay_prefill_url?: string | null;
  suggested_relay_url?: string | null;
  detected_relay_url?: string | null;
} | null | undefined): string | null {
  const prefill = status?.relay_prefill_url?.trim();
  if (prefill) return prefill;
  const suggested = status?.suggested_relay_url?.trim();
  if (suggested) return suggested;
  const detected = status?.detected_relay_url?.trim();
  return detected || null;
}

export function relayUrlsFromStatus(status: {
  relay_url?: string | null;
  relay_urls?: string[] | null;
  detected_relay_url?: string | null;
  suggested_relay_url?: string | null;
  relay_prefill_url?: string | null;
} | null | undefined): string[] {
  if (status?.relay_urls?.length) return status.relay_urls;
  const single = status?.relay_url?.trim();
  if (single) return [single];
  const detected = status?.detected_relay_url?.trim();
  return detected ? [detected] : [];
}

export function hasRelayConfigured(status: {
  relay_url?: string | null;
  relay_urls?: string[] | null;
  detected_relay_url?: string | null;
  suggested_relay_url?: string | null;
  relay_prefill_url?: string | null;
} | null | undefined): boolean {
  return relayUrlsFromStatus(status).length > 0;
}

export function relayHostLabel(relayUrl: string | null | undefined): string | null {
  if (!relayUrl) return null;
  try {
    const parsed = new URL(relayUrl);
    return parsed.port ? `${parsed.hostname}:${parsed.port}` : parsed.hostname;
  } catch {
    return relayUrl;
  }
}
