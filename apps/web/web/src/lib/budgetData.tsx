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
import { ApiError, api } from "./api";
import { useOptionalSyncStatus } from "./syncStatus";
import {
  BUDGET_D_TAG,
  budgetFingerprint,
  defaultBudgetConfig,
  normalizeBudgetConfig,
  parseBudgetRecord,
  rollBudgetPeriod,
  type BudgetConfig,
} from "./budget";

type BudgetDataContextValue = {
  config: BudgetConfig;
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  setConfig: (updater: (c: BudgetConfig) => BudgetConfig) => void;
};

const BudgetDataContext = createContext<BudgetDataContextValue | null>(null);

const AUTO_SAVE_MS = 600;

export function BudgetDataProvider({ children }: { children: ReactNode }) {
  const [config, setConfigState] = useState<BudgetConfig>(defaultBudgetConfig());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [savedFingerprint, setSavedFingerprint] = useState("");
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { notify, refresh } = useOptionalSyncStatus();

  const cancelPendingPersist = useCallback(() => {
    if (persistTimerRef.current) {
      clearTimeout(persistTimerRef.current);
      persistTimerRef.current = null;
    }
  }, []);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const record = records.find((r) => r.d_tag === BUDGET_D_TAG);
      const parsed = record?.plaintext ? parseBudgetRecord(record.plaintext) : null;
      if (parsed) {
        // Carry targets into the current month; reset manual spend if stale.
        const rolled = rollBudgetPeriod(parsed);
        setConfigState(rolled);
        // Fingerprint of the *stored* config, so a month rollover persists once.
        setSavedFingerprint(budgetFingerprint(parsed));
      } else {
        const fresh = defaultBudgetConfig();
        setConfigState(fresh);
        setSavedFingerprint(budgetFingerprint(fresh));
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load budget data.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const setConfig = useCallback((updater: (c: BudgetConfig) => BudgetConfig) => {
    setConfigState((prev) => normalizeBudgetConfig(updater(prev)));
  }, []);

  const persist = useCallback(
    async (next: BudgetConfig) => {
      cancelPendingPersist();
      const fingerprint = budgetFingerprint(next);
      if (fingerprint === savedFingerprint) return;

      setSaving(true);
      setError(null);
      try {
        const withId = next.id ? next : { ...next, id: crypto.randomUUID() };
        const saved = { ...withId, updatedAt: Date.now() };
        setConfigState(saved);
        setSavedFingerprint(budgetFingerprint(saved));
        await api.publishAppData({
          d_tag: BUDGET_D_TAG,
          plaintext: JSON.stringify(normalizeBudgetConfig(saved)),
        });
        refresh({ afterPublish: true });
      } catch (e) {
        const msg =
          e instanceof ApiError ? e.message : "Could not save budget data.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [cancelPendingPersist, notify, refresh, savedFingerprint],
  );

  // Do not publish to the relay until the first fetch completes (avoids a
  // fresh device clobbering existing budget data with an empty config).
  useEffect(() => {
    if (loading) return;
    if (budgetFingerprint(config) === savedFingerprint) return;

    cancelPendingPersist();
    persistTimerRef.current = setTimeout(() => {
      persistTimerRef.current = null;
      void persist(config);
    }, AUTO_SAVE_MS);

    return cancelPendingPersist;
  }, [config, savedFingerprint, loading, persist, cancelPendingPersist]);

  const value = useMemo(
    () => ({ config, loading, error, saving, reload, setConfig }),
    [config, loading, error, saving, reload, setConfig],
  );

  return (
    <BudgetDataContext.Provider value={value}>
      {children}
    </BudgetDataContext.Provider>
  );
}

export function useBudgetData(): BudgetDataContextValue {
  const ctx = useContext(BudgetDataContext);
  if (!ctx) throw new Error("useBudgetData must be used inside BudgetDataProvider");
  return ctx;
}
