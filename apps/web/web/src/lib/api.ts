export type AuthStatus = {
  has_state: boolean;
  unlocked: boolean;
  npub?: string | null;
  relay_url?: string | null;
  relay_urls?: string[];
  detected_relay_url?: string | null;
  detected_relay_label?: string | null;
};

export type AppDataRecord = {
  event_id: string;
  d_tag?: string | null;
  ciphertext: string;
  plaintext?: string | null;
  decrypt_error?: string | null;
  tags?: string[][];
};

export type SetupBody = {
  nsec: string;
  passphrase: string;
  relay_url?: string;
};

export type UnlockBody = {
  passphrase: string;
};

export type WipeBody = {
  passphrase: string;
  confirmation: string;
};

export type RelaySettingsInput = {
  relay_url?: string;
  relay_urls?: string[];
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

async function request<T>(
  path: string,
  init?: RequestInit & { json?: unknown },
): Promise<T> {
  const headers = new Headers(init?.headers);
  let body: BodyInit | undefined = init?.body ?? undefined;
  if (init?.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.json);
  }
  headers.set("Accept", "application/json");
  const res = await fetch(path, {
    ...init,
    headers,
    body,
    credentials: "same-origin",
  });
  const text = await res.text();
  let parsed: unknown = undefined;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = { error: text };
    }
  }
  if (!res.ok) {
    const msg =
      (parsed as { error?: string } | undefined)?.error ??
      `${res.status} ${res.statusText}`;
    if (res.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }
    throw new ApiError(res.status, msg);
  }
  return parsed as T;
}

export const api = {
  health: () => request<{ ok: boolean }>("/api/health"),
  authStatus: () => request<AuthStatus>("/api/auth/status"),
  authSetup: (body: SetupBody) =>
    request<AuthStatus>("/api/auth/setup", { method: "POST", json: body }),
  authUnlock: (body: UnlockBody) =>
    request<AuthStatus>("/api/auth/unlock", { method: "POST", json: body }),
  authLock: () =>
    request<{ ok: boolean }>("/api/auth/lock", { method: "POST" }),
  authWipe: (body: WipeBody) =>
    request<{ ok: boolean }>("/api/auth/wipe", { method: "POST", json: body }),
  getRelay: () =>
    request<{ relay_url: string; relay_urls: string[] }>("/api/settings/relay"),
  setRelay: (body: RelaySettingsInput) =>
    request<{ relay_url: string; relay_urls: string[] }>("/api/settings/relay", {
      method: "PUT",
      json: body,
    }),
  listAppData: () => request<AppDataRecord[]>("/api/nostr/app-data"),
  relayConnection: () =>
    request<{ connected: boolean; message?: string | null }>(
      "/api/nostr/connection",
    ),
  publishAppData: (body: { d_tag: string; plaintext: string }) =>
    request<{ event_id: string }>("/api/nostr/app-data", {
      method: "POST",
      json: body,
    }),
  listCypherLogSubscriptions: () =>
    request<
      Array<{
        event_id: string;
        d_tag: string;
        created_at: number;
        tags: string[][];
        content: string;
        plaintext?: string | null;
        decrypt_error?: string | null;
      }>
    >("/api/nostr/cypherlog/subscriptions"),
  publishCypherLogSubscription: (body: { tags: string[][] }) =>
    request<{ event_id: string }>("/api/nostr/cypherlog/subscription", {
      method: "POST",
      json: body,
    }),
  publishNostrDeletion: (body: { kind: number; d_tag: string }) =>
    request<{ event_id: string }>("/api/nostr/deletion", {
      method: "POST",
      json: body,
    }),
  blossomStatus: () =>
    request<{ configured: boolean; url: string | null }>("/api/blossom/status"),
  blossomUpload: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    const res = await fetch("/api/blossom/upload", {
      method: "POST",
      body: form,
      credentials: "same-origin",
    });
    const text = await res.text();
    let parsed: unknown = undefined;
    if (text.length > 0) {
      try {
        parsed = JSON.parse(text);
      } catch {
        parsed = { error: text };
      }
    }
    if (!res.ok) {
      const msg =
        (parsed as { error?: string } | undefined)?.error ??
        `${res.status} ${res.statusText}`;
      if (res.status === 401 && unauthorizedHandler) unauthorizedHandler();
      throw new ApiError(res.status, msg);
    }
    return parsed as {
      url: string;
      sha256: string;
      size: number;
      type: string;
      uploaded: number;
    };
  },
  blossomDownload: async (sha256: string) => {
    const res = await fetch(`/api/blossom/${encodeURIComponent(sha256)}`, {
      credentials: "same-origin",
    });
    if (!res.ok) {
      const text = await res.text();
      let msg = `${res.status} ${res.statusText}`;
      try {
        const parsed = JSON.parse(text) as { error?: string };
        if (parsed.error) msg = parsed.error;
      } catch {
        if (text) msg = text;
      }
      if (res.status === 401 && unauthorizedHandler) unauthorizedHandler();
      throw new ApiError(res.status, msg);
    }
    return res.blob();
  },
};
