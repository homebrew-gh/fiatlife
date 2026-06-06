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
  BANK_ACCOUNT_D_TAG_PREFIX,
  bankAccountDTag,
  newBankAccountId,
  parseBankAccountRecord,
  serializeBankAccount,
  type BankAccount,
} from "./bankAccount";

type BankAccountsDataContextValue = {
  accounts: BankAccount[];
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  saveAccount: (account: BankAccount) => Promise<BankAccount>;
  deleteAccount: (account: BankAccount) => Promise<void>;
};

const BankAccountsDataContext =
  createContext<BankAccountsDataContextValue | null>(null);

function isBankDTag(dTag: string): boolean {
  return dTag.startsWith(BANK_ACCOUNT_D_TAG_PREFIX);
}

export function BankAccountsDataProvider({ children }: { children: ReactNode }) {
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const parsed: BankAccount[] = [];
      for (const record of records) {
        const dTag = record.d_tag?.trim() ?? "";
        if (!isBankDTag(dTag) || !record.plaintext) continue;
        const account = parseBankAccountRecord(dTag, record.plaintext);
        if (account) parsed.push(account);
      }
      parsed.sort((a, b) =>
        a.name.localeCompare(b.name, undefined, { sensitivity: "base" }),
      );
      setAccounts(parsed);
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : "Could not load bank accounts.",
      );
      setAccounts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const saveAccount = useCallback(
    async (account: BankAccount) => {
      setSaving(true);
      setError(null);
      try {
        const withId = account.id
          ? account
          : { ...account, id: newBankAccountId() };
        const normalized = { ...withId, name: withId.name.trim() };
        await api.publishAppData({
          d_tag: bankAccountDTag(normalized.id),
          plaintext: serializeBankAccount(normalized),
        });
        await reload();
        return normalized;
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Save failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [reload],
  );

  const deleteAccount = useCallback(
    async (account: BankAccount) => {
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: bankAccountDTag(account.id),
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
      accounts,
      loading,
      error,
      saving,
      reload,
      saveAccount,
      deleteAccount,
    }),
    [accounts, loading, error, saving, reload, saveAccount, deleteAccount],
  );

  return (
    <BankAccountsDataContext.Provider value={value}>
      {children}
    </BankAccountsDataContext.Provider>
  );
}

export function useBankAccountsData(): BankAccountsDataContextValue {
  const ctx = useContext(BankAccountsDataContext);
  if (!ctx) {
    throw new Error(
      "useBankAccountsData must be used inside <BankAccountsDataProvider>",
    );
  }
  return ctx;
}
