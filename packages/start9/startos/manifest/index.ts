import { setupManifest } from '@start9labs/start-sdk'
import { long, short } from './i18n'

export const manifest = setupManifest({
  id: 'fiatlife',
  title: 'FiatLife',
  license: 'MIT',
  packageRepo: 'https://github.com/samcornwell/fiatlife',
  upstreamRepo: 'https://github.com/samcornwell/fiatlife',
  marketingUrl: 'https://github.com/samcornwell/fiatlife',
  donationUrl: null,
  description: { short, long },
  volumes: ['main'],
  images: {
    main: {
      source: {
        dockerBuild: {
          workdir: '../../apps/web',
        },
      },
      arch: ['x86_64', 'aarch64'],
    },
  },
  alerts: {
    install: null,
    update: null,
    uninstall: null,
    restore: null,
    start: null,
    stop: null,
  },
  dependencies: {
    'nostr-rs-relay': {
      description:
        'Recommended local Nostr relay — use the same relay as your Android FiatLife app.',
      optional: true,
      s9pk: null,
      metadata: {
        title: 'Nostr RS Relay',
        icon: '../icon.svg',
      },
    },
  },
})
