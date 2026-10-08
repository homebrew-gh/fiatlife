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
import {
  ApiError,
  api,
  type SimpleFinAccount,
  type SimpleFinBalances,
  type SimpleFinStatus,
} from "./api";
import type { BankAccountType } from "./bankAccount";
import { useBankAccountsData } from "./bankAccountsData";
import { useDebtData } from "./debtData";
import {
  accountsNeedingBalanceUpdate,
  debtAccountsNeedingBalanceUpdate,
  looksLikeDebt,
  newAccountFromSimpleFin,
  SIMPLEFIN_AUTO_SYNC_MS,
  withSimpleFinBalance,
  withSimpleFinDebtBalance,
} from "./simplefin";
import { useOptionalSyncStatus } from "./syncStatus";

export type SimpleFinLinkTarget =
  | { kind: "none" }
  | { kind: "existing"; accountId: string }
  | { kind: "new"; type: BankAccountType }
  | { kind: "debt"; accountId: string };

type SimpleFinDataContextValue = {
  status: SimpleFinStatus | null;
  balances: SimpleFinBalances | null;
  loading: boolean;
  busy: boolean;
  error: string | null;
  connect: (token: string) => Promise<void>;
  sync: () => Promise<void>;
  disconnect: () => Promise<void>;
  linkAccount: (
    source: SimpleFinAccount,
    target: SimpleFinLinkTarget,
  ) => Promise<void>;
};

const SimpleFinDataContext = createContext<SimpleFinDataContextValue | null>(
  null,
);

function errorMessage(e: unknown, fallback: string): string {
  return e instanceof ApiError ? e.message : fallback;
}

export function SimpleFinDataProvider({ children }: { children: ReactNode }) {
  const { accounts, loading: bankLoading, saveAccount } = useBankAccountsData();
  const {
    accounts: debtAccounts,
    loading: debtLoading,
    saveSyncedAccount,
  } = useDebtData();
  const accountsLoading = bankLoading || debtLoading;
  const { notify } = useOptionalSyncStatus();
  const [status, setStatus] = useState<SimpleFinStatus | null>(null);
  const [balances, setBalances] = useState<SimpleFinBalances | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const autoSynced = useRef(false);

  const accountsRef = useRef(accounts);
  useEffect(() => {
    accountsRef.current = accounts;
  }, [accounts]);

  const debtAccountsRef = useRef(debtAccounts);
  useEffect(() => {
    debtAccountsRef.current = debtAccounts;
  }, [debtAccounts]);

  const loadStatus = useCallback(async () => {
    try {
      const next = await api.simplefinStatus();
      setStatus(next);
      if (next.connected) {
        const saved = await api.simplefinBalances();
        setBalances((current) => current ?? saved);
      }
    } catch (e) {
      setError(errorMessage(e, "Could not load SimpleFIN status."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  const applyBalances = useCallback(
    async (result: SimpleFinBalances) => {
      setBalances(result);
      const updates = accountsNeedingBalanceUpdate(
        accountsRef.current,
        result.accounts,
      );
      for (const account of updates) {
        await saveAccount(account);
      }
      const debtUpdates = debtAccountsNeedingBalanceUpdate(
        debtAccountsRef.current,
        result.accounts,
      );
      for (const account of debtUpdates) {
        await saveSyncedAccount(account);
      }
    },
    [saveAccount, saveSyncedAccount],
  );

  const runBalanceRequest = useCallback(
    async (request: () => Promise<SimpleFinBalances>, fallback: string) => {
      setBusy(true);
      setError(null);
      try {
        await applyBalances(await request());
      } catch (e) {
        const msg = errorMessage(e, fallback);
        setError(msg);
        throw e;
      } finally {
        setBusy(false);
        void loadStatus();
      }
    },
    [applyBalances, loadStatus],
  );

  const sync = useCallback(
    () => runBalanceRequest(api.simplefinSync, "SimpleFIN sync failed."),
    [runBalanceRequest],
  );

  const connect = useCallback(
    (token: string) =>
      runBalanceRequest(
        () => api.simplefinConnect(token),
        "Could not connect to SimpleFIN.",
      ),
    [runBalanceRequest],
  );

  const disconnect = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      await api.simplefinDisconnect();
      setBalances(null);
      await loadStatus();
    } catch (e) {
      setError(errorMessage(e, "Could not disconnect SimpleFIN."));
      throw e;
    } finally {
      setBusy(false);
    }
  }, [loadStatus]);

  useEffect(() => {
    if (autoSynced.current || loading || accountsLoading || !status?.connected) {
      return;
    }
    autoSynced.current = true;
    const last = status.last_fetched_at_ms ?? 0;
    if (Date.now() - last < SIMPLEFIN_AUTO_SYNC_MS) return;
    sync().catch((e) => {
      notify(errorMessage(e, "SimpleFIN sync failed."), "error");
    });
  }, [loading, accountsLoading, status, sync, notify]);

  const linkAccount = useCallback(
    async (source: SimpleFinAccount, target: SimpleFinLinkTarget) => {
      if ((target.kind === "existing" || target.kind === "new") && looksLikeDebt(source)) {
        setError(
          `${source.name} has a negative balance, so it looks like a card or loan. Link it to a debt account instead.`,
        );
        return;
      }
      setBusy(true);
      setError(null);
      try {
        const current = accountsRef.current;
        const currentDebt = debtAccountsRef.current;
        const keepBankId = target.kind === "existing" ? target.accountId : null;
        const keepDebtId = target.kind === "debt" ? target.accountId : null;
        for (const account of current) {
          if (account.simplefinAccountKey === source.key && account.id !== keepBankId) {
            await saveAccount({
              ...account,
              simplefinAccountKey: null,
              updatedAt: Date.now(),
            });
          }
        }
        for (const account of currentDebt) {
          if (account.simplefinAccountKey === source.key && account.id !== keepDebtId) {
            await saveSyncedAccount({
              ...account,
              simplefinAccountKey: null,
              simplefinBalance: null,
              simplefinBalanceAsOf: null,
            });
          }
        }
        if (target.kind === "existing") {
          const account = current.find((a) => a.id === target.accountId);
          if (account) await saveAccount(withSimpleFinBalance(account, source));
        } else if (target.kind === "new") {
          await saveAccount(newAccountFromSimpleFin(source, target.type));
        } else if (target.kind === "debt") {
          const account = currentDebt.find((a) => a.id === target.accountId);
          if (account) {
            await saveSyncedAccount(withSimpleFinDebtBalance(account, source));
          }
        }
      } catch (e) {
        setError(errorMessage(e, "Could not update account link."));
      } finally {
        setBusy(false);
      }
    },
    [saveAccount, saveSyncedAccount],
  );

  const value = useMemo(
    () => ({
      status,
      balances,
      loading,
      busy,
      error,
      connect,
      sync,
      disconnect,
      linkAccount,
    }),
    [status, balances, loading, busy, error, connect, sync, disconnect, linkAccount],
  );

  return (
    <SimpleFinDataContext.Provider value={value}>
      {children}
    </SimpleFinDataContext.Provider>
  );
}

export function useSimpleFinData(): SimpleFinDataContextValue {
  const ctx = useContext(SimpleFinDataContext);
  if (!ctx) {
    throw new Error("useSimpleFinData must be used inside <SimpleFinDataProvider>");
  }
  return ctx;
}
