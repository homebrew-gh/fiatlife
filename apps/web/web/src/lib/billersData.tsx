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
  BILLER_D_TAG_PREFIX,
  billerDTag,
  newBillerId,
  normalizeCompanyName,
  parseBillerRecord,
  serializeBiller,
  type Biller,
} from "./biller";

type BillersDataContextValue = {
  billers: Biller[];
  loading: boolean;
  error: string | null;
  saving: boolean;
  reload: () => Promise<void>;
  getBillerById: (id: string) => Biller | undefined;
  getOrCreateByName: (name: string) => Promise<Biller>;
  saveBiller: (biller: Biller) => Promise<Biller>;
  archiveBiller: (biller: Biller) => Promise<void>;
  unarchiveBiller: (biller: Biller) => Promise<void>;
  deleteBiller: (biller: Biller) => Promise<void>;
  setCompanyArchived: (
    companyKey: string,
    companyName: string,
    archived: boolean,
  ) => Promise<void>;
  linkToBill: (billerId: string, billId: string) => Promise<void>;
  unlinkFromBill: (billerId: string, billId: string) => Promise<void>;
};

const BillersDataContext = createContext<BillersDataContextValue | null>(null);

function isBillerDTag(dTag: string): boolean {
  return dTag.startsWith(BILLER_D_TAG_PREFIX);
}

export function BillersDataProvider({ children }: { children: ReactNode }) {
  const [billers, setBillers] = useState<Biller[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const parsed: Biller[] = [];
      for (const record of records) {
        const dTag = record.d_tag?.trim() ?? "";
        if (!isBillerDTag(dTag) || !record.plaintext) continue;
        const biller = parseBillerRecord(dTag, record.plaintext);
        if (biller) parsed.push(biller);
      }
      parsed.sort((a, b) =>
        a.name.localeCompare(b.name, undefined, { sensitivity: "base" }),
      );
      setBillers(parsed);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load billers.");
      setBillers([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const publish = useCallback(
    async (biller: Biller) => {
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: billerDTag(biller.id),
          plaintext: serializeBiller(biller),
        });
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

  const getBillerById = useCallback(
    (id: string) => billers.find((b) => b.id === id),
    [billers],
  );

  const saveBiller = useCallback(
    async (biller: Biller) => {
      const now = Date.now();
      const withId = biller.id ? biller : { ...biller, id: newBillerId() };
      const normalized: Biller = {
        ...withId,
        name: withId.name.trim(),
        normalizedName:
          withId.normalizedName.trim() || normalizeCompanyName(withId.name),
        updatedAt: now,
      };
      await publish(normalized);
      return normalized;
    },
    [publish],
  );

  const getOrCreateByName = useCallback(
    async (name: string) => {
      const trimmed = name.trim();
      const normalized = normalizeCompanyName(trimmed);
      const existing = billers.find((b) => b.normalizedName === normalized);
      if (existing) {
        // Rename in place if the user typed a different display casing/spelling.
        if (existing.name !== trimmed && trimmed) {
          return saveBiller({ ...existing, name: trimmed });
        }
        return existing;
      }
      return saveBiller({
        id: "",
        name: trimmed,
        normalizedName: normalized,
        isArchived: false,
        updatedAt: Date.now(),
      });
    },
    [billers, saveBiller],
  );

  const linkToBill = useCallback(
    async (billerId: string, billId: string) => {
      const biller = billers.find((b) => b.id === billerId);
      if (!biller || biller.linkedBillId === billId) return;
      await saveBiller({ ...biller, linkedBillId: billId });
    },
    [billers, saveBiller],
  );

  const unlinkFromBill = useCallback(
    async (billerId: string, billId: string) => {
      const biller = billers.find((b) => b.id === billerId);
      if (!biller || biller.linkedBillId !== billId) return;
      await saveBiller({ ...biller, linkedBillId: null });
    },
    [billers, saveBiller],
  );

  const archiveBiller = useCallback(
    async (biller: Biller) => {
      await saveBiller({ ...biller, isArchived: true });
    },
    [saveBiller],
  );

  const unarchiveBiller = useCallback(
    async (biller: Biller) => {
      await saveBiller({ ...biller, isArchived: false });
    },
    [saveBiller],
  );

  const deleteBiller = useCallback(
    async (biller: Biller) => {
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: billerDTag(biller.id),
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

  const setCompanyArchived = useCallback(
    async (companyKey: string, companyName: string, archived: boolean) => {
      if (companyKey.startsWith("id:")) {
        const id = companyKey.slice("id:".length);
        const biller = billers.find((b) => b.id === id);
        if (!biller) return;
        await saveBiller({ ...biller, isArchived: archived });
        return;
      }
      if (companyKey.startsWith("name:")) {
        const normalized = companyKey.slice("name:".length);
        const existing = billers.find((b) => b.normalizedName === normalized);
        const base =
          existing ??
          (await getOrCreateByName(companyName || normalized));
        await saveBiller({ ...base, isArchived: archived });
      }
    },
    [billers, saveBiller, getOrCreateByName],
  );

  const value = useMemo(
    () => ({
      billers,
      loading,
      error,
      saving,
      reload,
      getBillerById,
      getOrCreateByName,
      saveBiller,
      archiveBiller,
      unarchiveBiller,
      deleteBiller,
      setCompanyArchived,
      linkToBill,
      unlinkFromBill,
    }),
    [
      billers,
      loading,
      error,
      saving,
      reload,
      getBillerById,
      getOrCreateByName,
      saveBiller,
      archiveBiller,
      unarchiveBiller,
      deleteBiller,
      setCompanyArchived,
      linkToBill,
      unlinkFromBill,
    ],
  );

  return (
    <BillersDataContext.Provider value={value}>
      {children}
    </BillersDataContext.Provider>
  );
}

export function useBillersData(): BillersDataContextValue {
  const ctx = useContext(BillersDataContext);
  if (!ctx) {
    throw new Error("useBillersData must be used inside <BillersDataProvider>");
  }
  return ctx;
}
