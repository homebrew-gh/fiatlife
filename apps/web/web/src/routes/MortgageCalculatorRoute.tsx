import { Link } from "react-router-dom";
import { MortgageCalculator } from "../components/debt/MortgageCalculator";

export function MortgageCalculatorRoute() {
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
      <MortgageCalculator />
    </div>
  );
}
