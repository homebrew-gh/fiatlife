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
  SALARY_D_TAG,
  calculateAnnual,
  calculateDepositAllocations,
  calculatePaycheck,
  defaultSalaryConfig,
  effectiveRateAt,
  applyInferredPayRatesForAllLogYears,
  generatePaycheckLogsForMissingDates,
  mergeSalaryConfigPreserveLogs,
  normalizeSalaryConfig,
  resolveSalaryConfigFromAppDataRecords,
  salaryFingerprint,
  type AnnualProjection,
  type DirectDeposit,
  type DepositAllocation,
  type EffectiveRate,
  type PaycheckCalculation,
  type PaycheckLogEntry,
  type SalaryConfig,
} from "./salary";

/** Local, non-persisted pay-rate override used by the Model calculator. */
export type WhatIfPayRate = {
  hourlyRate?: number;
  annualSalary?: number;
};

type SalaryDataContextValue = {
  config: SalaryConfig;
  calculation: PaycheckCalculation;
  annualOvertimeHours: number;
  annualProjection: AnnualProjection;
  annualBaseline: AnnualProjection;
  /** Most current pay rate inferred from saved config + logged paychecks. */
  currentEffectiveRate: EffectiveRate;
  /** Local pay-rate override for the Model calculator (null = use logged rate). */
  whatIfPayRate: WhatIfPayRate | null;
  setWhatIfPayRate: (patch: WhatIfPayRate) => void;
  resetWhatIfPayRate: () => void;
  /** Model calculations honoring the what-if pay-rate override. */
  modelCalculation: PaycheckCalculation;
  modelAnnualProjection: AnnualProjection;
  modelAnnualBaseline: AnnualProjection;
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  setConfig: (updater: (c: SalaryConfig) => SalaryConfig) => void;
  setAnnualOvertimeHours: (hours: number) => void;
  whatIfDirectDeposits: DirectDeposit[] | null;
  whatIfDepositsCustomized: boolean;
  whatIfDepositAllocations: DepositAllocation[];
  setWhatIfDirectDeposits: (deposits: DirectDeposit[]) => void;
  resetWhatIfDirectDeposits: () => void;
  addPaycheckLog: (entry: PaycheckLogEntry) => Promise<void>;
  updatePaycheckLog: (entry: PaycheckLogEntry) => Promise<void>;
  removePaycheckLog: (id: string) => Promise<void>;
  generateMissingPaycheckLogs: (year: number) => Promise<number>;
};

const SalaryDataContext = createContext<SalaryDataContextValue | null>(null);

const AUTO_SAVE_MS = 600;

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
  const [whatIfDirectDeposits, setWhatIfDirectDepositsState] = useState<
    DirectDeposit[] | null
  >(null);
  const [whatIfPayRate, setWhatIfPayRateState] = useState<WhatIfPayRate | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [savedFingerprint, setSavedFingerprint] = useState("");
  const configRef = useRef(config);
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { notify, refresh } = useOptionalSyncStatus();

  const cancelPendingPersist = useCallback(() => {
    if (persistTimerRef.current) {
      clearTimeout(persistTimerRef.current);
      persistTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    configRef.current = config;
  }, [config]);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const parsed = resolveSalaryConfigFromAppDataRecords(records);
      if (parsed) {
        const withInferred = applyInferredPayRatesForAllLogYears(parsed);
        setConfigState(withInferred);
        setSavedFingerprint(salaryFingerprint(withInferred));
      } else {
        const salaryRecord = records.find((r) => r.d_tag === SALARY_D_TAG);
        if (salaryRecord && !salaryRecord.plaintext) {
          setError(
            salaryRecord.decrypt_error ??
              "Could not decrypt paycheck data from the relay.",
          );
        }
        const fresh = defaultSalaryConfig();
        setConfigState(fresh);
        setSavedFingerprint(salaryFingerprint(fresh));
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
    setConfigState((prev) => normalizeSalaryConfig(updater(prev)));
  }, []);

  const setAnnualOvertimeHours = useCallback((hours: number) => {
    setAnnualOvertimeHoursState(Math.max(0, hours));
  }, []);

  const setWhatIfDirectDeposits = useCallback((deposits: DirectDeposit[]) => {
    setWhatIfDirectDepositsState(deposits);
  }, []);

  const resetWhatIfDirectDeposits = useCallback(() => {
    setWhatIfDirectDepositsState(null);
  }, []);

  const setWhatIfPayRate = useCallback((patch: WhatIfPayRate) => {
    setWhatIfPayRateState((prev) => ({ ...prev, ...patch }));
  }, []);

  const resetWhatIfPayRate = useCallback(() => {
    setWhatIfPayRateState(null);
  }, []);

  const persist = useCallback(async (next: SalaryConfig) => {
    cancelPendingPersist();
    const fingerprint = salaryFingerprint(next);
    if (fingerprint === savedFingerprint) return;

    setSaving(true);
    setError(null);
    try {
      const withId = next.id
        ? next
        : { ...next, id: crypto.randomUUID() };
      const records = await api.listAppData();
      const remote = resolveSalaryConfigFromAppDataRecords(records);
      const merged = mergeSalaryConfigPreserveLogs(withId, remote);
      const updatedAt = Date.now();
      const saved = { ...merged, updatedAt };
      const published = JSON.stringify(saved);
      setConfigState(saved);
      setSavedFingerprint(salaryFingerprint(saved));
      await api.publishAppData({
        d_tag: SALARY_D_TAG,
        plaintext: published,
      });
      refresh({ afterPublish: true });
    } catch (e) {
      const msg =
        e instanceof ApiError ? e.message : "Could not save paycheck data.";
      setError(msg);
      notify(msg, "error");
      throw e;
    } finally {
      setSaving(false);
    }
  }, [cancelPendingPersist, notify, refresh, savedFingerprint]);

  // Do not publish to the relay until the first fetch from relay/server completes.
  useEffect(() => {
    if (loading) return;
    if (salaryFingerprint(config) === savedFingerprint) return;

    cancelPendingPersist();
    persistTimerRef.current = setTimeout(() => {
      persistTimerRef.current = null;
      void persist(config);
    }, AUTO_SAVE_MS);

    return cancelPendingPersist;
  }, [config, savedFingerprint, loading, persist, cancelPendingPersist]);

  const mutatePaycheckLog = useCallback(
    async (updater: (log: PaycheckLogEntry[]) => PaycheckLogEntry[]) => {
      const withLog = normalizeSalaryConfig({
        ...configRef.current,
        paycheckLog: updater(configRef.current.paycheckLog ?? []),
      });
      const next = applyInferredPayRatesForAllLogYears(withLog);
      await persist(next);
    },
    [persist],
  );

  const addPaycheckLog = useCallback(
    async (entry: PaycheckLogEntry) => {
      await mutatePaycheckLog((log) => [...log, entry]);
    },
    [mutatePaycheckLog],
  );

  const updatePaycheckLog = useCallback(
    async (entry: PaycheckLogEntry) => {
      await mutatePaycheckLog((log) =>
        log.map((e) => (e.id === entry.id ? entry : e)),
      );
    },
    [mutatePaycheckLog],
  );

  const removePaycheckLog = useCallback(
    async (id: string) => {
      await mutatePaycheckLog((log) => log.filter((e) => e.id !== id));
    },
    [mutatePaycheckLog],
  );

  const generateMissingPaycheckLogs = useCallback(
    async (year: number) => {
      let added = 0;
      await mutatePaycheckLog((log) => {
        const config = { ...configRef.current, paycheckLog: log };
        const entries = generatePaycheckLogsForMissingDates(config, year);
        added = entries.length;
        return entries.length > 0 ? [...log, ...entries] : log;
      });
      return added;
    },
    [mutatePaycheckLog],
  );

  const derived = useMemo(
    () => recalc(config, annualOvertimeHours),
    [config, annualOvertimeHours],
  );

  const currentEffectiveRate = useMemo(
    () => effectiveRateAt(config, Date.now()),
    [config],
  );

  // Apply the local pay-rate override (if any) on top of the saved config.
  // Clearing payRateHistory makes the overridden base rate take effect for the
  // Model calculations without disturbing logged paychecks or inferred raises.
  const modelConfig = useMemo<SalaryConfig>(() => {
    if (!whatIfPayRate) return config;
    return {
      ...config,
      hourlyRate: whatIfPayRate.hourlyRate ?? config.hourlyRate,
      annualSalary: whatIfPayRate.annualSalary ?? config.annualSalary,
      payRateHistory: [],
    };
  }, [config, whatIfPayRate]);

  const modelDerived = useMemo(
    () =>
      whatIfPayRate
        ? recalc(modelConfig, annualOvertimeHours)
        : derived,
    [whatIfPayRate, modelConfig, annualOvertimeHours, derived],
  );

  const effectiveWhatIfDeposits =
    whatIfDirectDeposits ?? config.directDeposits;
  const whatIfDepositAllocations = useMemo(
    () =>
      calculateDepositAllocations(
        effectiveWhatIfDeposits,
        modelDerived.calculation.netPay,
      ),
    [effectiveWhatIfDeposits, modelDerived.calculation.netPay],
  );

  const value = useMemo(
    () => ({
      config,
      annualOvertimeHours,
      currentEffectiveRate,
      whatIfPayRate,
      setWhatIfPayRate,
      resetWhatIfPayRate,
      modelCalculation: modelDerived.calculation,
      modelAnnualProjection: modelDerived.annualProjection,
      modelAnnualBaseline: modelDerived.annualBaseline,
      whatIfDirectDeposits,
      whatIfDepositsCustomized: whatIfDirectDeposits !== null,
      whatIfDepositAllocations,
      loading,
      error,
      saving,
      reload,
      setConfig,
      setAnnualOvertimeHours,
      setWhatIfDirectDeposits,
      resetWhatIfDirectDeposits,
      addPaycheckLog,
      updatePaycheckLog,
      removePaycheckLog,
      generateMissingPaycheckLogs,
      ...derived,
    }),
    [
      config,
      annualOvertimeHours,
      currentEffectiveRate,
      whatIfPayRate,
      setWhatIfPayRate,
      resetWhatIfPayRate,
      modelDerived,
      whatIfDirectDeposits,
      whatIfDepositAllocations,
      loading,
      error,
      saving,
      reload,
      setConfig,
      setAnnualOvertimeHours,
      setWhatIfDirectDeposits,
      resetWhatIfDirectDeposits,
      addPaycheckLog,
      updatePaycheckLog,
      removePaycheckLog,
      generateMissingPaycheckLogs,
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
