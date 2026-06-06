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
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: goalDTag(goal.id),
          plaintext: serializeGoal(goal),
        });
        await reload();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Save failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [reload],
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
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: goalDTag(goal.id),
          plaintext: JSON.stringify({ deleted: true }),
        });
        await reload();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Delete failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [reload],
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
