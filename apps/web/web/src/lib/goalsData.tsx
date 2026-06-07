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
  GOAL_D_TAG_PREFIX,
  defaultGoal,
  goalDTag,
  newGoalId,
  parseGoalRecord,
  serializeGoal,
  type FinancialGoal,
} from "./goal";

type GoalsDataContextValue = {
  goals: FinancialGoal[];
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  saveGoal: (goal: FinancialGoal) => Promise<void>;
  addGoal: (
    input: Omit<FinancialGoal, "id" | "createdAt" | "updatedAt">,
  ) => Promise<void>;
  updateProgress: (goalId: string, currentAmount: number) => Promise<void>;
  deleteGoal: (goal: FinancialGoal) => Promise<void>;
};

const GoalsDataContext = createContext<GoalsDataContextValue | null>(null);

function isGoalDTag(dTag: string): boolean {
  return dTag.startsWith(GOAL_D_TAG_PREFIX);
}

export function GoalsDataProvider({ children }: { children: ReactNode }) {
  const [goals, setGoals] = useState<FinancialGoal[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const { notify, refresh } = useOptionalSyncStatus();

  const goalsRef = useRef(goals);
  useEffect(() => {
    goalsRef.current = goals;
  }, [goals]);

  const sortGoals = useCallback(
    (list: FinancialGoal[]) =>
      [...list].sort((a, b) =>
        a.name.localeCompare(b.name, undefined, { sensitivity: "base" }),
      ),
    [],
  );

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const parsed: FinancialGoal[] = [];
      for (const record of records) {
        const dTag = record.d_tag?.trim() ?? "";
        if (!isGoalDTag(dTag) || !record.plaintext) continue;
        const goal = parseGoalRecord(dTag, record.plaintext);
        if (goal) parsed.push(goal);
      }
      parsed.sort((a, b) =>
        a.name.localeCompare(b.name, undefined, { sensitivity: "base" }),
      );
      setGoals(parsed);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load goals.");
      setGoals([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const publish = useCallback(
    async (goal: FinancialGoal) => {
      const prev = goalsRef.current.find((g) => g.id === goal.id);
      setGoals((list) =>
        sortGoals([...list.filter((g) => g.id !== goal.id), goal]),
      );
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: goalDTag(goal.id),
          plaintext: serializeGoal(goal),
        });
        refresh();
      } catch (e) {
        setGoals((list) => {
          const without = list.filter((g) => g.id !== goal.id);
          return prev ? sortGoals([...without, prev]) : without;
        });
        const msg = e instanceof ApiError ? e.message : "Save failed.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [notify, refresh, sortGoals],
  );

  const saveGoal = useCallback(
    async (goal: FinancialGoal) => {
      const now = Date.now();
      const withMeta = defaultGoal({
        ...goal,
        id: goal.id || newGoalId(),
        createdAt: goal.createdAt || now,
        updatedAt: now,
      });
      await publish(withMeta);
    },
    [publish],
  );

  const addGoal = useCallback(
    async (input: Omit<FinancialGoal, "id" | "createdAt" | "updatedAt">) => {
      const now = Date.now();
      await publish(
        defaultGoal({
          ...input,
          id: newGoalId(),
          createdAt: now,
          updatedAt: now,
        }),
      );
    },
    [publish],
  );

  const updateProgress = useCallback(
    async (goalId: string, currentAmount: number) => {
      const goal = goals.find((g) => g.id === goalId);
      if (!goal) return;
      await saveGoal({ ...goal, currentAmount });
    },
    [goals, saveGoal],
  );

  const deleteGoal = useCallback(
    async (goal: FinancialGoal) => {
      const prev = goalsRef.current.find((g) => g.id === goal.id);
      setGoals((list) => list.filter((g) => g.id !== goal.id));
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: goalDTag(goal.id),
          plaintext: JSON.stringify({ deleted: true }),
        });
        refresh();
      } catch (e) {
        setGoals((list) => {
          const without = list.filter((g) => g.id !== goal.id);
          return prev ? sortGoals([...without, prev]) : without;
        });
        const msg = e instanceof ApiError ? e.message : "Delete failed.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [notify, refresh, sortGoals],
  );

  const value = useMemo(
    () => ({
      goals,
      loading,
      error,
      saving,
      reload,
      saveGoal,
      addGoal,
      updateProgress,
      deleteGoal,
    }),
    [
      goals,
      loading,
      error,
      saving,
      reload,
      saveGoal,
      addGoal,
      updateProgress,
      deleteGoal,
    ],
  );

  return (
    <GoalsDataContext.Provider value={value}>{children}</GoalsDataContext.Provider>
  );
}

export function useGoalsData(): GoalsDataContextValue {
  const ctx = useContext(GoalsDataContext);
  if (!ctx) throw new Error("useGoalsData must be used inside <GoalsDataProvider>");
  return ctx;
}
