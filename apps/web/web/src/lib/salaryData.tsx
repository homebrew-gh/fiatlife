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
  SALARY_D_TAG,
  calculateAnnual,
  calculatePaycheck,
  defaultSalaryConfig,
  normalizeSalaryConfig,
  parseSalaryRecord,
  serializeSalary,
  type AnnualProjection,
  type PaycheckCalculation,
  type PaycheckLogEntry,
  type SalaryConfig,
} from "./salary";

type SalaryDataContextValue = {
  config: SalaryConfig;
  calculation: PaycheckCalculation;
  annualOvertimeHours: number;
  annualProjection: AnnualProjection;
  annualBaseline: AnnualProjection;
  loading: boolean;
  error: string | null;
  saving: boolean;
  dirty: boolean;
  reload: () => Promise<void>;
  setConfig: (updater: (c: SalaryConfig) => SalaryConfig) => void;
  setAnnualOvertimeHours: (hours: number) => void;
  save: () => Promise<void>;
  addPaycheckLog: (entry: PaycheckLogEntry) => void;
  updatePaycheckLog: (entry: PaycheckLogEntry) => void;
  removePaycheckLog: (id: string) => void;
};

const SalaryDataContext = createContext<SalaryDataContextValue | null>(null);

function recalc(
  config: SalaryConfig,
  annualOvertimeHours: number,
): Pick<
  SalaryDataContextValue,
  "calculation" | "annualProjection" | "annualBaseline"
> {
  const calculation = calculatePaycheck(config);
  return {
    calculation,
    annualProjection: calculateAnnual(config, annualOvertimeHours),
    annualBaseline: calculateAnnual(config, 0),
  };
}

export function SalaryDataProvider({ children }: { children: ReactNode }) {
  const [config, setConfigState] = useState<SalaryConfig>(defaultSalaryConfig());
  const [annualOvertimeHours, setAnnualOvertimeHoursState] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [savedSnapshot, setSavedSnapshot] = useState("");
  const { notify, refresh } = useOptionalSyncStatus();

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const record = records.find((r) => r.d_tag === SALARY_D_TAG);
      if (record?.plaintext) {
        const parsed = parseSalaryRecord(record.plaintext);
        if (parsed) {
          setConfigState(parsed);
          setSavedSnapshot(serializeSalary(parsed));
          setDirty(false);
        }
      } else {
        const fresh = defaultSalaryConfig();
        setConfigState(fresh);
        setSavedSnapshot(serializeSalary(fresh));
        setDirty(false);
      }
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load paycheck data.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const setConfig = useCallback((updater: (c: SalaryConfig) => SalaryConfig) => {
    setConfigState((prev) => {
      const next = normalizeSalaryConfig(updater(prev));
      setDirty(serializeSalary(next) !== savedSnapshot);
      return next;
    });
  }, [savedSnapshot]);

  const setAnnualOvertimeHours = useCallback((hours: number) => {
    setAnnualOvertimeHoursState(Math.max(0, hours));
  }, []);

  const persist = useCallback(async (next: SalaryConfig) => {
    setSaving(true);
    setError(null);
    try {
      const withId = next.id
        ? next
        : { ...next, id: crypto.randomUUID() };
      const payload = serializeSalary(withId);
      setConfigState(withId);
      setSavedSnapshot(payload);
      setDirty(false);
      await api.publishAppData({
        d_tag: SALARY_D_TAG,
        plaintext: payload,
      });
      refresh();
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not save paycheck data.";
      setError(msg);
      notify(msg, "error");
      throw e;
    } finally {
      setSaving(false);
    }
  }, [notify, refresh]);

  const save = useCallback(async () => {
    await persist(config);
  }, [config, persist]);

  const addPaycheckLog = useCallback((entry: PaycheckLogEntry) => {
    setConfig((c) => ({
      ...c,
      paycheckLog: [...(c.paycheckLog ?? []), entry],
    }));
  }, [setConfig]);

  const updatePaycheckLog = useCallback((entry: PaycheckLogEntry) => {
    setConfig((c) => ({
      ...c,
      paycheckLog: (c.paycheckLog ?? []).map((e) =>
        e.id === entry.id ? entry : e,
      ),
    }));
  }, [setConfig]);

  const removePaycheckLog = useCallback((id: string) => {
    setConfig((c) => ({
      ...c,
      paycheckLog: (c.paycheckLog ?? []).filter((e) => e.id !== id),
    }));
  }, [setConfig]);

  const derived = useMemo(
    () => recalc(config, annualOvertimeHours),
    [config, annualOvertimeHours],
  );

  const value = useMemo(
    () => ({
      config,
      annualOvertimeHours,
      loading,
      error,
      saving,
      dirty,
      reload,
      setConfig,
      setAnnualOvertimeHours,
      save,
      addPaycheckLog,
      updatePaycheckLog,
      removePaycheckLog,
      ...derived,
    }),
    [
      config,
      annualOvertimeHours,
      loading,
      error,
      saving,
      dirty,
      reload,
      setConfig,
      setAnnualOvertimeHours,
      save,
      addPaycheckLog,
      updatePaycheckLog,
      removePaycheckLog,
      derived,
    ],
  );

  return (
    <SalaryDataContext.Provider value={value}>
      {children}
    </SalaryDataContext.Provider>
  );
}

export function useSalaryData(): SalaryDataContextValue {
  const ctx = useContext(SalaryDataContext);
  if (!ctx) throw new Error("useSalaryData must be used inside SalaryDataProvider");
  return ctx;
}
