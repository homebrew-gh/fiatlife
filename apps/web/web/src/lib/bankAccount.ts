/** Bank/asset account aligned with Android `domain/model/BankAccount.kt`. */

export type BankAccountType = "CHECKING" | "SAVINGS" | "RETIREMENT" | "INVESTMENT";

export const ALL_BANK_ACCOUNT_TYPES: BankAccountType[] = [
  "CHECKING",
  "SAVINGS",
  "RETIREMENT",
  "INVESTMENT",
];

export const BANK_ACCOUNT_TYPE_LABELS: Record<BankAccountType, string> = {
  CHECKING: "Checking",
  SAVINGS: "Savings",
  RETIREMENT: "Retirement",
  INVESTMENT: "Investment",
};

export type BankAccount = {
  id: string;
  name: string;
  /** Missing on records written before account types existed. */
  type?: BankAccountType;
  balance?: number | null;
  availableBalance?: number | null;
  /** When the institution reported `balance` (epoch ms). */
  balanceAsOf?: number | null;
  /** SimpleFIN `conn_id:account_id`; when set, balances come from sync. */
  simplefinAccountKey?: string | null;
  updatedAt?: number;
};

export const BANK_ACCOUNT_D_TAG_PREFIX = "fiatlife/settings/bank/";

export function bankAccountDTag(id: string): string {
  return `${BANK_ACCOUNT_D_TAG_PREFIX}${id}`;
}

export function newBankAccountId(): string {
  return crypto.randomUUID();
}

export function bankAccountType(account: BankAccount): BankAccountType {
  return account.type ?? "CHECKING";
}

/** Checking/savings can pay bills; retirement/investment accounts can't. */
export function isCashAccount(account: BankAccount): boolean {
  const type = bankAccountType(account);
  return type === "CHECKING" || type === "SAVINGS";
}

export function summarizeBankAccounts(accounts: BankAccount[]): {
  cash: number;
  invested: number;
  withBalanceCount: number;
} {
  let cash = 0;
  let invested = 0;
  let withBalanceCount = 0;
  for (const account of accounts) {
    if (account.balance == null) continue;
    withBalanceCount += 1;
    if (isCashAccount(account)) cash += account.balance;
    else invested += account.balance;
  }
  return { cash, invested, withBalanceCount };
}

function optionalNumber(value: unknown): number | null {
  if (value == null) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function parseType(value: unknown): BankAccountType | undefined {
  const raw = String(value ?? "").toUpperCase();
  return (ALL_BANK_ACCOUNT_TYPES as string[]).includes(raw)
    ? (raw as BankAccountType)
    : undefined;
}

export function parseBankAccountRecord(
  dTag: string,
  plaintext: string,
): BankAccount | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    if (parsed.deleted === true) return null;
    const id = String(parsed.id ?? dTag.replace(BANK_ACCOUNT_D_TAG_PREFIX, ""));
    const name = String(parsed.name ?? "").trim();
    if (!id || !name) return null;
    return {
      id,
      name,
      type: parseType(parsed.type),
      balance: optionalNumber(parsed.balance),
      availableBalance: optionalNumber(parsed.availableBalance),
      balanceAsOf: optionalNumber(parsed.balanceAsOf),
      simplefinAccountKey:
        typeof parsed.simplefinAccountKey === "string" && parsed.simplefinAccountKey
          ? parsed.simplefinAccountKey
          : null,
      updatedAt: optionalNumber(parsed.updatedAt) ?? 0,
    };
  } catch {
    return null;
  }
}

export function serializeBankAccount(account: BankAccount): string {
  return JSON.stringify(account);
}
