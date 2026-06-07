import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_1 = VersionInfo.of({
  version: '0.4.0:1',
  releaseNotes: {
    en_US:
      'Paycheck log highlights missing scheduled paychecks when your first payday of the year is configured.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
