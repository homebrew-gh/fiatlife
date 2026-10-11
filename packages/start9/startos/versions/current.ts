import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

/** Latest wrapper revision. Bump `version` in place unless a data migration is required. */
export const current = VersionInfo.of({
  version: '0.4.0:18',
  releaseNotes: {
    en_US:
      'The service log now records each SimpleFIN sync: how many accounts came back, how old the bank data is, and any SimpleFIN error codes (never balances or account names). Settings shows when your bank last reported each SimpleFIN balance, and warns when SimpleFIN Bridge has had no new balances for a linked account in over two days, or when a linked account is missing from the latest sync. The Android app can now refresh SimpleFIN balances itself: the web app shares its SimpleFIN connection with your other devices, encrypted to your key on your relay, and both apps keep to one SimpleFIN check an hour. Open the web app once after updating so it can share the connection. Nostr RS Relay, Mempool and Maple Proxy now appear under Dependencies when installed. If Mempool is installed, the BTC price comes from your own Mempool, falling back to mempool.space and then Coinbase. Tabs are now Home, Spending, Bills and Accounts. Accounts shows your net worth above your credit cards and loans; Paycheck and Goals open from Home. Old Budget and Debt links redirect. Net worth page: cash, investments, bitcoin wallets at the live BTC price, and home equity minus debts, with each line expanding to show the accounts behind it. Bitcoin wallets can be marked as retirement (IRA). SimpleFIN accounts stay listed in Settings after reloads, locks, and restarts without using a sync. Optional SimpleFIN Bridge connection syncs checking, savings, retirement, and brokerage balances, and the balance owed on linked credit cards, loans, and mortgages. Logging a mortgage payment now reduces the balance by principal only. Paycheck taxes use 2025/2026 federal brackets, standard deductions and Social Security wage base, current state flat rates, and the overtime-premium deduction; health, HSA, FSA and transit deductions are excluded from Social Security and Medicare; projections use your recent paystub rates and count unlogged paydays.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
