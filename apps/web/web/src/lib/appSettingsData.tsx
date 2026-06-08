import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ApiError, api } from "./api";
import { useOptionalSyncStatus } from "./syncStatus";
import {
  APP_SETTINGS_D_TAG,
  defaultAppSettings,
  parseAppSettings,
  serializeAppSettings,
  type AppSettings,
} from "./appSettings";

type AppSettingsContextValue = {
  settings: AppSettings;
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  saveSettings: (partial: Partial<AppSettings>) => Promise<AppSettings>;
};

const AppSettingsContext = createContext<AppSettingsContextValue | null>(null);

export function AppSettingsDataProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<AppSettings>(defaultAppSettings());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const { notify, refresh } = useOptionalSyncStatus();

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const record = records.find((r) => r.d_tag === APP_SETTINGS_D_TAG);
      if (record?.plaintext) {
        const parsed = parseAppSettings(record.plaintext);
        if (parsed) setSettings(parsed);
        else setSettings(defaultAppSettings());
      } else {
        setSettings(defaultAppSettings());
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load settings.");
      setSettings(defaultAppSettings());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const saveSettings = useCallback(
    async (partial: Partial<AppSettings>) => {
      const prev = settings;
      const next = defaultAppSettings({
        ...settings,
        ...partial,
        updatedAt: Date.now(),
      });
      setSettings(next);
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: APP_SETTINGS_D_TAG,
          plaintext: serializeAppSettings(next),
        });
        refresh({ afterPublish: true });
        return next;
      } catch (e) {
        setSettings(prev);
        const msg = e instanceof ApiError ? e.message : "Save failed.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [settings, notify, refresh],
  );

  const value = useMemo(
    () => ({
      settings,
      loading,
      error,
      saving,
      reload,
      saveSettings,
    }),
    [settings, loading, error, saving, reload, saveSettings],
  );

  return (
    <AppSettingsContext.Provider value={value}>
      {children}
    </AppSettingsContext.Provider>
  );
}

export function useAppSettingsData(): AppSettingsContextValue {
  const ctx = useContext(AppSettingsContext);
  if (!ctx) {
    throw new Error(
      "useAppSettingsData must be used inside <AppSettingsDataProvider>",
    );
  }
  return ctx;
}
