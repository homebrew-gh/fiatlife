/** StartOS Nostr RS Relay package (optional dependency). */
export const nostrRelayPackageId = 'nostr-rs-relay'
export const nostrRelayInterfaceId = 'relay'
export const nostrRelayInternalPort = 8080
export const nostrRelayInternalUrl = `ws://${nostrRelayPackageId}.startos:${nostrRelayInternalPort}`
