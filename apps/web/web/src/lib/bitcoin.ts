/** Manually-tracked bitcoin wallet (e.g. "Cold storage"). Amount only — no keys or xpubs. */

export const BITCOIN_WALLET_D_TAG_PREFIX = "fiatlife/btc/";

export const SATS_PER_BTC = 100_000_000;

export type BitcoinWallet = {
  id: string;
  name: string;
  /** Whole satoshis, so amounts never pick up float rounding. */
  sats: number;
  notes?: string;
  /** Held in a retirement account (e.g. a bitcoin IRA). Still counted once, under Bitcoin. */
  retirement?: boolean;
  createdAt: number;
  /** When the amount was last entered. */
  updatedAt: number;
};

export function bitcoinWalletDTag(id: string): string {
  return `${BITCOIN_WALLET_D_TAG_PREFIX}${id}`;
}

export function newBitcoinWalletId(): string {
  return crypto.randomUUID();
}

/**
 * Parse a user-entered BTC amount ("0.5", "1,000.12345678") into sats.
 * Returns null for anything that isn't a non-negative amount with ≤ 8 decimals.
 */
export function parseBtcToSats(input: string): number | null {
  const text = input.trim().replace(/,/g, "").replace(/\s*btc$/i, "");
  const match = /^(\d*)(?:\.(\d{0,8}))?$/.exec(text);
  if (!match || (match[1] === "" && !match[2])) return null;
  const whole = Number(match[1] || "0");
  const fraction = Number((match[2] ?? "").padEnd(8, "0"));
  const sats = whole * SATS_PER_BTC + fraction;
  return Number.isSafeInteger(sats) ? sats : null;
}

/** "0.5", "1.23456789" — trailing zeros trimmed. */
export function formatBtc(sats: number): string {
  const negative = sats < 0;
  const abs = Math.abs(Math.round(sats));
  const whole = Math.floor(abs / SATS_PER_BTC);
  const fraction = String(abs % SATS_PER_BTC)
    .padStart(8, "0")
    .replace(/0+$/, "");
  const body = fraction
    ? `${whole.toLocaleString("en-US")}.${fraction}`
    : whole.toLocaleString("en-US");
  return `${negative ? "-" : ""}${body}`;
}

export function satsToUsd(sats: number, usdPerBtc: number): number {
  return (sats / SATS_PER_BTC) * usdPerBtc;
}

export function totalSats(wallets: BitcoinWallet[]): number {
  return wallets.reduce((sum, w) => sum + w.sats, 0);
}

export function walletDetail(wallet: BitcoinWallet): string {
  return `${formatBtc(wallet.sats)} BTC${wallet.retirement ? " · Retirement" : ""}`;
}

export function parseBitcoinWalletRecord(
  dTag: string,
  plaintext: string,
): BitcoinWallet | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    if (parsed.deleted === true) return null;
    const id = String(parsed.id ?? dTag.replace(BITCOIN_WALLET_D_TAG_PREFIX, ""));
    const name = String(parsed.name ?? "").trim();
    const sats = Number(parsed.sats ?? 0);
    if (!id || !name || !Number.isFinite(sats)) return null;
    return {
      id,
      name,
      sats: Math.round(sats),
      notes: parsed.notes != null ? String(parsed.notes) : "",
      retirement: parsed.retirement === true,
      createdAt: Number(parsed.createdAt ?? 0),
      updatedAt: Number(parsed.updatedAt ?? 0),
    };
  } catch {
    return null;
  }
}

export function serializeBitcoinWallet(wallet: BitcoinWallet): string {
  return JSON.stringify(wallet);
}
