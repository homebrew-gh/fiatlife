import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_6 = VersionInfo.of({
  version: '0.4.0:6',
  releaseNotes: {
    en_US:
      'Home now leads with leftover cash, bills due soon, and shortcuts to paycheck, budget, debt, and housing. Live mortgages use PITI so escrow and linked housing bills stay in sync.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
