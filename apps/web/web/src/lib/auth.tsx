import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, setUnauthorizedHandler, type AuthStatus, type WipeBody } from "./api";

type AuthContextValue = {
  status: AuthStatus | null;
  loading: boolean;
  refresh: () => Promise<AuthStatus | null>;
  lock: () => Promise<void>;
  wipe: (body: WipeBody) => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const next = await api.authStatus();
      setStatus(next);
      return next;
    } catch {
      setStatus(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const lock = useCallback(async () => {
    try {
      await api.authLock();
    } finally {
      await refresh();
    }
  }, [refresh]);

  const wipe = useCallback(
    async (body: WipeBody) => {
      await api.authWipe(body);
      await refresh();
    },
    [refresh],
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      void refresh();
    });
    return () => setUnauthorizedHandler(null);
  }, [refresh]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, loading, refresh, lock, wipe }),
    [status, loading, refresh, lock, wipe],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
