import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { ApiError, api, type AppDataRecord } from "./api";
import { uploadBlob } from "./blossom";
import {
  billDTag,
  markBillPaid,
  markBillUnpaid,
  newBillId,
  parseBillRecord,
  serializeBill,
  skippedNextDueDateMillis,
  type Bill,
  type BillFrequency,
  type BillSource,
  type BillStatusEvent,
  type BillWithSource,
  type StatementEntry,
} from "./bill";
import {
  billToCypherLogTags,
  CYPHERLOG_KIND,
  cypherLogTombstoneDTag,
  cypherLogTombstonePayload,
  deletedSubscriptionIds,
  LEGACY_SUBSCRIPTION_KIND,
  mergeCypherLogSubscriptions,
  parseCypherLog37004Record,
  parseCypherLogRecord,
  rawSubscriptionId,
  SUBSCRIPTION_DTAG_PREFIX,
  toSubscriptionDTag,
} from "./cypherlog";

type BillsDataContextValue = {
  bills: BillWithSource[];
  allBills: BillWithSource[];
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  getBillById: (id: string) => BillWithSource | undefined;
  getBillsLinkedToAccount: (accountId: string) => BillWithSource[];
  saveBill: (
    bill: Bill,
    source: BillSource,
    dTag?: string,
    preservedTags?: Record<string, string[]>,
  ) => Promise<void>;
  deleteBill: (item: BillWithSource) => Promise<void>;
  togglePaid: (item: BillWithSource) => Promise<void>;
  recordPayment: (
    item: BillWithSource,
    amount: number,
    newBalance?: number,
  ) => Promise<void>;
  cancelSubscription: (item: BillWithSource, note?: string) => Promise<void>;
  reactivateSubscription: (item: BillWithSource) => Promise<void>;
  reactivateSubscriptionWithSchedule: (
    item: BillWithSource,
    frequency: BillFrequency,
    newBillingDateMillis: number,
  ) => Promise<void>;
  skipInterval: (item: BillWithSource) => Promise<void>;
  attachStatement: (item: BillWithSource, file: File) => Promise<void>;
  addBill: (
    bill: Omit<Bill, "id" | "createdAt" | "updatedAt">,
    source?: BillSource,
  ) => Promise<Bill>;
};

const BillsDataContext = createContext<BillsDataContextValue | null>(null);

function isBillDTag(dTag: string): boolean {
  return dTag.startsWith("fiatlife/bill/") || dTag.startsWith(SUBSCRIPTION_DTAG_PREFIX);
}

function parseAllBills(
  records: AppDataRecord[],
  cypherlog37004: Awaited<ReturnType<typeof api.listCypherLogSubscriptions>>,
): BillWithSource[] {
  const deletedIds = deletedSubscriptionIds(records);
  const native: BillWithSource[] = [];
  const legacyCypherlog: BillWithSource[] = [];

  for (const record of records) {
    const dTag = record.d_tag?.trim() ?? "";
    if (!isBillDTag(dTag)) continue;

    if (dTag.startsWith(SUBSCRIPTION_DTAG_PREFIX)) {
      if (deletedIds.has(rawSubscriptionId(dTag))) continue;
      const item = parseCypherLogRecord(
        dTag,
        record.tags ?? [],
        record.plaintext,
      );
      if (item) legacyCypherlog.push(item);
      continue;
    }

    if (!record.plaintext) continue;
    const item = parseBillRecord(dTag, record.plaintext);
    if (item) native.push(item);
  }

  const from37004 = cypherlog37004
    .filter((record) => !deletedIds.has(rawSubscriptionId(record.d_tag)))
    .map(parseCypherLog37004Record)
    .filter((item): item is BillWithSource => item != null);

  const cypherlog = mergeCypherLogSubscriptions(legacyCypherlog, from37004);
  const parsed = [...native, ...cypherlog];
  parsed.sort((a, b) =>
    a.bill.name.localeCompare(b.bill.name, undefined, { sensitivity: "base" }),
  );
  return parsed;
}

export function BillsDataProvider({ children }: { children: ReactNode }) {
  const [allBills, setAllBills] = useState<BillWithSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const [records, cypherlog37004] = await Promise.all([
        api.listAppData(),
        api.listCypherLogSubscriptions(),
      ]);
      setAllBills(parseAllBills(records, cypherlog37004));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load bills.");
      setAllBills([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const bills = useMemo(
    () => allBills.filter((item) => !item.bill.isCancelled),
    [allBills],
  );

  const publish = useCallback(
    async (
      bill: Bill,
      source: BillSource,
      dTag: string,
      preservedTags?: Record<string, string[]>,
    ) => {
      setSaving(true);
      setError(null);
      try {
        if (source === "CYPHERLOG") {
          const tags = billToCypherLogTags(bill, preservedTags);
          await api.publishCypherLogSubscription({ tags });
          // Best-effort: tombstone legacy kind-30078 copy if it exists.
          const legacyDTag = toSubscriptionDTag(bill.id);
          if (legacyDTag !== bill.id) {
            try {
              await api.publishNostrDeletion({
                kind: LEGACY_SUBSCRIPTION_KIND,
                d_tag: legacyDTag,
              });
            } catch {
              /* legacy row may not exist */
            }
          }
        } else {
          await api.publishAppData({
            d_tag: dTag,
            plaintext: serializeBill(bill),
          });
        }
        await reload();
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "Save failed.");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [reload],
  );

  const getBillById = useCallback(
    (id: string) => allBills.find((item) => item.bill.id === id),
    [allBills],
  );

  const getBillsLinkedToAccount = useCallback(
    (accountId: string) =>
      allBills.filter((item) => item.bill.linkedCreditAccountId === accountId),
    [allBills],
  );

  const saveBill = useCallback(
    async (
      bill: Bill,
      source: BillSource,
      dTag?: string,
      preservedTags?: Record<string, string[]>,
    ) => {
      const tag =
        dTag ??
        (source === "CYPHERLOG" ? bill.id : billDTag(bill.id, source));
      await publish(bill, source, tag, preservedTags);
    },
    [publish],
  );

  const deleteBill = useCallback(
    async (item: BillWithSource) => {
      setSaving(true);
      setError(null);
      try {
        if (item.source === "CYPHERLOG" || item.isCypherLog) {
          const rawId = rawSubscriptionId(item.dTag);
          await api.publishAppData({
            d_tag: cypherLogTombstoneDTag(rawId),
            plaintext: cypherLogTombstonePayload(rawId),
          });
          try {
            await api.publishNostrDeletion({
              kind: CYPHERLOG_KIND,
              d_tag: rawId,
            });
          } catch {
            /* relay may not support deletion */
          }
          try {
            await api.publishNostrDeletion({
              kind: LEGACY_SUBSCRIPTION_KIND,
              d_tag: toSubscriptionDTag(rawId),
            });
          } catch {
            /* legacy row may not exist */
          }
        } else {
          await api.publishAppData({
            d_tag: item.dTag,
            plaintext: JSON.stringify({ deleted: true }),
          });
        }
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

  const togglePaid = useCallback(
    async (item: BillWithSource) => {
      const now = Date.now();
      const updated = item.bill.isPaid
        ? markBillUnpaid(item.bill, now)
        : markBillPaid(item.bill, undefined, now);
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const recordPayment = useCallback(
    async (item: BillWithSource, amount: number, newBalance?: number) => {
      const now = Date.now();
      let updated = markBillPaid(item.bill, amount, now);
      if (newBalance != null && updated.creditCardDetails) {
        updated = {
          ...updated,
          creditCardDetails: {
            ...updated.creditCardDetails,
            currentBalance: newBalance,
          },
        };
      }
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const appendStatus = (
    bill: Bill,
    type: string,
    note: string,
    now: number,
  ): BillStatusEvent[] => {
    const history = [...(bill.statusHistory ?? [])];
    history.push({ date: now, type, note });
    return history;
  };

  const cancelSubscription = useCallback(
    async (item: BillWithSource, note = "") => {
      const now = Date.now();
      const updated: Bill = {
        ...item.bill,
        isCancelled: true,
        cancelledAt: now,
        statusHistory: appendStatus(item.bill, "cancelled", note, now),
        updatedAt: now,
      };
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const reactivateSubscription = useCallback(
    async (item: BillWithSource) => {
      const now = Date.now();
      const updated: Bill = {
        ...item.bill,
        isCancelled: false,
        cancelledAt: null,
        statusHistory: appendStatus(item.bill, "activated", "", now),
        updatedAt: now,
      };
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const reactivateSubscriptionWithSchedule = useCallback(
    async (
      item: BillWithSource,
      frequency: BillFrequency,
      newBillingDateMillis: number,
    ) => {
      const dueDay = new Date(newBillingDateMillis).getDate();
      const now = Date.now();
      const updated: Bill = {
        ...item.bill,
        isCancelled: false,
        cancelledAt: null,
        isRecurring: true,
        frequency,
        dueDay: Math.min(Math.max(dueDay, 1), 31),
        initialPurchaseDateMillis: newBillingDateMillis,
        renewalDateMillis: newBillingDateMillis,
        isPaid: false,
        lastPaidDate: null,
        statusHistory: appendStatus(item.bill, "activated", "Subscription reactivated", now),
        updatedAt: now,
      };
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const skipInterval = useCallback(
    async (item: BillWithSource) => {
      const skipped = skippedNextDueDateMillis(item.bill);
      if (skipped == null) return;
      const now = Date.now();
      const updated: Bill = {
        ...item.bill,
        renewalDateMillis: skipped,
        statusHistory: appendStatus(item.bill, "skipped_interval", "", now),
        updatedAt: now,
      };
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const attachStatement = useCallback(
    async (item: BillWithSource, file: File) => {
      const descriptor = await uploadBlob(file);
      const now = Date.now();
      const entry: StatementEntry = {
        hash: descriptor.sha256,
        addedAt: now,
        label: file.name || "Statement",
      };
      const updated: Bill = {
        ...item.bill,
        statementEntries: [...(item.bill.statementEntries ?? []), entry],
        updatedAt: now,
      };
      await saveBill(updated, item.source, item.dTag, item.preservedTags);
    },
    [saveBill],
  );

  const addBill = useCallback(
    async (
      partial: Omit<Bill, "id" | "createdAt" | "updatedAt">,
      source: BillSource = "NATIVE",
    ) => {
      const now = Date.now();
      const bill: Bill = {
        ...partial,
        id: newBillId(),
        isRecurring: partial.isRecurring ?? true,
        isCancelled: false,
        isPaid: false,
        paymentHistory: partial.paymentHistory ?? [],
        statementEntries: partial.statementEntries ?? [],
        statusHistory: partial.statusHistory ?? [],
        createdAt: now,
        updatedAt: now,
      };
      await saveBill(bill, source, undefined, undefined);
      return bill;
    },
    [saveBill],
  );

  const value = useMemo(
    () => ({
      bills,
      allBills,
      loading,
      error,
      saving,
      reload,
      getBillById,
      getBillsLinkedToAccount,
      saveBill,
      deleteBill,
      togglePaid,
      recordPayment,
      cancelSubscription,
      reactivateSubscription,
      reactivateSubscriptionWithSchedule,
      skipInterval,
      attachStatement,
      addBill,
    }),
    [
      bills,
      allBills,
      loading,
      error,
      saving,
      reload,
      getBillById,
      getBillsLinkedToAccount,
      saveBill,
      deleteBill,
      togglePaid,
      recordPayment,
      cancelSubscription,
      reactivateSubscription,
      reactivateSubscriptionWithSchedule,
      skipInterval,
      attachStatement,
      addBill,
    ],
  );

  return (
    <BillsDataContext.Provider value={value}>
      {children}
    </BillsDataContext.Provider>
  );
}

export function useBillsData(): BillsDataContextValue {
  const ctx = useContext(BillsDataContext);
  if (!ctx) {
    throw new Error("useBillsData must be used inside <BillsDataProvider>");
  }
  return ctx;
}
