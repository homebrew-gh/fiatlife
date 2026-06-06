import { sdk } from './sdk'

/** Optional relay — no hard gate; main.ts reads its interface when installed. */
export const setDependencies = sdk.setupDependencies(async () => ({}))
