import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_0_0 = VersionInfo.of({
  version: '0.1.0:0',
  releaseNotes: {
    en_US:
      'Initial StartOS package: web UI with relay sync, dashboard event counts, and green dollar theme. Bills, paycheck, debt, and goals screens are placeholders.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
