import { Link } from "react-router-dom";
import { MortgageCalculator } from "../components/debt/MortgageCalculator";
import { useSalaryData } from "../lib/salaryData";
import { useDebtData } from "../lib/debtData";
import { effectiveMonthlyPayment } from "../lib/creditAccount";
import { computeConservativeMonthlyTakeHome } from "../lib/salary";

export function MortgageCalculatorRoute() {
  // Baseline (no projected overtime) for conservative lender qualifying income.
  const { annualBaseline, config } = useSalaryData();
  const { accounts } = useDebtData();
  const grossMonthlyIncome =
    annualBaseline.annualGrossPay > 0
      ? annualBaseline.annualGrossPay / 12
      : 0;
  const conservativeTakeHome = computeConservativeMonthlyTakeHome(config);
  const takeHomeMonthlyIncome = conservativeTakeHome.monthlyTakeHome;

  // Existing non-mortgage debt payments feed the back-end DTI. The mortgage
  // being modeled is the new housing payment, so existing mortgages are excluded.
  const trackedMonthlyDebts = accounts
    .filter((a) => a.type !== "MORTGAGE")
    .reduce((sum, a) => sum + effectiveMonthlyPayment(a), 0);

  return (
    <div className="space-y-5">
      <div className="flex items-start gap-3">
        <Link to="/app/debt" className="btn-ghost text-sm py-1.5 shrink-0">
          ← Debt
        </Link>
        <div>
          <h1 className="page-title">Mortgage Calculator</h1>
          <p className="text-sm text-muted mt-1">
            Compare down payments, interest rates, and loan terms before adding a
            mortgage to your accounts.
          </p>
        </div>
      </div>
      <MortgageCalculator
        suggestedGrossMonthlyIncome={grossMonthlyIncome}
        suggestedTakeHomeMonthlyIncome={takeHomeMonthlyIncome}
        conservativeTakeHome={conservativeTakeHome}
        trackedMonthlyDebts={trackedMonthlyDebts}
      />
    </div>
  );
}
