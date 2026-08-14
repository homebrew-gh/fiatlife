import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { api, type OutboxFailedItem } from "./api";

export type ToastKind = "error" | "success" | "info";

export type Toast = {
  id: number;
  message: string;
  kind: ToastKind;
};

export type RefreshOptions = {
  /** Set when a Nostr publish was just queued — toast when relay delivery completes. */
  afterPublish?: boolean;
};

type SyncStatusValue = {
  /** Show a transient toast. */
  notify: (message: string, kind?: ToastKind) => void;
  /** Background relay sends still in flight. */
  pending: number;
  /** Background relay sends that exhausted retries. */
  failed: number;
  failedItems: OutboxFailedItem[];
  toasts: Toast[];
  dismissToast: (id: number) => void;
  /** Re-attempt all failed background sends. */
  retry: () => Promise<void>;
  /** Discard all failed background sends (dismiss a stuck "not synced" badge). */
  clearFailed: () => Promise<void>;
  /** Poll the outbox now (call right after a mutation). */
  refresh: (opts?: RefreshOptions) => void;
};

const SyncStatusContext = createContext<SyncStatusValue | null>(null);

const POLL_INTERVAL_MS = 4000;
const TOAST_TTL_MS = 6000;

export function SyncStatusProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState(0);
  const [failed, setFailed] = useState(0);
  const [failedItems, setFailedItems] = useState<OutboxFailedItem[]>([]);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastSeq = useRef(0);
  const awaitingDeliveryRef = useRef(false);

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, kind: ToastKind = "info") => {
      const id = ++toastSeq.current;
      setToasts((prev) => [...prev, { id, message, kind }]);
      window.setTimeout(() => dismissToast(id), TOAST_TTL_MS);
    },
    [dismissToast],
  );

  const poll = useCallback(async () => {
    try {
      const status = await api.outboxStatus();
      setPending(status.pending);
      setFailed(status.failed);
      setFailedItems(status.failed_items ?? []);

      if (awaitingDeliveryRef.current && status.pending === 0) {
        awaitingDeliveryRef.current = false;
        if (status.failed === 0) {
          notify("Saved to relay", "success");
        } else {
          notify("Could not sync to relay — tap Retry below", "error");
        }
      }
    } catch {
      /* outbox status is best-effort; ignore transient errors */
    }
  }, [notify]);

  const refresh = useCallback(
    (opts?: RefreshOptions) => {
      if (opts?.afterPublish) {
        awaitingDeliveryRef.current = true;
      }
      void poll();
    },
    [poll],
  );

  const retry = useCallback(async () => {
    try {
      const hadFailed = failed > 0;
      const status = await api.outboxRetry();
      setPending(status.pending);
      setFailed(status.failed);
      setFailedItems(status.failed_items ?? []);
      if (hadFailed) {
        awaitingDeliveryRef.current = true;
      }
      notify("Retrying sync…", "info");
      void poll();
    } catch {
      notify("Could not retry sync.", "error");
    }
  }, [failed, notify, poll]);

  const clearFailed = useCallback(async () => {
    try {
      const status = await api.outboxClear();
      setPending(status.pending);
      setFailed(status.failed);
      setFailedItems(status.failed_items ?? []);
      awaitingDeliveryRef.current = false;
      notify("Dismissed unsynced changes.", "info");
    } catch {
      notify("Could not dismiss unsynced changes.", "error");
    }
  }, [notify]);

  useEffect(() => {
    void poll();
    const handle = window.setInterval(() => void poll(), POLL_INTERVAL_MS);
    return () => window.clearInterval(handle);
  }, [poll]);

  const value = useMemo(
    () => ({
      notify,
      pending,
      failed,
      failedItems,
      toasts,
      dismissToast,
      retry,
      clearFailed,
      refresh,
    }),
    [
      notify,
      pending,
      failed,
      failedItems,
      toasts,
      dismissToast,
      retry,
      clearFailed,
      refresh,
    ],
  );

  return (
    <SyncStatusContext.Provider value={value}>
      {children}
    </SyncStatusContext.Provider>
  );
}

export function useSyncStatus(): SyncStatusValue {
  const ctx = useContext(SyncStatusContext);
  if (!ctx) {
    throw new Error("useSyncStatus must be used within SyncStatusProvider");
  }
  return ctx;
}

/** Optional hook: safe to call outside the provider (returns no-op notify). */
export function useOptionalSyncStatus(): Pick<
  SyncStatusValue,
  "notify" | "refresh"
> {
  const ctx = useContext(SyncStatusContext);
  return useMemo(
    () => ({
      notify: ctx?.notify ?? (() => {}),
      refresh: ctx?.refresh ?? ((_opts?: RefreshOptions) => {}),
    }),
    [ctx],
  );
}
