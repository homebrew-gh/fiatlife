import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_4 = VersionInfo.of({
  version: '0.4.0:4',
  releaseNotes: {
    en_US:
      'New Budget tab for planning monthly spending against income. Mortgage calculator now estimates property tax from a rate, tracks home insurance, HOA, and PMI separately (with automatic PMI removal at 20% equity), and adds cash-to-close and debt-to-income affordability checks prefilled from your baseline paycheck income and tracked debt payments.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
