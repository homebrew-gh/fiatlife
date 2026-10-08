import { summarizeBankAccounts, type BankAccount } from "./bankAccount";
import { satsToUsd, totalSats, type BitcoinWallet } from "./bitcoin";
import { summarizeDebt, type CreditAccount } from "./creditAccount";

export type NetWorthSummary = {
  cash: number;
  invested: number;
  bitcoinSats: number;
  /** Null when there are wallets but no BTC price yet. */
  bitcoinUsd: number | null;
  /** Purchase price of homes with a mortgage on file (offsets the mortgage debt). */
  homeValue: number;
  /** Balance owed on mortgages that have a home price. */
  mortgageDebt: number;
  /** homeValue − mortgageDebt. */
  homeEquity: number;
  /** All debt except mortgages counted in homeEquity. */
  otherDebt: number;
  /** A mortgage with a balance but no home price, so it counts only as debt. */
  missingHomePrice: boolean;
  debt: number;
  assets: number;
  total: number;
  hasData: boolean;
};

export function computeNetWorth(input: {
  bankAccounts: BankAccount[];
  wallets: BitcoinWallet[];
  usdPerBtc: number | null;
  creditAccounts: CreditAccount[];
}): NetWorthSummary {
  const { cash, invested, withBalanceCount } = summarizeBankAccounts(
    input.bankAccounts,
  );
  const bitcoinSats = totalSats(input.wallets);
  const bitcoinUsd =
    input.usdPerBtc != null
      ? satsToUsd(bitcoinSats, input.usdPerBtc)
      : bitcoinSats === 0
        ? 0
        : null;
  const mortgages = input.creditAccounts.filter((a) => a.type === "MORTGAGE");
  const priced = mortgages.filter((a) => (a.homePrice ?? 0) > 0);
  const homeValue = priced.reduce((sum, a) => sum + (a.homePrice ?? 0), 0);
  const mortgageDebt = priced.reduce(
    (sum, a) => sum + Math.max(0, a.currentBalance),
    0,
  );
  const debt = summarizeDebt(input.creditAccounts).totalDebt;
  const assets = cash + invested + (bitcoinUsd ?? 0) + homeValue;
  return {
    cash,
    invested,
    bitcoinSats,
    bitcoinUsd,
    homeValue,
    mortgageDebt,
    homeEquity: homeValue - mortgageDebt,
    otherDebt: debt - mortgageDebt,
    missingHomePrice: mortgages.some(
      (a) => !((a.homePrice ?? 0) > 0) && a.currentBalance > 0,
    ),
    debt,
    assets,
    total: assets - debt,
    hasData:
      withBalanceCount > 0 || input.wallets.length > 0 || input.creditAccounts.length > 0,
  };
}
