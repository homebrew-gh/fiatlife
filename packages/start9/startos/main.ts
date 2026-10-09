import { i18n } from './i18n'
import {
  mempoolInterfaceId,
  mempoolInternalUrl,
  mempoolPackageId,
} from './mempool'
import { sdk } from './sdk'
import {
  nostrRelayInterfaceId,
  nostrRelayInternalUrl,
  nostrRelayPackageId,
} from './relay'
import { uiPort } from './utils'

function relayUrlsFromInterface(urls: string[] | null | undefined) {
  const internal =
    urls?.find((u) => u.startsWith('ws://') && u.includes('.startos')) ??
    urls?.find((u) => u.startsWith('ws://')) ??
    nostrRelayInternalUrl
  const suggested = urls?.find((u) => u.startsWith('wss://')) ?? null
  return { internal, suggested }
}

export const main = sdk.setupMain(async ({ effects }) => {
  console.info(i18n('Starting FiatLife'))

  const formattedUrls =
    (await sdk.serviceInterface
      .get(
        effects,
        { id: nostrRelayInterfaceId, packageId: nostrRelayPackageId },
        (i) => i?.addressInfo?.format() ?? null,
      )
      .const()) ?? null

  const { internal: internalRelayUrl, suggested: suggestedRelayUrl } =
    relayUrlsFromInterface(formattedUrls)

  const subcontainer = await sdk.SubContainer.of(
    effects,
    { imageId: 'main' },
    sdk.Mounts.of().mountVolume({
      volumeId: 'main',
      subpath: null,
      mountpoint: '/data',
      readonly: false,
    }),
    'fiatlife-sub',
  )

  const env: Record<string, string> = {
    FL_INTERNAL_RELAY_URL: internalRelayUrl,
  }
  if (suggestedRelayUrl) {
    env.FL_SUGGESTED_RELAY_URL = suggestedRelayUrl
  }

  // Only set when Mempool is installed, so the server doesn't wait on a dead host.
  const mempoolUrls =
    (await sdk.serviceInterface
      .get(
        effects,
        { id: mempoolInterfaceId, packageId: mempoolPackageId },
        (i) => i?.addressInfo?.format() ?? null,
      )
      .const()) ?? null
  if (mempoolUrls) {
    env.FL_MEMPOOL_URL =
      mempoolUrls.find((u) => u.startsWith('http://') && u.includes('.startos')) ??
      mempoolInternalUrl
  }

  return sdk.Daemons.of(effects).addDaemon('primary', {
    subcontainer,
    exec: {
      command: ['/usr/local/bin/fiatlife-web'],
      env,
    },
    ready: {
      display: i18n('Web UI'),
      fn: () =>
        sdk.healthCheck.checkPortListening(effects, uiPort, {
          successMessage: i18n('The FiatLife web UI is ready'),
          errorMessage: i18n('The FiatLife web UI is not ready'),
        }),
    },
    requires: [],
  })
})
