import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_3 = VersionInfo.of({
  version: '0.4.0:3',
  releaseNotes: {
    en_US:
      'Logged paychecks auto-sync to the relay. Toast confirms when events are delivered. Summary/What-if paycheck layout matches Android.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
