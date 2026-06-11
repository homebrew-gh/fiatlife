import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./lib/auth";
import { hasRelayConfigured } from "./lib/relayUrl";
import { SetupRoute } from "./routes/SetupRoute";
import { UnlockRoute } from "./routes/UnlockRoute";
import { RelaySetupRoute } from "./routes/RelaySetupRoute";
import { AppShell } from "./routes/AppShell";
import { BillDetailRoute } from "./routes/BillDetailRoute";
import { CompanyHistoryRoute } from "./routes/CompanyHistoryRoute";
import { CompanyHistoryDetailRoute } from "./routes/CompanyHistoryDetailRoute";
import { BillsTab } from "./routes/tabs/BillsTab";
import { DashboardTab } from "./routes/tabs/DashboardTab";
import { PaycheckTab } from "./routes/tabs/PaycheckTab";
import { DebtDetailRoute } from "./routes/DebtDetailRoute";
import { DebtPlannerRoute } from "./routes/DebtPlannerRoute";
import { MortgageCalculatorRoute } from "./routes/MortgageCalculatorRoute";
import { DebtTab } from "./routes/tabs/DebtTab";
import { GoalsTab } from "./routes/tabs/GoalsTab";
import { BudgetTab } from "./routes/tabs/BudgetTab";
import { SettingsTab } from "./routes/tabs/SettingsTab";

export function App() {
  return (
    <AuthProvider>
      <Gate />
    </AuthProvider>
  );
}

function Gate() {
  const { status, loading } = useAuth();

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center text-muted">
        <span className="font-serif text-heading text-lg">FiatLife</span>
        <span className="ml-2">Loading…</span>
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/setup" element={<SetupRoute />} />
      <Route path="/unlock" element={<UnlockRoute />} />
      <Route path="/relay-setup" element={<RelaySetupRoute />} />
      <Route path="/app" element={<AppShell />}>
        <Route index element={<DashboardTab />} />
        <Route path="bills" element={<BillsTab />} />
        <Route path="bills/companies" element={<CompanyHistoryRoute />} />
        <Route
          path="bills/companies/:companyKey"
          element={<CompanyHistoryDetailRoute />}
        />
        <Route path="bills/:billId" element={<BillDetailRoute />} />
        <Route path="paycheck" element={<PaycheckTab />} />
        <Route path="debt" element={<DebtTab />} />
        <Route path="debt/planner" element={<DebtPlannerRoute />} />
        <Route path="debt/mortgage-calculator" element={<MortgageCalculatorRoute />} />
        <Route path="debt/:accountId" element={<DebtDetailRoute />} />
        <Route path="goals" element={<GoalsTab />} />
        <Route path="budget" element={<BudgetTab />} />
        <Route path="settings" element={<SettingsTab />} />
      </Route>
      <Route path="*" element={<RootRedirect status={status} />} />
    </Routes>
  );
}

function RootRedirect({
  status,
}: {
  status: ReturnType<typeof useAuth>["status"];
}) {
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (!status.unlocked) return <Navigate to="/unlock" replace />;
  if (!hasRelayConfigured(status)) return <Navigate to="/relay-setup" replace />;
  return <Navigate to="/app" replace />;
}
