import { i18n } from './i18n'
import { sdk } from './sdk'
import {
  nostrRelayInterfaceId,
  nostrRelayInternalUrl,
  nostrRelayPackageId,
} from './relay'
import { uiPort } from './utils'

export const main = sdk.setupMain(async ({ effects }) => {
  console.info(i18n('Starting FiatLife'))

  const internalRelayUrl =
    (await sdk.serviceInterface
      .get(
        effects,
        { id: nostrRelayInterfaceId, packageId: nostrRelayPackageId },
        (i) => {
          const urls = i?.addressInfo?.format()
          if (!urls || urls.length === 0) return null
          return (
            urls.find((u) => u.startsWith('ws://')) ??
            urls.find((u) => u.startsWith('wss://')) ??
            urls[0]
          )
        },
      )
      .const()) ?? nostrRelayInternalUrl

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

  return sdk.Daemons.of(effects).addDaemon('primary', {
    subcontainer,
    exec: {
      command: ['/usr/local/bin/fiatlife-web'],
      env: {
        FL_INTERNAL_RELAY_URL: internalRelayUrl,
      },
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
