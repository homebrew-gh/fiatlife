import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import clsx from "clsx";
import { SecretInput } from "../../components/SecretInput";
import { BankAccountSheet } from "../../components/settings/BankAccountSheet";
import { ApiError, api } from "../../lib/api";
import { useAppSettingsData } from "../../lib/appSettingsData";
import { useAuth } from "../../lib/auth";
import type { BankAccount } from "../../lib/bankAccount";
import { useBankAccountsData } from "../../lib/bankAccountsData";
import {
  hasRelayConfigured,
  isAllowedRelayUrl,
  relayHostLabel,
  relayPrefillFromStatus,
  relayUrlsFromStatus,
  RELAY_URL_POLICY,
} from "../../lib/relayUrl";

function normalizeBlossomUrl(url: string): string {
  const trimmed = url.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
    return trimmed.replace(/\/+$/, "");
  }
  return `https://${trimmed.replace(/\/+$/, "")}`;
}

export function SettingsTab() {
  const { status, refresh, wipe } = useAuth();
  const navigate = useNavigate();
  const {
    settings,
    loading: settingsLoading,
    error: settingsError,
    saving: settingsSaving,
    saveSettings,
  } = useAppSettingsData();
  const {
    accounts: bankAccounts,
    loading: bankLoading,
    error: bankError,
    saving: bankSaving,
    saveAccount,
    deleteAccount,
  } = useBankAccountsData();

  const [editingBank, setEditingBank] = useState<BankAccount | null>(null);
  const [bankSheetOpen, setBankSheetOpen] = useState(false);

  const [relayUrl, setRelayUrl] = useState(
    () => relayUrlsFromStatus(status)[0] ?? "wss://",
  );

  useEffect(() => {
    const urls = relayUrlsFromStatus(status);
    if (urls.length > 0) {
      setRelayUrl(urls[0] ?? "wss://");
    }
  }, [
    status?.relay_url,
    status?.relay_urls,
    status?.detected_relay_url,
    status?.suggested_relay_url,
    status?.relay_prefill_url,
  ]);
  const [relaySaving, setRelaySaving] = useState(false);
  const [relayError, setRelayError] = useState<string | null>(null);
  const [relayOk, setRelayOk] = useState(false);

  const [blossomUrl, setBlossomUrl] = useState("");
  const [blossomOk, setBlossomOk] = useState(false);

  const [connection, setConnection] = useState<{
    connected: boolean;
    message?: string;
    checking: boolean;
  }>({ connected: false, checking: true });

  const [wipePass, setWipePass] = useState("");
  const [wipeConfirm, setWipeConfirm] = useState("");
  const [wipeError, setWipeError] = useState<string | null>(null);
  const [wiping, setWiping] = useState(false);

  useEffect(() => {
    setBlossomUrl(settings.blossomUrl);
  }, [settings.blossomUrl]);

  const checkConnection = async () => {
    setConnection((c) => ({ ...c, checking: true }));
    try {
      const res = await api.relayConnection();
      setConnection({
        connected: res.connected,
        message: res.message ?? undefined,
        checking: false,
      });
    } catch (e) {
      setConnection({
        connected: false,
        message: e instanceof ApiError ? e.message : "Connection check failed.",
        checking: false,
      });
    }
  };

  useEffect(() => {
    void checkConnection();
  }, [status?.relay_url, status?.relay_urls]);

  const saveRelay = async (e: React.FormEvent) => {
    e.preventDefault();
    setRelayError(null);
    setRelayOk(false);
    if (!isAllowedRelayUrl(relayUrl)) {
      setRelayError(RELAY_URL_POLICY);
      return;
    }
    setRelaySaving(true);
    try {
      await api.setRelay({ relay_url: relayUrl, relay_urls: [relayUrl] });
      await refresh();
      setRelayOk(true);
      void checkConnection();
    } catch (err) {
      setRelayError(err instanceof ApiError ? err.message : "Save failed.");
    } finally {
      setRelaySaving(false);
    }
  };

  const saveBlossom = async (e: React.FormEvent) => {
    e.preventDefault();
    setBlossomOk(false);
    try {
      await saveSettings({ blossomUrl: normalizeBlossomUrl(blossomUrl) });
      setBlossomOk(true);
    } catch {
      /* error via settingsError */
    }
  };

  const onWipe = async (e: React.FormEvent) => {
    e.preventDefault();
    setWipeError(null);
    setWiping(true);
    try {
      await wipe({ passphrase: wipePass, confirmation: wipeConfirm });
      navigate("/setup", { replace: true });
    } catch (err) {
      setWipeError(err instanceof ApiError ? err.message : "Remove key failed.");
    } finally {
      setWiping(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-lg space-y-6">
      <div>
        <h1 className="page-title">Settings</h1>
        <p className="text-sm text-muted mt-1">
          Relay, Blossom, payment accounts, and local key management.
        </p>
      </div>

      <section className="card p-5 space-y-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="font-medium text-body">Connection</h2>
          <button
            type="button"
            className="btn-ghost text-xs py-1"
            onClick={() => void checkConnection()}
            disabled={connection.checking}
          >
            {connection.checking ? "Checking…" : "Refresh"}
          </button>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={clsx(
              "h-2.5 w-2.5 rounded-full shrink-0",
              connection.checking
                ? "bg-muted animate-pulse"
                : connection.connected
                  ? "bg-success"
                  : "bg-error",
            )}
            aria-hidden
          />
          <p className="text-sm">
            {connection.checking
              ? "Checking relay…"
              : connection.connected
                ? "Relay connected"
                : "Relay disconnected"}
          </p>
        </div>
        {!connection.connected && connection.message ? (
          <p className="text-xs text-muted">{connection.message}</p>
        ) : null}
      </section>

      <section className="card p-5 space-y-3">
        <h2 className="font-medium text-body">Identity</h2>
        {status?.npub ? (
          <p className="font-mono text-xs break-all text-muted">
            {status.npub}
          </p>
        ) : (
          <p className="text-sm text-muted">No npub cached.</p>
        )}
        {hasRelayConfigured(status) ? (
          <p className="text-sm text-muted">
            Relay:{" "}
            <span className="font-mono">
              {relayHostLabel(relayUrlsFromStatus(status)[0])}
            </span>
          </p>
        ) : null}
      </section>

      <section className="card p-5 space-y-3">
        <div>
          <h2 className="font-medium text-body">Payment Accounts (Banks)</h2>
          <p className="text-sm text-muted mt-1">
            Named accounts to tag which bills are paid from which account. No
            credentials stored — syncs to your relay for Android too.
          </p>
        </div>
        {bankLoading ? (
          <p className="text-sm text-muted">Loading accounts…</p>
        ) : null}
        {bankError ? (
          <p className="text-sm text-error" role="alert">
            {bankError}
          </p>
        ) : null}
        {!bankLoading && bankAccounts.length === 0 ? (
          <p className="text-sm text-muted">No payment accounts yet.</p>
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border">
            {bankAccounts.map((account) => (
              <li key={account.id}>
                <button
                  type="button"
                  className="w-full flex items-center justify-between gap-3 px-4 py-3 text-left hover:bg-surface-variant/50 transition-colors"
                  onClick={() => {
                    setEditingBank(account);
                    setBankSheetOpen(true);
                  }}
                >
                  <span className="font-medium">{account.name}</span>
                  <span className="text-muted text-sm">Edit</span>
                </button>
              </li>
            ))}
          </ul>
        )}
        <button
          type="button"
          className="btn-ghost w-full"
          onClick={() => {
            setEditingBank({ id: "", name: "" });
            setBankSheetOpen(true);
          }}
          disabled={bankSaving}
        >
          + Add bank account
        </button>
      </section>

      <section className="card p-5">
        <h2 className="font-medium text-body mb-3">Nostr Relay</h2>
        {(() => {
          const prefill = relayPrefillFromStatus(status);
          if (!prefill || prefill === relayUrl.trim()) return null;
          return (
            <div className="notice-panel p-3 text-sm space-y-2 mb-3">
              <p className="text-body">
                This server has{" "}
                <span className="font-semibold">
                  {status?.detected_relay_label?.trim() || "Nostr RS Relay"}
                </span>{" "}
                available at:
              </p>
              <p className="font-mono text-xs text-muted break-all">{prefill}</p>
              <button
                type="button"
                className="btn-ghost text-sm"
                disabled={relaySaving}
                onClick={() => setRelayUrl(prefill)}
              >
                Use detected relay
              </button>
            </div>
          );
        })()}
        <form className="space-y-3" onSubmit={saveRelay}>
          <input
            className="input font-mono text-sm"
            type="url"
            value={relayUrl}
            onChange={(e) => setRelayUrl(e.target.value.trim())}
          />
          {relayError ? (
            <p className="text-sm text-error">{relayError}</p>
          ) : null}
          {relayOk ? (
            <p className="text-sm text-success">Relay saved.</p>
          ) : null}
          <button type="submit" className="btn-primary" disabled={relaySaving}>
            {relaySaving ? "Saving…" : "Save relay"}
          </button>
        </form>
      </section>

      <section className="card p-5">
        <h2 className="font-medium text-body mb-1">Blossom Server</h2>
        <p className="text-sm text-muted mb-3">
          URL for statement attachments (BUD-01). Syncs to your relay via app
          settings — shared with Android.
        </p>
        <form className="space-y-3" onSubmit={saveBlossom}>
          <input
            className="input font-mono text-sm"
            type="url"
            placeholder="https://blossom.example.com"
            value={blossomUrl}
            onChange={(e) => setBlossomUrl(e.target.value)}
            disabled={settingsLoading}
          />
          {settingsError ? (
            <p className="text-sm text-error">{settingsError}</p>
          ) : null}
          {blossomOk ? (
            <p className="text-sm text-success">Blossom URL saved.</p>
          ) : null}
          <button
            type="submit"
            className="btn-primary"
            disabled={settingsSaving || settingsLoading}
          >
            {settingsSaving ? "Saving…" : "Save Blossom URL"}
          </button>
        </form>
      </section>

      <section className="card p-5 space-y-2">
        <h2 className="font-medium text-body">Data & Privacy</h2>
        <p className="text-sm text-muted">
          FiatLife stores your financial data as NIP-44 encrypted kind-30078
          events on your Nostr relay. Statement files use Blossom blob storage.
          Your nsec is encrypted on this server and never leaves your Start9
          package unencrypted.
        </p>
      </section>

      <section className="card p-5">
        <h2 className="font-medium text-body">About</h2>
        <p className="text-sm text-muted mt-1">FiatLife web v0.1.0</p>
      </section>

      <section className="card p-5 border-error/30">
        <h2 className="font-medium text-error mb-1">Remove Local Key</h2>
        <p className="text-sm text-muted mb-4">
          Wipes the encrypted nsec from this server. Your relay data is unchanged.
        </p>
        <form className="space-y-3" onSubmit={onWipe}>
          <div>
            <label className="label" htmlFor="wipe-pass">
              Passphrase
            </label>
            <SecretInput
              id="wipe-pass"
              value={wipePass}
              onChange={(e) => setWipePass(e.target.value)}
            />
          </div>
          <div>
            <label className="label" htmlFor="wipe-confirm">
              Type DELETE to confirm
            </label>
            <input
              id="wipe-confirm"
              className="input"
              value={wipeConfirm}
              onChange={(e) => setWipeConfirm(e.target.value)}
            />
          </div>
          {wipeError ? <p className="text-sm text-error">{wipeError}</p> : null}
          <button
            type="submit"
            className="btn-ghost border-error text-error"
            disabled={wiping || wipeConfirm !== "DELETE"}
          >
            {wiping ? "Removing…" : "Log out and remove key"}
          </button>
        </form>
      </section>

      <BankAccountSheet
        open={bankSheetOpen}
        account={editingBank}
        onClose={() => {
          setBankSheetOpen(false);
          setEditingBank(null);
        }}
        onSave={async (name) => {
          await saveAccount({
            id: editingBank?.id ?? "",
            name,
          });
        }}
        onDelete={
          editingBank?.id
            ? async () => {
                await deleteAccount(editingBank);
              }
            : undefined
        }
        saving={bankSaving}
      />
    </div>
  );
}
