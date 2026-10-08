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
import { ApiError, api, type BtcPrice } from "./api";
import {
  BITCOIN_WALLET_D_TAG_PREFIX,
  bitcoinWalletDTag,
  newBitcoinWalletId,
  parseBitcoinWalletRecord,
  serializeBitcoinWallet,
  type BitcoinWallet,
} from "./bitcoin";
import { useOptionalSyncStatus } from "./syncStatus";

const PRICE_REFRESH_MS = 5 * 60 * 1000;

type BitcoinDataContextValue = {
  wallets: BitcoinWallet[];
  loading: boolean;
  error: string | null;
  saving: boolean;
  price: BtcPrice | null;
  priceError: string | null;
  reload: () => Promise<void>;
  refreshPrice: () => Promise<void>;
  saveWallet: (wallet: BitcoinWallet) => Promise<BitcoinWallet>;
  deleteWallet: (wallet: BitcoinWallet) => Promise<void>;
};

const BitcoinDataContext = createContext<BitcoinDataContextValue | null>(null);

function sortWallets(list: BitcoinWallet[]): BitcoinWallet[] {
  return [...list].sort((a, b) =>
    a.name.localeCompare(b.name, undefined, { sensitivity: "base" }),
  );
}

export function BitcoinDataProvider({ children }: { children: ReactNode }) {
  const [wallets, setWallets] = useState<BitcoinWallet[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [price, setPrice] = useState<BtcPrice | null>(null);
  const [priceError, setPriceError] = useState<string | null>(null);
  const { notify, refresh } = useOptionalSyncStatus();

  const walletsRef = useRef(wallets);
  useEffect(() => {
    walletsRef.current = wallets;
  }, [wallets]);

  const reload = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const records = await api.listAppData();
      const parsed: BitcoinWallet[] = [];
      for (const record of records) {
        const dTag = record.d_tag?.trim() ?? "";
        if (!dTag.startsWith(BITCOIN_WALLET_D_TAG_PREFIX) || !record.plaintext) {
          continue;
        }
        const wallet = parseBitcoinWalletRecord(dTag, record.plaintext);
        if (wallet) parsed.push(wallet);
      }
      setWallets(sortWallets(parsed));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load bitcoin wallets.");
      setWallets([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshPrice = useCallback(async () => {
    try {
      setPrice(await api.btcPrice());
      setPriceError(null);
    } catch (e) {
      setPriceError(e instanceof ApiError ? e.message : "BTC price unavailable.");
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    void refreshPrice();
    const timer = window.setInterval(() => void refreshPrice(), PRICE_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [refreshPrice]);

  const saveWallet = useCallback(
    async (wallet: BitcoinWallet) => {
      const now = Date.now();
      const normalized: BitcoinWallet = {
        ...wallet,
        id: wallet.id || newBitcoinWalletId(),
        name: wallet.name.trim(),
        createdAt: wallet.createdAt || now,
        updatedAt: now,
      };
      const prev = walletsRef.current.find((w) => w.id === normalized.id);
      setWallets((list) =>
        sortWallets([...list.filter((w) => w.id !== normalized.id), normalized]),
      );
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: bitcoinWalletDTag(normalized.id),
          plaintext: serializeBitcoinWallet(normalized),
        });
        refresh({ afterPublish: true });
        return normalized;
      } catch (e) {
        setWallets((list) => {
          const without = list.filter((w) => w.id !== normalized.id);
          return prev ? sortWallets([...without, prev]) : without;
        });
        const msg = e instanceof ApiError ? e.message : "Save failed.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [notify, refresh],
  );

  const deleteWallet = useCallback(
    async (wallet: BitcoinWallet) => {
      const prev = walletsRef.current.find((w) => w.id === wallet.id);
      setWallets((list) => list.filter((w) => w.id !== wallet.id));
      setSaving(true);
      setError(null);
      try {
        await api.publishAppData({
          d_tag: bitcoinWalletDTag(wallet.id),
          plaintext: JSON.stringify({ deleted: true }),
        });
        refresh({ afterPublish: true });
      } catch (e) {
        setWallets((list) => {
          const without = list.filter((w) => w.id !== wallet.id);
          return prev ? sortWallets([...without, prev]) : without;
        });
        const msg = e instanceof ApiError ? e.message : "Delete failed.";
        setError(msg);
        notify(msg, "error");
        throw e;
      } finally {
        setSaving(false);
      }
    },
    [notify, refresh],
  );

  const value = useMemo(
    () => ({
      wallets,
      loading,
      error,
      saving,
      price,
      priceError,
      reload,
      refreshPrice,
      saveWallet,
      deleteWallet,
    }),
    [
      wallets,
      loading,
      error,
      saving,
      price,
      priceError,
      reload,
      refreshPrice,
      saveWallet,
      deleteWallet,
    ],
  );

  return (
    <BitcoinDataContext.Provider value={value}>
      {children}
    </BitcoinDataContext.Provider>
  );
}

export function useBitcoinData(): BitcoinDataContextValue {
  const ctx = useContext(BitcoinDataContext);
  if (!ctx) {
    throw new Error("useBitcoinData must be used inside <BitcoinDataProvider>");
  }
  return ctx;
}
