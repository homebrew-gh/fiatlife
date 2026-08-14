import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_5 = VersionInfo.of({
  version: '0.4.0:5',
  releaseNotes: {
    en_US:
      'Mortgage calculator uses conservative base take-home pay (regular pay only, typical two-paycheck month) for affordability checks, with configurable comfort thresholds and utilities included in take-home budget mode.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
