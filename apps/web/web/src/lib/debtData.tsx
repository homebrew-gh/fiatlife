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
import { useBillsData } from "./billsData";
import { uploadBlob } from "./blossom";
import {
  ensureBillsForAccount,
  inferCreditPaymentFromBalanceDrop,
  type BillOps,
} from "./creditBillLink";
import {
  CREDIT_D_TAG_PREFIX,
  creditAccountDTag,
  defaultCreditAccount,
  newCreditAccountId,
  parseCreditAccountRecord,
  serializeCreditAccount,
  sortCreditAccounts,
  type CreditAccount,
  type StatementEntry,
} from "./creditAccount";

type DebtDataContextValue = {
  accounts: CreditAccount[];
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  getAccountById: (id: string) => CreditAccount | undefined;
  saveAccount: (account: CreditAccount) => Promise<CreditAccount>;
  addAccount: (
    input: Omit<CreditAccount, "id" | "createdAt" | "updatedAt">,
  ) => Promise<CreditAccount>;
  updateBalance: (accountId: string, currentBalance: number) => Promise<void>;
  setBalanceFromPayment: (
    accountId: string,
    newBalance: number,
  ) => Promise<void>;
  deleteAccount: (account: CreditAccount) => Promise<void>;
  attachStatement: (
    account: CreditAccount,
    file: File,
  ) => Promise<CreditAccount>;
};

const DebtDataContext = createContext<DebtDataContextValue | null>(null);

function isCreditDTag(dTag: string): boolean {
  return dTag.startsWith(CREDIT_D_TAG_PREFIX);
}

export function DebtDataProvider({ children }: { children: ReactNode }) {
  return <DebtDataProviderInner>{children}</DebtDataProviderInner>;
}

function DebtDataProviderInner({ children }: { children: ReactNode }) {
  const {
    bills,
    getBillById,
    getBillsLinkedToAccount,
    saveBill,
    deleteBill,
  } = useBillsData();

  const [accounts, setAccounts] = useState<CreditAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const billOps = useMemo<BillOps>(
    () => ({
      getBillById,
      getBillsLinkedToAccount,
      getAllBills: () => bills.map((item) => item.bill),
      saveBill: (bill, source = "NATIVE", dTag) =>
        saveBill(bill, source, dTag),
      deleteBill,
    }),
    [bills, getBillById, getBillsLinkedToAccount, saveBill, deleteBill],
  );

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const parsed: CreditAccount[] = [];
      for (const record of records) {
        const dTag = record.d_tag?.trim() ?? "";
        if (!isCreditDTag(dTag) || !record.plaintext) continue;
        const account = parseCreditAccountRecord(dTag, record.plaintext);
        if (account) parsed.push(account);
      }
      setAccounts(sortCreditAccounts(parsed));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load debt accounts.");
      setAccounts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const publishAccount = useCallback(
    async (account: CreditAccount) => {
      await api.publishAppData({
        d_tag: creditAccountDTag(account.id),
        plaintext: serializeCreditAccount(account),
      });
      await reload();
    },
    [reload],
  );

  const persistWithBilling = useCallback(
    async (
      account: CreditAccount,
      previous: CreditAccount | null,
    ): Promise<CreditAccount> => {
      setSaving(true);
      setError(null);
      try {
        const now = Date.now();
        let withMeta = defaultCreditAccount({
          ...account,
          id: account.id || newCreditAccountId(),
          createdAt: account.createdAt || now,
          updatedAt: now,
        });

        withMeta = await ensureBillsForAccount(withMeta, billOps);
        await inferCreditPaymentFromBalanceDrop(previous, withMeta, billOps);
        await publishAccount(withMeta);
        return withMeta;
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Save failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [billOps, publishAccount],
  );

  const getAccountById = useCallback(
    (id: string) => accounts.find((a) => a.id === id),
    [accounts],
  );

  const saveAccount = useCallback(
    async (account: CreditAccount) => {
      const previous = accounts.find((a) => a.id === account.id) ?? null;
      return persistWithBilling(account, previous);
    },
    [accounts, persistWithBilling],
  );

  const addAccount = useCallback(
    async (input: Omit<CreditAccount, "id" | "createdAt" | "updatedAt">) => {
      const now = Date.now();
      return persistWithBilling(
        defaultCreditAccount({
          ...input,
          id: newCreditAccountId(),
          createdAt: now,
          updatedAt: now,
        }),
        null,
      );
    },
    [persistWithBilling],
  );

  const updateBalance = useCallback(
    async (accountId: string, currentBalance: number) => {
      const account = accounts.find((a) => a.id === accountId);
      if (!account) return;
      await saveAccount({ ...account, currentBalance });
    },
    [accounts, saveAccount],
  );

  // Apply a new balance after a bill payment was already recorded on the linked
  // bill. Skips inferCreditPaymentFromBalanceDrop (which would double-count) and
  // skips ensureBills (preserves the linked bill + its payment history even when
  // the balance reaches zero; aggregation hides zero-balance debt bills).
  const setBalanceFromPayment = useCallback(
    async (accountId: string, newBalance: number) => {
      const account = accounts.find((a) => a.id === accountId);
      if (!account) return;
      setSaving(true);
      setError(null);
      try {
        await publishAccount(
          defaultCreditAccount({
            ...account,
            currentBalance: Math.max(0, newBalance),
            updatedAt: Date.now(),
          }),
        );
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Save failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [accounts, publishAccount],
  );

  const deleteAccount = useCallback(
    async (account: CreditAccount) => {
      setSaving(true);
      setError(null);
      try {
        if (account.annualFeeLinkedBillId) {
          const fee = getBillById(account.annualFeeLinkedBillId);
          if (fee) await deleteBill(fee);
        }
        if (account.linkedBillId) {
          const linked = getBillById(account.linkedBillId);
          if (linked) await deleteBill(linked);
        }
        for (const item of getBillsLinkedToAccount(account.id)) {
          await deleteBill(item);
        }
        await api.publishAppData({
          d_tag: creditAccountDTag(account.id),
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
    [deleteBill, getBillById, getBillsLinkedToAccount, reload],
  );

  const attachStatement = useCallback(
    async (account: CreditAccount, file: File) => {
      const descriptor = await uploadBlob(file);
      const now = Date.now();
      const entry: StatementEntry = {
        hash: descriptor.sha256,
        addedAt: now,
        label: file.name || "Statement",
      };
      const updated = {
        ...account,
        statementEntries: [...account.statementEntries, entry],
        attachmentHashes: [...account.attachmentHashes, descriptor.sha256],
      };
      return saveAccount(updated);
    },
    [saveAccount],
  );

  const value = useMemo(
    () => ({
      accounts,
      loading,
      error,
      saving,
      reload,
      getAccountById,
      saveAccount,
      addAccount,
      updateBalance,
      setBalanceFromPayment,
      deleteAccount,
      attachStatement,
    }),
    [
      accounts,
      loading,
      error,
      saving,
      reload,
      getAccountById,
      saveAccount,
      addAccount,
      updateBalance,
      setBalanceFromPayment,
      deleteAccount,
      attachStatement,
    ],
  );

  return (
    <DebtDataContext.Provider value={value}>{children}</DebtDataContext.Provider>
  );
}

export function useDebtData(): DebtDataContextValue {
  const ctx = useContext(DebtDataContext);
  if (!ctx) throw new Error("useDebtData must be used inside <DebtDataProvider>");
  return ctx;
}
