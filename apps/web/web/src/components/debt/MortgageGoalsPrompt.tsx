import { useState } from "react";
import type { CreditAccount } from "../../lib/creditAccount";
import { housingPitiMonthly } from "../../lib/creditAccount";
import {
  suggestedEmergencyFundTarget,
  suggestedMaintenanceAnnual,
} from "../../lib/mortgage";
import { formatUsd } from "../../lib/format";
import {
  defaultGoal,
  GOAL_CATEGORY_COLORS,
  type FinancialGoal,
} from "../../lib/goal";

export function MortgageGoalsPrompt({
  account,
  existingGoals,
  onApply,
  onSkip,
  saving,
}: {
  account: CreditAccount;
  existingGoals: FinancialGoal[];
  onApply: (goals: FinancialGoal[]) => Promise<void>;
  onSkip: () => void;
  saving: boolean;
}) {
  const housing = housingPitiMonthly(account);
  const [months, setMonths] = useState<3 | 6>(3);
  const [wantEmergency, setWantEmergency] = useState(housing > 0);
  const [wantMaintenance, setWantMaintenance] = useState(
    (account.homePrice ?? 0) > 0,
  );

  const emergencyTarget = suggestedEmergencyFundTarget(housing, months);
  const maintenanceAnnual = suggestedMaintenanceAnnual(account.homePrice ?? 0);
  const existingEmergency = existingGoals.find(
    (g) => g.category === "EMERGENCY_FUND",
  );
  const existingMaintenance = existingGoals.find(
    (g) =>
      g.category === "HOME_IMPROVEMENT" &&
      g.name.toLowerCase().includes("maintenance"),
  );

  if (housing <= 0 && (account.homePrice ?? 0) <= 0) {
    return (
      <div
        className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
      >
        <div className="card w-full max-w-md p-5 space-y-4">
          <h2 className="page-title text-xl">Mortgage saved</h2>
          <p className="text-sm text-muted">
            Add a home price and housing costs on the account if you want
            emergency-fund and maintenance suggestions later.
          </p>
          <button type="button" className="btn-primary w-full" onClick={onSkip}>
            Continue
          </button>
        </div>
      </div>
    );
  }

  const onSubmit = async () => {
    const now = Date.now();
    const next: FinancialGoal[] = [];
    if (wantEmergency && emergencyTarget > 0) {
      if (existingEmergency) {
        next.push({
          ...existingEmergency,
          targetAmount: Math.max(existingEmergency.targetAmount, emergencyTarget),
          updatedAt: now,
        });
      } else {
        next.push(
          defaultGoal({
            name: "Emergency fund",
            category: "EMERGENCY_FUND",
            targetAmount: emergencyTarget,
            color: GOAL_CATEGORY_COLORS.EMERGENCY_FUND,
          }),
        );
      }
    }
    if (wantMaintenance && maintenanceAnnual > 0) {
      if (existingMaintenance) {
        next.push({
          ...existingMaintenance,
          targetAmount: Math.max(existingMaintenance.targetAmount, maintenanceAnnual),
          monthlyContribution: Math.max(
            existingMaintenance.monthlyContribution,
            maintenanceAnnual / 12,
          ),
          updatedAt: now,
        });
      } else {
        next.push(
          defaultGoal({
            name: "Home maintenance",
            category: "HOME_IMPROVEMENT",
            targetAmount: maintenanceAnnual,
            monthlyContribution: maintenanceAnnual / 12,
            color: GOAL_CATEGORY_COLORS.HOME_IMPROVEMENT,
            notes: "About 1% of home price per year.",
          }),
        );
      }
    }
    if (next.length === 0) {
      onSkip();
      return;
    }
    await onApply(next);
  };

  return (
    <div
      className="modal-overlay fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="mortgage-goals-title"
    >
      <div className="card w-full max-w-md p-5 space-y-4">
        <h2 id="mortgage-goals-title" className="page-title text-xl">
          Set aside for the house?
        </h2>
        <p className="text-sm text-muted">
          Optional. These use your new housing payment and home price. You can
          skip or edit them later in Goals.
        </p>
        {housing > 0 ? (
          <fieldset className="space-y-2">
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-1"
                checked={wantEmergency}
                onChange={(e) => setWantEmergency(e.target.checked)}
              />
              <span>
                Emergency fund at {months} months of housing (
                {formatUsd(emergencyTarget)})
              </span>
            </label>
            {wantEmergency ? (
              <div className="pl-6 flex gap-2">
                <button
                  type="button"
                  className={months === 3 ? "btn-primary text-xs" : "btn-ghost text-xs"}
                  onClick={() => setMonths(3)}
                >
                  3 months
                </button>
                <button
                  type="button"
                  className={months === 6 ? "btn-primary text-xs" : "btn-ghost text-xs"}
                  onClick={() => setMonths(6)}
                >
                  6 months
                </button>
              </div>
            ) : null}
          </fieldset>
        ) : null}
        {(account.homePrice ?? 0) > 0 ? (
          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1"
              checked={wantMaintenance}
              onChange={(e) => setWantMaintenance(e.target.checked)}
            />
            <span>
              Home maintenance reserve {formatUsd(maintenanceAnnual)}/yr (
              {formatUsd(maintenanceAnnual / 12)}/mo)
            </span>
          </label>
        ) : null}
        <div className="flex gap-2 pt-2">
          <button
            type="button"
            className="btn-ghost flex-1"
            onClick={onSkip}
            disabled={saving}
          >
            Skip
          </button>
          <button
            type="button"
            className="btn-primary flex-1"
            onClick={() => void onSubmit()}
            disabled={saving}
          >
            {saving ? "Saving…" : "Save goals"}
          </button>
        </div>
      </div>
    </div>
  );
}
