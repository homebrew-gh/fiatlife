import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_4_0_0 = VersionInfo.of({
  version: '0.4.0:0',
  releaseNotes: {
    en_US:
      'Full web parity with Android: bills (including CypherLog subscriptions), paycheck with tax overrides and direct deposits, debt payoff insights and planner, goals, settings, and company history. Syncs via your existing Nostr relay.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
