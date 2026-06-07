# FiatLife

Personal finance tracker — bills, paycheck breakdowns, debt, and savings goals. Syncs through your own NIP-42 authenticated Nostr relay with NIP-44 encryption, using the **same keypair and relay as the Android app**.

## What this package does

- Stores your Nostr secret key (nsec) **encrypted at rest** on your Start9 server.
- Connects to your Nostr relay and syncs FiatLife kind `30078` app data and CypherLog kind `37004` subscriptions.
- Bills, paycheck, debt (with payoff planner), goals, settings, and company history — full web parity with Android.

## What this package does **not** do

- It does **not** run a Nostr relay — install **Nostr RS Relay** separately or use an external `wss://` relay.
- It does **not** include Blossom — bill/debt statement attachments are Android-only until Blossom is added.

## Setup

1. Install and configure a **Nostr relay** (same one your Android app uses).
2. Open the **Web UI** from StartOS.
3. On first launch:
   - Paste your Android **nsec**.
   - Choose a passphrase (encrypts the key on this server only).
   - Enter your relay URL, or rely on auto-detected `nostr-rs-relay` if installed on StartOS.
4. Unlock on each visit. Your data loads from the relay.

## Relay on StartOS

If **Nostr RS Relay** is installed, FiatLife auto-connects at `ws://nostr-rs-relay.startos:8080`. LAN `.local` URLs entered during setup are rewritten to this internal address inside the container.

For a different relay package, use **Settings** → internal URL from that package's interface panel (`ws://package.startos:PORT`).

## Security

- Passphrase required to unlock; nsec is zeroed on lock or idle timeout.
- Sessions use signed `HttpOnly` cookies over StartOS HTTPS.
