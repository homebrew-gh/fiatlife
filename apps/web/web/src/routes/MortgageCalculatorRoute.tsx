import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { MortgageCalculator } from "../components/debt/MortgageCalculator";
import { MortgageGoalsPrompt } from "../components/debt/MortgageGoalsPrompt";
import { useSalaryData } from "../lib/salaryData";
import { useDebtData } from "../lib/debtData";
import { useGoalsData } from "../lib/goalsData";
import { effectiveMonthlyPayment } from "../lib/creditAccount";
import { computeConservativeMonthlyTakeHome } from "../lib/salary";
import {
  creditAccountFromScenario,
  type MortgageScenarioResult,
} from "../lib/mortgage";
import type { CreditAccount } from "../lib/creditAccount";

export function MortgageCalculatorRoute() {
  const navigate = useNavigate();
  const { annualBaseline, config } = useSalaryData();
  const { accounts, addAccount, saving } = useDebtData();
  const { goals, saveGoal, saving: goalsSaving } = useGoalsData();
  const [goalsPromptAccount, setGoalsPromptAccount] =
    useState<CreditAccount | null>(null);

  const grossMonthlyIncome =
    annualBaseline.annualGrossPay > 0
      ? annualBaseline.annualGrossPay / 12
      : 0;
  const conservativeTakeHome = computeConservativeMonthlyTakeHome(config);
  const takeHomeMonthlyIncome = conservativeTakeHome.monthlyTakeHome;

  const trackedMonthlyDebts = accounts
    .filter((a) => a.type !== "MORTGAGE")
    .reduce((sum, a) => sum + effectiveMonthlyPayment(a), 0);

  const onSaveAsAccount = async (result: MortgageScenarioResult) => {
    const saved = await addAccount(creditAccountFromScenario(result));
    setGoalsPromptAccount(saved);
  };

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-3">
        <Link to="/app/debt" className="btn-ghost text-sm py-1.5 shrink-0">
          ← Debt
        </Link>
        <div>
          <h1 className="page-title">Mortgage Calculator</h1>
          <p className="text-sm text-muted mt-1">
            Compare down payments, interest rates, and loan terms, then save a
            scenario as a mortgage account.
          </p>
        </div>
      </div>
      <MortgageCalculator
        suggestedGrossMonthlyIncome={grossMonthlyIncome}
        suggestedTakeHomeMonthlyIncome={takeHomeMonthlyIncome}
        conservativeTakeHome={conservativeTakeHome}
        trackedMonthlyDebts={trackedMonthlyDebts}
        onSaveAsAccount={(result) => void onSaveAsAccount(result)}
        savingAccount={saving}
      />
      {goalsPromptAccount ? (
        <MortgageGoalsPrompt
          account={goalsPromptAccount}
          existingGoals={goals}
          saving={goalsSaving}
          onSkip={() => {
            const id = goalsPromptAccount.id;
            setGoalsPromptAccount(null);
            navigate(`/app/debt/${id}`);
          }}
          onApply={async (nextGoals) => {
            const id = goalsPromptAccount.id;
            for (const goal of nextGoals) {
              await saveGoal(goal);
            }
            setGoalsPromptAccount(null);
            navigate(`/app/debt/${id}`);
          }}
        />
      ) : null}
    </div>
  );
}
