import { useState } from "react";
import { Link, Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { Logo } from "../components/Logo";
import { ThemeToggle } from "../components/ThemeToggle";
import { AppSettingsDataProvider } from "../lib/appSettingsData";
import { BankAccountsDataProvider } from "../lib/bankAccountsData";
import { BitcoinDataProvider } from "../lib/bitcoinData";
import { BillersDataProvider } from "../lib/billersData";
import { BillsDataProvider } from "../lib/billsData";
import { BudgetDataProvider } from "../lib/budgetData";
import { DebtDataProvider } from "../lib/debtData";
import { GoalsDataProvider } from "../lib/goalsData";
import { SalaryDataProvider } from "../lib/salaryData";
import { SimpleFinDataProvider } from "../lib/simplefinData";
import { SyncStatusProvider } from "../lib/syncStatus";
import { SyncStatusOverlay } from "../components/SyncStatusOverlay";
import { useAuth } from "../lib/auth";
import { hasRelayConfigured } from "../lib/relayUrl";

/** `also`: pages opened from the tab that keep it highlighted. */
const TABS: { to: string; label: string; also?: string[] }[] = [
  { to: "/app", label: "Home", also: ["/app/paycheck", "/app/goals"] },
  { to: "/app/spending", label: "Spending" },
  { to: "/app/bills", label: "Bills" },
  { to: "/app/accounts", label: "Accounts", also: ["/app/debt", "/app/net-worth"] },
];

function isUnder(path: string, base: string): boolean {
  return path === base || path.startsWith(`${base}/`);
}

function tabIsActive(tab: (typeof TABS)[number], path: string): boolean {
  if (tab.to === "/app") {
    return path === "/app" || path === "/app/" || (tab.also ?? []).some((p) => isUnder(path, p));
  }
  return [tab.to, ...(tab.also ?? [])].some((p) => isUnder(path, p));
}

export function AppShell() {
  const { status, loading, lock } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
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
    <BitcoinDataProvider>
    <SalaryDataProvider>
    <GoalsDataProvider>
    <DebtDataProvider>
    <SimpleFinDataProvider>
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
          {TABS.map((tab) => {
            const active = tabIsActive(tab, location.pathname);
            return (
              <Link
                key={tab.to}
                to={tab.to}
                aria-current={active ? "page" : undefined}
                className={clsx("nav-tab min-w-[4.5rem]", active && "nav-tab-active")}
              >
                <span>{tab.label}</span>
              </Link>
            );
          })}
        </div>
      </nav>
      <SyncStatusOverlay />
    </div>
    </BudgetDataProvider>
    </SimpleFinDataProvider>
    </DebtDataProvider>
    </GoalsDataProvider>
    </SalaryDataProvider>
    </BitcoinDataProvider>
    </BankAccountsDataProvider>
    </AppSettingsDataProvider>
    </BillersDataProvider>
    </BillsDataProvider>
    </SyncStatusProvider>
  );
}
