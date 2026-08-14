import { useState } from "react";
import { Navigate, NavLink, Outlet, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { Logo } from "../components/Logo";
import { ThemeToggle } from "../components/ThemeToggle";
import { AppSettingsDataProvider } from "../lib/appSettingsData";
import { BankAccountsDataProvider } from "../lib/bankAccountsData";
import { BillersDataProvider } from "../lib/billersData";
import { BillsDataProvider } from "../lib/billsData";
import { BudgetDataProvider } from "../lib/budgetData";
import { DebtDataProvider } from "../lib/debtData";
import { GoalsDataProvider } from "../lib/goalsData";
import { SalaryDataProvider } from "../lib/salaryData";
import { SyncStatusProvider } from "../lib/syncStatus";
import { SyncStatusOverlay } from "../components/SyncStatusOverlay";
import { useAuth } from "../lib/auth";
import { hasRelayConfigured } from "../lib/relayUrl";

const TABS: { to: string; label: string; end?: boolean }[] = [
  { to: "/app", label: "Home", end: true },
  { to: "/app/bills", label: "Bills" },
  { to: "/app/paycheck", label: "Paycheck" },
  { to: "/app/debt", label: "Debt" },
  { to: "/app/goals", label: "Goals" },
  { to: "/app/budget", label: "Budget" },
];

export function AppShell() {
  const { status, loading, lock } = useAuth();
  const navigate = useNavigate();
  const [locking, setLocking] = useState(false);

  if (loading) return null;
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (!status.unlocked) return <Navigate to="/unlock" replace />;
  if (!hasRelayConfigured(status)) return <Navigate to="/relay-setup" replace />;

  const onLock = async () => {
    if (locking) return;
    setLocking(true);
    try {
      await lock();
      navigate("/unlock", { replace: true });
    } finally {
      setLocking(false);
    }
  };

  return (
    <SyncStatusProvider>
    <BillsDataProvider>
    <BillersDataProvider>
    <AppSettingsDataProvider>
    <BankAccountsDataProvider>
    <SalaryDataProvider>
    <GoalsDataProvider>
    <DebtDataProvider>
    <BudgetDataProvider>
    <div className="h-full flex flex-col">
      <header className="app-chrome border-b sticky top-0 z-10">
        <div className="mx-auto max-w-5xl px-4 h-14 flex items-center justify-between gap-3">
          <Logo className="text-base" />
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <NavLink
              to="/app/settings"
              className={({ isActive }) =>
                clsx(
                  "btn-ghost text-sm py-1.5",
                  isActive && "text-accent",
                )
              }
              aria-label="Settings"
            >
              Settings
            </NavLink>
            {/* Spacer + divider keeps Lock away from Settings to avoid mis-taps. */}
            <span className="h-5 w-px bg-outline/60 mx-1" aria-hidden="true" />
            <button
              type="button"
              className="btn-ghost text-sm py-1.5"
              onClick={() => void onLock()}
              disabled={locking}
            >
              {locking ? "Locking…" : "Lock"}
            </button>
          </div>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-5xl px-4 py-6">
          <Outlet />
        </div>
      </main>

      <nav className="app-chrome border-t sticky bottom-0 z-10">
        <div className="mx-auto max-w-5xl px-2 py-2 flex justify-between gap-1 overflow-x-auto">
          {TABS.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                clsx("nav-tab min-w-[4.5rem]", isActive && "nav-tab-active")
              }
            >
              <span>{tab.label}</span>
            </NavLink>
          ))}
        </div>
      </nav>
      <SyncStatusOverlay />
    </div>
    </BudgetDataProvider>
    </DebtDataProvider>
    </GoalsDataProvider>
    </SalaryDataProvider>
    </BankAccountsDataProvider>
    </AppSettingsDataProvider>
    </BillersDataProvider>
    </BillsDataProvider>
    </SyncStatusProvider>
  );
}
