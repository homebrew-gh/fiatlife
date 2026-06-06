/** Bank payment account aligned with Android `domain/model/BankAccount.kt`. */

export type BankAccount = {
  id: string;
  name: string;
};

export const BANK_ACCOUNT_D_TAG_PREFIX = "fiatlife/settings/bank/";

export function bankAccountDTag(id: string): string {
  return `${BANK_ACCOUNT_D_TAG_PREFIX}${id}`;
}

export function newBankAccountId(): string {
  return crypto.randomUUID();
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
    return { id, name };
  } catch {
    return null;
  }
}

export function serializeBankAccount(account: BankAccount): string {
  return JSON.stringify(account);
}
