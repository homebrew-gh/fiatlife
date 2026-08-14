import { useMemo, useState } from "react";
import { GoalSheet } from "../../components/goals/GoalSheet";
import { UpdateProgressSheet } from "../../components/goals/UpdateProgressSheet";
import { formatUsd } from "../../lib/format";
import {
  GOAL_CATEGORY_LABELS,
  goalIsComplete,
  goalMonthsRemaining,
  goalProgressPercent,
  goalRemainingAmount,
  summarizeGoals,
  type FinancialGoal,
} from "../../lib/goal";
import { useGoalsData } from "../../lib/goalsData";
import {
  EmptyState,
  ErrorBanner,
  HeroCard,
  PageHeader,
} from "../../components/ui";

function goalColor(goal: FinancialGoal): string {
  if (/^#[0-9A-Fa-f]{6}$/.test(goal.color)) return goal.color;
  return "#4CAF50";
}

function GoalCard({
  goal,
  onUpdate,
  onEdit,
  onDelete,
  deleting,
}: {
  goal: FinancialGoal;
  onUpdate: () => void;
  onEdit: () => void;
  onDelete: () => void;
  deleting: boolean;
}) {
  const color = goalColor(goal);
  const progress = goalProgressPercent(goal);
  const months = goalMonthsRemaining(goal);
  const complete = goalIsComplete(goal);

  return (
    <article className="card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-semibold text-body truncate">{goal.name}</h3>
          <p className="text-sm text-muted">
            {GOAL_CATEGORY_LABELS[goal.category]}
          </p>
        </div>
        {complete ? (
          <span className="badge-success shrink-0">Complete</span>
        ) : null}
      </div>

      <div className="mt-3 flex items-baseline justify-between gap-2">
        <span className="font-mono text-lg font-semibold" style={{ color }}>
          {formatUsd(goal.currentAmount)}
        </span>
        <span className="text-sm text-muted font-mono">
          {formatUsd(goal.targetAmount)}
        </span>
      </div>

      <div className="mt-2 h-2 rounded-full bg-surfaceVariant overflow-hidden">
        <div
          className="h-full rounded-full transition-all"
          style={{ width: `${progress}%`, backgroundColor: color }}
        />
      </div>

      <div className="mt-1 flex justify-between text-xs text-muted">
        <span>{progress.toFixed(0)}% complete</span>
        <span>{formatUsd(goalRemainingAmount(goal))} remaining</span>
      </div>

      {goal.monthlyContribution > 0 ? (
        <p className="mt-2 text-xs text-muted">
          <span className="text-success font-medium">
            {formatUsd(goal.monthlyContribution)}/mo
          </span>
          {months != null ? (
            <span> · ~{months} months remaining</span>
          ) : null}
        </p>
      ) : null}

      {goal.notes ? (
        <p className="mt-2 text-sm text-muted line-clamp-2">{goal.notes}</p>
      ) : null}

      <div className="mt-3 flex justify-end gap-1">
        <button type="button" className="btn-ghost text-sm py-1.5" onClick={onUpdate}>
          Update
        </button>
        <button type="button" className="btn-ghost text-sm py-1.5" onClick={onEdit}>
          Edit
        </button>
        <button
          type="button"
          className="btn-ghost text-sm py-1.5 text-error"
          onClick={onDelete}
          disabled={deleting}
        >
          {deleting ? "Deleting…" : "Delete"}
        </button>
      </div>
    </article>
  );
}

export function GoalsTab() {
  const {
    goals,
    loading,
    error,
    saving,
    reload,
    addGoal,
    saveGoal,
    updateProgress,
    deleteGoal,
  } = useGoalsData();
  const [showSheet, setShowSheet] = useState(false);
  const [editingGoal, setEditingGoal] = useState<FinancialGoal | null>(null);
  const [updatingGoal, setUpdatingGoal] = useState<FinancialGoal | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const summary = useMemo(() => summarizeGoals(goals), [goals]);

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await reload();
    } finally {
      setRefreshing(false);
    }
  };

  const openAdd = () => {
    setEditingGoal(null);
    setShowSheet(true);
  };

  const openEdit = (goal: FinancialGoal) => {
    setEditingGoal(goal);
    setShowSheet(true);
  };

  const onSaveGoal = async (
    input: Omit<FinancialGoal, "id" | "createdAt" | "updatedAt">,
  ) => {
    if (editingGoal) {
      await saveGoal({ ...editingGoal, ...input });
    } else {
      await addGoal(input);
    }
    setShowSheet(false);
    setEditingGoal(null);
  };

  const onDeleteGoal = async (goal: FinancialGoal) => {
    if (!window.confirm(`Delete "${goal.name}"? This syncs to your relay.`)) {
      return;
    }
    setDeletingId(goal.id);
    try {
      await deleteGoal(goal);
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Goals"
        description="Savings targets synced with Android via your Nostr relay."
        refreshing={refreshing}
        onRefresh={() => void onRefresh()}
        refreshDisabled={loading}
        actions={
          <button
            type="button"
            className="btn-primary text-sm"
            onClick={openAdd}
            disabled={saving}
          >
            Add goal
          </button>
        }
      />

      {error ? <ErrorBanner message={error} /> : null}

      {loading ? (
        <p className="text-sm text-muted">Loading goals…</p>
      ) : (
        <>
          <HeroCard className="p-6" center>
            <h2 className="text-center text-sm font-medium opacity-80">
              Overall Progress
            </h2>
            <p className="text-center font-serif text-4xl font-bold mt-2">
              {summary.overallProgress.toFixed(0)}%
            </p>
            <div className="mt-3 h-2 rounded-full bg-surfaceVariant overflow-hidden">
              <div
                className="h-full rounded-full bg-success transition-all"
                style={{
                  width: `${Math.min(100, summary.overallProgress)}%`,
                }}
              />
            </div>
            <div className="mt-4 grid grid-cols-3 gap-2 text-center text-sm">
              <div>
                <p className="opacity-70">Saved</p>
                <p className="money font-semibold">
                  {formatUsd(summary.totalSaved)}
                </p>
              </div>
              <div>
                <p className="opacity-70">Target</p>
                <p className="money font-semibold">
                  {formatUsd(summary.totalTarget)}
                </p>
              </div>
              <div>
                <p className="opacity-70">Goals</p>
                <p className="font-semibold">{goals.length}</p>
              </div>
            </div>
          </HeroCard>

          {goals.length === 0 ? (
            <EmptyState
              title="No goals yet"
              description="Start tracking your financial goals."
              action={
                <button type="button" className="btn-primary" onClick={openAdd}>
                  Add your first goal
                </button>
              }
            />
          ) : (
            <div className="space-y-4">
              {goals.map((goal) => (
                <GoalCard
                  key={goal.id}
                  goal={goal}
                  onUpdate={() => setUpdatingGoal(goal)}
                  onEdit={() => openEdit(goal)}
                  onDelete={() => void onDeleteGoal(goal)}
                  deleting={deletingId === goal.id}
                />
              ))}
            </div>
          )}
        </>
      )}

      <GoalSheet
        open={showSheet}
        goal={editingGoal}
        onClose={() => {
          setShowSheet(false);
          setEditingGoal(null);
        }}
        onSave={onSaveGoal}
        saving={saving}
      />

      <UpdateProgressSheet
        open={updatingGoal != null}
        goal={updatingGoal}
        onClose={() => setUpdatingGoal(null)}
        onUpdate={updateProgress}
        saving={saving}
      />
    </div>
  );
}
