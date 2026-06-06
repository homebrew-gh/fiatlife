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
      setSaving(true);
      setError(null);
      try {
        const next = defaultAppSettings({
          ...settings,
          ...partial,
          updatedAt: Date.now(),
        });
        await api.publishAppData({
          d_tag: APP_SETTINGS_D_TAG,
          plaintext: serializeAppSettings(next),
        });
        setSettings(next);
        await reload();
        return next;
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Save failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [settings, reload],
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
