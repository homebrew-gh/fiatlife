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
import { ApiError, api } from "./api";
import { useBillsData } from "./billsData";
import { useOptionalSyncStatus } from "./syncStatus";
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
  housingSatelliteBillIds,
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
  updateStatement: (
    accountId: string,
    input: {
      statementBalance: number;
      statementBalanceAsOfMillis: number;
      statementAmountDue: number | null;
      dueDay: number;
      paymentAmount: number;
      balanceAfterPayment: number;
    },
  ) => Promise<void>;
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
    loading: billsLoading,
    getBillById,
    getBillsLinkedToAccount,
    saveBill,
    deleteBill,
  } = useBillsData();

  const [accounts, setAccounts] = useState<CreditAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const { notify, refresh } = useOptionalSyncStatus();

  const accountsRef = useRef(accounts);
  const legacyMigrationAttempts = useRef(new Set<string>());
  useEffect(() => {
    accountsRef.current = accounts;
  }, [accounts]);

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
      const prev = accountsRef.current.find((a) => a.id === account.id);
      setAccounts((list) =>
        sortCreditAccounts([
          ...list.filter((a) => a.id !== account.id),
          account,
        ]),
      );
      try {
        await api.publishAppData({
          d_tag: creditAccountDTag(account.id),
          plaintext: serializeCreditAccount(account),
        });
        refresh({ afterPublish: true });
      } catch (e) {
        setAccounts((list) => {
          const without = list.filter((a) => a.id !== account.id);
          return prev ? sortCreditAccounts([...without, prev]) : without;
        });
        notify(e instanceof ApiError ? e.message : "Save failed.", "error");
        throw e;
      }
    },
    [notify, refresh],
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

  useEffect(() => {
    if (loading || billsLoading) return;
    const candidates = bills.filter(
      (item) =>
        item.source === "NATIVE" &&
        item.bill.creditCardDetails != null &&
        !item.bill.linkedCreditAccountId &&
        !legacyMigrationAttempts.current.has(item.bill.id) &&
        !accounts.some(
          (account) =>
            account.linkedBillId === item.bill.id ||
            account.name.toLowerCase() === item.bill.name.toLowerCase(),
        ),
    );
    if (candidates.length === 0) return;

    void (async () => {
      for (const item of candidates) {
        legacyMigrationAttempts.current.add(item.bill.id);
        const legacy = item.bill.creditCardDetails;
        if (!legacy) continue;
        try {
          const now = Date.now();
          const saved = await persistWithBilling(
            defaultCreditAccount({
              id: newCreditAccountId(),
              name: item.bill.name,
              type: "CREDIT_CARD",
              institution: item.bill.accountName ?? "",
              apr: legacy.apr ?? 0,
              standardApr: legacy.apr ?? 0,
              currentBalance: Math.max(0, legacy.currentBalance ?? 0),
              statementAmountDue: Math.max(0, item.bill.amount),
              dueDay: item.bill.dueDay,
              linkedBillId: item.bill.id,
              notes: item.bill.notes ?? "",
              statementEntries: item.bill.statementEntries ?? [],
              attachmentHashes: item.bill.attachmentHashes ?? [],
              minimumPaymentType:
                legacy.minimumPaymentType ?? "PERCENT_OF_BALANCE",
              minimumPaymentValue: legacy.minimumPaymentValue ?? 2,
              createdAt: item.bill.createdAt || now,
              updatedAt: now,
            }),
            null,
          );
          await saveBill(
            {
              ...item.bill,
              linkedCreditAccountId: saved.id,
              creditCardDetails: null,
              updatedAt: Date.now(),
            },
            item.source,
            item.dTag,
          );
        } catch {
          legacyMigrationAttempts.current.delete(item.bill.id);
        }
      }
    })();
  }, [
    accounts,
    bills,
    billsLoading,
    loading,
    persistWithBilling,
    saveBill,
  ]);

  const updateBalance = useCallback(
    async (accountId: string, currentBalance: number) => {
      const account = accounts.find((a) => a.id === accountId);
      if (!account) return;
      await saveAccount({ ...account, currentBalance });
    },
    [accounts, saveAccount],
  );

  const updateStatement = useCallback(
    async (
      accountId: string,
      input: {
        statementBalance: number;
        statementBalanceAsOfMillis: number;
        statementAmountDue: number | null;
        dueDay: number;
        paymentAmount: number;
        balanceAfterPayment: number;
      },
    ) => {
      const account = accounts.find((a) => a.id === accountId);
      if (!account) return;
      setSaving(true);
      setError(null);
      try {
        let updated = defaultCreditAccount({
          ...account,
          currentBalance: Math.max(0, input.balanceAfterPayment),
          statementBalanceAsOfMillis: input.statementBalanceAsOfMillis,
          statementAmountDue:
            input.statementAmountDue == null
              ? null
              : Math.max(0, input.statementAmountDue),
          dueDay: Math.max(1, Math.min(31, input.dueDay)),
          updatedAt: Date.now(),
        });
        updated = await ensureBillsForAccount(updated, billOps);
        if (input.paymentAmount > 0 && updated.linkedBillId) {
          const linked = getBillById(updated.linkedBillId);
          if (linked) {
            const now = Date.now();
            await saveBill(
              {
                ...linked.bill,
                paymentHistory: [
                  ...(linked.bill.paymentHistory ?? []),
                  { date: now, amount: input.paymentAmount },
                ],
                isPaid: true,
                lastPaidDate: now,
                updatedAt: now,
              },
              linked.source,
              linked.dTag,
            );
          }
        }
        await publishAccount(updated);
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Statement update failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [accounts, billOps, getBillById, publishAccount, saveBill],
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
      const prev = accountsRef.current.find((a) => a.id === account.id);
      setAccounts((list) => list.filter((a) => a.id !== account.id));
      setSaving(true);
      setError(null);
      try {
        if (account.annualFeeLinkedBillId) {
          const fee = getBillById(account.annualFeeLinkedBillId);
          if (fee) await deleteBill(fee);
        }
        for (const satelliteId of housingSatelliteBillIds(account)) {
          const satellite = getBillById(satelliteId);
          if (satellite) await deleteBill(satellite);
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
        refresh({ afterPublish: true });
      } catch (e) {
        setAccounts((list) => {
          const without = list.filter((a) => a.id !== account.id);
          return prev ? sortCreditAccounts([...without, prev]) : without;
        });
        const msg = e instanceof ApiError ? e.message : "Delete failed.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [deleteBill, getBillById, getBillsLinkedToAccount, notify, refresh],
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
      updateStatement,
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
      updateStatement,
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
