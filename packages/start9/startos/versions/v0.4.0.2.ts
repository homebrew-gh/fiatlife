import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_2 = VersionInfo.of({
  version: '0.4.0:2',
  releaseNotes: {
    en_US:
      'Paycheck Summary now projects annual income from logged paychecks. Calculator and Annual tabs are combined into What-if for hypothetical modeling. Improved paycheck schedule reconciliation and tax override inputs.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
