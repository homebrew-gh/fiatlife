import { T } from '@start9labs/start-sdk'
import { mapleProxyInterfaceId, mapleProxyPackageId } from './maple'
import { mempoolInterfaceId, mempoolPackageId } from './mempool'
import { nostrRelayInterfaceId, nostrRelayPackageId } from './relay'
import { sdk } from './sdk'

/**
 * Optional integrations are only reported once installed, so StartOS lists them without
 * flagging an uninstalled one as missing. Only the relay must be running: FiatLife falls
 * back to public BTC prices without Mempool, and doesn't call Maple yet.
 */
export const setDependencies = sdk.setupDependencies(async ({ effects }) => {
  const installed = async (packageId: string, id: string) =>
    (await sdk.serviceInterface
      .get(effects, { id, packageId }, (i) => !!i)
      .const()) ?? false

  const deps: T.CurrentDependenciesResult<any> = {}
  if (await installed(nostrRelayPackageId, nostrRelayInterfaceId)) {
    deps[nostrRelayPackageId] = { kind: 'running', versionRange: '*', healthChecks: [] }
  }
  if (await installed(mempoolPackageId, mempoolInterfaceId)) {
    deps[mempoolPackageId] = { kind: 'exists', versionRange: '*' }
  }
  if (await installed(mapleProxyPackageId, mapleProxyInterfaceId)) {
    deps[mapleProxyPackageId] = { kind: 'exists', versionRange: '*' }
  }
  return deps
})
