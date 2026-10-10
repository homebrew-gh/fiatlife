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
        icon: '../assets/nostr-rs-relay.svg',
      },
    },
    mempool: {
      description:
        'Optional — when installed, FiatLife gets the BTC price from your own Mempool instead of mempool.space.',
      optional: true,
      s9pk: null,
      metadata: {
        title: 'Mempool',
        icon: '../assets/mempool.svg',
      },
    },
    'maple-proxy': {
      description:
        'Optional — planned AI provider for transaction categorization and the financial planner. Not used yet. Prompts leave this server for the Maple enclave and are billed to your Maple account.',
      optional: true,
      s9pk: null,
      metadata: {
        title: 'Maple Proxy',
        icon: '../assets/maple-proxy.svg',
      },
    },
  },
})
