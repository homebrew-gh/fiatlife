import { useState } from "react";
import type { SimpleFinAccount } from "../../lib/api";
import {
  ALL_BANK_ACCOUNT_TYPES,
  BANK_ACCOUNT_TYPE_LABELS,
  type BankAccount,
  type BankAccountType,
} from "../../lib/bankAccount";
import type { CreditAccount } from "../../lib/creditAccount";
import { useDebtData } from "../../lib/debtData";
import { formatUsd } from "../../lib/format";
import { formatBalanceAsOf, looksLikeDebt } from "../../lib/simplefin";
import {
  useSimpleFinData,
  type SimpleFinLinkTarget,
} from "../../lib/simplefinData";

function linkValue(
  source: SimpleFinAccount,
  accounts: BankAccount[],
  debtAccounts: CreditAccount[],
): string {
  const bank = accounts.find((a) => a.simplefinAccountKey === source.key);
  if (bank) return `acct:${bank.id}`;
  const debt = debtAccounts.find((a) => a.simplefinAccountKey === source.key);
  return debt ? `debt:${debt.id}` : "";
}

function targetFromValue(value: string): SimpleFinLinkTarget {
  if (value.startsWith("acct:")) {
    return { kind: "existing", accountId: value.slice("acct:".length) };
  }
  if (value.startsWith("debt:")) {
    return { kind: "debt", accountId: value.slice("debt:".length) };
  }
  if (value.startsWith("new:")) {
    return { kind: "new", type: value.slice("new:".length) as BankAccountType };
  }
  return { kind: "none" };
}

export function SimpleFinSection({ accounts }: { accounts: BankAccount[] }) {
  const { accounts: debtAccounts } = useDebtData();
  const {
    status,
    balances,
    loading,
    busy,
    error,
    connect,
    sync,
    disconnect,
    linkAccount,
  } = useSimpleFinData();
  const [token, setToken] = useState("");
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);

  const onConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await connect(token);
      setToken("");
    } catch {
      /* error shown from context */
    }
  };

  const onDisconnect = async () => {
    try {
      await disconnect();
      setConfirmDisconnect(false);
    } catch {
      /* error shown from context */
    }
  };

  return (
    <section className="card p-5 space-y-3">
      <div>
        <h2 className="font-medium text-body">Bank Sync (SimpleFIN)</h2>
        <p className="text-sm text-muted mt-1">
          Read-only balances from SimpleFIN Bridge. The access token stays
          encrypted on this server and is never sent to your browser or relay.
        </p>
      </div>

      {loading ? <p className="text-sm text-muted">Loading…</p> : null}
      {error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}

      {!loading && status && !status.connected ? (
        <form className="space-y-3" onSubmit={onConnect}>
          <ol className="text-sm text-muted list-decimal pl-5 space-y-1">
            <li>
              Create a setup token at{" "}
              <a
                href={status.create_token_url}
                target="_blank"
                rel="noreferrer"
                className="text-accent underline"
              >
                SimpleFIN Bridge
              </a>
              .
            </li>
            <li>Paste it below. Each token works once.</li>
          </ol>
          <textarea
            className="input font-mono text-xs min-h-[5rem]"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="SimpleFIN setup token"
            aria-label="SimpleFIN setup token"
            spellCheck={false}
            autoComplete="off"
          />
          <button
            type="submit"
            className="btn-primary"
            disabled={busy || !token.trim()}
          >
            {busy ? "Connecting…" : "Connect"}
          </button>
        </form>
      ) : null}

      {!loading && status?.connected ? (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-sm text-muted">
                {status.last_fetched_at_ms
                  ? `Last synced ${formatBalanceAsOf(status.last_fetched_at_ms)}`
                  : "Not synced yet"}
              </p>
              <p className="text-xs text-muted">
                SimpleFIN is asked at most once an hour (it allows about 24 a
                day); syncing sooner shows the saved result.
              </p>
            </div>
            <button
              type="button"
              className="btn-ghost text-sm py-1.5"
              onClick={() => void sync().catch(() => undefined)}
              disabled={busy}
            >
              {busy ? "Syncing…" : "Sync now"}
            </button>
          </div>

          {balances?.messages.length ? (
            <ul className="notice-panel p-3 text-sm space-y-1">
              {balances.messages.map((m, i) => (
                <li key={`${m.code}-${i}`}>{m.message}</li>
              ))}
            </ul>
          ) : null}

          {balances == null ? (
            <p className="text-sm text-muted">
              Sync to see your SimpleFIN accounts and link them.
            </p>
          ) : balances.accounts.length === 0 ? (
            <p className="text-sm text-muted">
              SimpleFIN returned no accounts. Add institutions in SimpleFIN
              Bridge, then sync again.
            </p>
          ) : (
            <>
              <ul className="divide-y divide-outline rounded-lg border border-outline">
                {balances.accounts.map((source) => {
                  const current = linkValue(source, accounts, debtAccounts);
                  const debtLike =
                    looksLikeDebt(source) && !current.startsWith("acct:");
                  return (
                  <li key={source.key} className="px-4 py-3 space-y-2">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-medium truncate">{source.name}</p>
                        <p className="text-xs text-muted truncate">
                          {source.institution}
                        </p>
                      </div>
                      <p className="money text-sm shrink-0">
                        {source.balance != null ? formatUsd(source.balance) : "—"}
                      </p>
                    </div>
                    <select
                      className="input text-sm"
                      aria-label={`Link ${source.name}`}
                      value={current}
                      disabled={busy}
                      onChange={(e) =>
                        void linkAccount(source, targetFromValue(e.target.value))
                      }
                    >
                      <option value="">Not linked</option>
                      {debtAccounts.length > 0 ? (
                        <optgroup label="Debt account (balance owed)">
                          {debtAccounts.map((a) => (
                            <option key={a.id} value={`debt:${a.id}`}>
                              {a.name}
                            </option>
                          ))}
                        </optgroup>
                      ) : null}
                      {!debtLike && accounts.length > 0 ? (
                        <optgroup label="Cash or investment account">
                          {accounts.map((a) => (
                            <option key={a.id} value={`acct:${a.id}`}>
                              {a.name}
                            </option>
                          ))}
                        </optgroup>
                      ) : null}
                      {!debtLike ? (
                        <optgroup label="Create new account">
                          {ALL_BANK_ACCOUNT_TYPES.map((t) => (
                            <option key={t} value={`new:${t}`}>
                              New {BANK_ACCOUNT_TYPE_LABELS[t].toLowerCase()} account
                            </option>
                          ))}
                        </optgroup>
                      ) : null}
                    </select>
                    {debtLike ? (
                      <p className="text-xs text-muted">
                        Negative balance — link this card or loan to its Debt
                        account.
                      </p>
                    ) : null}
                  </li>
                  );
                })}
              </ul>
              <p className="text-xs text-muted">
                Linking a card or loan only updates its balance owed. APR, due
                date, payments, and bills stay as you set them under Debt.
              </p>
            </>
          )}

          {confirmDisconnect ? (
            <div className="card-quiet p-3 space-y-2">
              <p className="text-sm">
                Disconnect SimpleFIN? Linked accounts keep their last balance.
                To fully revoke access, also disable the FiatLife token in
                SimpleFIN Bridge.
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="btn-ghost flex-1 text-sm"
                  onClick={() => setConfirmDisconnect(false)}
                  disabled={busy}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="btn-ghost flex-1 text-sm text-error"
                  onClick={() => void onDisconnect()}
                  disabled={busy}
                >
                  Disconnect
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              className="btn-ghost text-sm text-error"
              onClick={() => setConfirmDisconnect(true)}
              disabled={busy}
            >
              Disconnect SimpleFIN
            </button>
          )}
        </>
      ) : null}
    </section>
  );
}
