import type { SimpleFinAccount } from "./api";
import type { BankAccount, BankAccountType } from "./bankAccount";
import type { CreditAccount } from "./creditAccount";

/** Auto-sync on app open when the last fetch is older than this. */
export const SIMPLEFIN_AUTO_SYNC_MS = 6 * 60 * 60 * 1000;

/** Copy SimpleFIN's balance fields onto a FiatLife account. */
export function withSimpleFinBalance(
  account: BankAccount,
  source: SimpleFinAccount,
  now = Date.now(),
): BankAccount {
  return {
    ...account,
    simplefinAccountKey: source.key,
    balance: source.balance,
    availableBalance: source.available_balance,
    balanceAsOf: source.balance_date_ms,
    updatedAt: now,
  };
}

/** Linked accounts whose stored balance differs from the latest sync. */
export function accountsNeedingBalanceUpdate(
  accounts: BankAccount[],
  synced: SimpleFinAccount[],
  now = Date.now(),
): BankAccount[] {
  const byKey = new Map(synced.map((a) => [a.key, a]));
  const updates: BankAccount[] = [];
  for (const account of accounts) {
    const source = account.simplefinAccountKey
      ? byKey.get(account.simplefinAccountKey)
      : undefined;
    if (!source || source.balance == null) continue;
    if (
      account.balance === source.balance &&
      (account.availableBalance ?? null) === source.available_balance &&
      (account.balanceAsOf ?? null) === source.balance_date_ms
    ) {
      continue;
    }
    updates.push(withSimpleFinBalance(account, source, now));
  }
  return updates;
}

/** SimpleFIN reports what you owe on cards and loans as a negative balance. */
export function looksLikeDebt(source: SimpleFinAccount): boolean {
  return source.balance != null && source.balance < 0;
}

/**
 * Amount owed from a SimpleFIN balance. Uses the magnitude because not every
 * institution signs liabilities the same way.
 */
export function owedFromSimpleFin(balance: number): number {
  return Math.round(Math.abs(balance) * 100) / 100;
}

export function withSimpleFinDebtBalance(
  account: CreditAccount,
  source: SimpleFinAccount,
): CreditAccount {
  if (source.balance == null) {
    return { ...account, simplefinAccountKey: source.key };
  }
  const owed = owedFromSimpleFin(source.balance);
  return {
    ...account,
    simplefinAccountKey: source.key,
    currentBalance: owed,
    simplefinBalance: owed,
    simplefinBalanceAsOf: source.balance_date_ms,
  };
}

/**
 * Linked debt accounts whose SimpleFIN balance changed since the last sync.
 * A manual balance (e.g. a logged payment that hasn't posted) stands until the
 * institution reports a different amount.
 */
export function debtAccountsNeedingBalanceUpdate(
  accounts: CreditAccount[],
  synced: SimpleFinAccount[],
): CreditAccount[] {
  const byKey = new Map(synced.map((a) => [a.key, a]));
  const updates: CreditAccount[] = [];
  for (const account of accounts) {
    const source = account.simplefinAccountKey
      ? byKey.get(account.simplefinAccountKey)
      : undefined;
    if (!source || source.balance == null) continue;
    if (account.simplefinBalance === owedFromSimpleFin(source.balance)) continue;
    updates.push(withSimpleFinDebtBalance(account, source));
  }
  return updates;
}

export function newAccountFromSimpleFin(
  source: SimpleFinAccount,
  type: BankAccountType,
  now = Date.now(),
): BankAccount {
  return withSimpleFinBalance(
    { id: "", name: source.name || source.institution || "Account", type },
    source,
    now,
  );
}

export function formatBalanceAsOf(ms: number | null | undefined): string {
  if (!ms) return "";
  return new Date(ms).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}
