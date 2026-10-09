import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

/** Latest wrapper revision. Bump `version` in place unless a data migration is required. */
export const current = VersionInfo.of({
  version: '0.4.0:13',
  releaseNotes: {
    en_US:
      'Tabs are now Home, Spending, Bills and Accounts. Accounts shows your net worth above your credit cards and loans; Paycheck and Goals open from Home. Old Budget and Debt links redirect. Net worth page: cash, investments, bitcoin wallets at the live BTC price, and home equity minus debts, with each line expanding to show the accounts behind it. Bitcoin wallets can be marked as retirement (IRA). SimpleFIN accounts stay listed in Settings after reloads, locks, and restarts without using a sync. Optional SimpleFIN Bridge connection syncs checking, savings, retirement, and brokerage balances, and the balance owed on linked credit cards, loans, and mortgages. Logging a mortgage payment now reduces the balance by principal only. Paycheck taxes use 2025/2026 federal brackets, standard deductions and Social Security wage base, current state flat rates, and the overtime-premium deduction; health, HSA, FSA and transit deductions are excluded from Social Security and Medicare; projections use your recent paystub rates and count unlogged paydays.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
