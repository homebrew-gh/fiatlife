# FiatLife — Nostr protocol (NIP) usage

This document describes how **FiatLife** uses the Nostr protocol for storing and syncing app data. It is intended for developers, integrators, and anyone building on the same relay data (e.g. [CypherLog](https://github.com/homebrew-gh/CypherLog) subscription integration).

## Overview

- **App data**: FiatLife stores bills, financial goals, and salary/paycheck configuration as **parameterized replaceable events** (NIP-33) of **kind 30078** (arbitrary app-specific data, NIP-78).
- **Encryption**: Payloads are **NIP-44** (v2) encrypted to the **user’s own public key** (self-encryption) so only the user’s clients can read them.
- **Identity**: Events are signed by the user’s Nostr key (local or via NIP-46/NIP-55). Same pubkey across devices gives seamless sync.
- **Deletions**: Deleted items are represented by a **tombstone** (replaceable event with `{"deleted":true}`) plus a **NIP-09** (kind 5) deletion event targeting the replaceable event’s `a` tag.

---

## Kind 30078: App-specific data

All FiatLife domain data (bills, goals, salary config) is published as **kind 30078** with:

- **Tags**: Exactly one `d` tag whose value is the **address** of the resource (see below). No other tags are required for the app data itself.
- **Content**: A **NIP-44** encrypted string. The **plaintext** before encryption is JSON. The ciphertext is encrypted to the **author’s pubkey** (self).

Relays and other clients see only kind, `d` tag, and ciphertext; they cannot read the payload without the user’s private key.

### `d` tag (address) conventions

FiatLife uses the following prefixes so one subscription can request all app data or a subset:

| Prefix              | Resource        | Example `d` value        | Notes                          |
|---------------------|-----------------|---------------------------|--------------------------------|
| `fiatlife/bill/`    | Bill            | `fiatlife/bill/<uuid>`    | One replaceable event per bill  |
| `fiatlife/goal/`    | Financial goal  | `fiatlife/goal/<uuid>`    | One replaceable event per goal |
| `fiatlife/salary`   | Salary config   | `fiatlife/salary`        | Single global config; no suffix|

- **Bills** and **goals** use a UUID after the prefix. Each bill/goal has a unique `d` tag; publishing a new event with the same `d` **replaces** the previous one (replaceable semantics).
- **Salary** uses a fixed `d` value `fiatlife/salary`; there is only one salary config per user.

---

## Encryption (NIP-44)

- **Algorithm**: NIP-44 v2 (XChaCha20-Poly1305).
- **Recipient**: The **same** pubkey as the event author (self-encryption). Encrypt with the user’s private key and their own pubkey so that only the user’s clients (with the same key) can decrypt.
- **Scope**: The entire JSON payload in the `content` field is encrypted. The `d` tag (and other structural tags) remain plaintext so relays can filter and replace by address.

Any client that has the user’s private key (or NIP-46 remote signing with decrypt capability) can decrypt and read the payload.

---

## Payload schemas (plaintext JSON)

The following are the **decrypted** JSON shapes. In practice they are serialized from Kotlin data classes; this documents the contract for integration or sync.

### Bill (`fiatlife/bill/<id>`)

Bills include one-off and recurring expenses (utilities, rent, subscriptions, credit card minimums, etc.). Optional `creditCardDetails` is used for credit card–style tracking (balance, APR, minimum payment rule).

- `id`, `name`, `amount`, `category`, `frequency`, `dueDay`, `autoPay`, `accountName`, `notes`
- `attachmentHashes`, `statementEntries` (list of `{ hash, addedAt, label }`)
- `paymentHistory` (list of `{ date, amount }`), `isPaid`, `lastPaidDate`
- `creditCardDetails` (optional): `currentBalance`, `apr`, `minimumPaymentType`, `minimumPaymentValue`, `interestChargedLastPeriod`
- `createdAt`, `updatedAt`

Category and frequency are string/enum values (e.g. `MONTHLY`, `CREDIT_CARD`); see app domain model for allowed values.

### Financial goal (`fiatlife/goal/<id>`)

- `id`, `name`, `targetAmount`, `currentAmount`, `category`, `deadline` (optional), `createdAt`, `updatedAt`

### Salary config (`fiatlife/salary`)

Single object: hourly rate, pay frequency, standard/OT hours, tax settings, pre/post-tax deductions, direct deposits, etc. Full schema is the `SalaryConfig` model (see codebase).

---

## Subscriptions (how FiatLife reads from the relay)

- **Bills**: Subscribe with `authors = [user_pubkey]`, `kinds = [30078]`, `#d` filter: prefix `fiatlife/bill/` (or fetch all 30078 and filter client-side).
- **Goals**: Same, with `#d` prefix `fiatlife/goal/`.
- **Salary**: Same, with `#d` exact value `fiatlife/salary`.

On receipt, FiatLife decrypts the `content` with the user’s key, parses the JSON, and merges into local storage (Room DB). Replaceable events are merged by `d` tag (latest `created_at` wins if multiple are received).

---

## Deletions

When the user deletes a bill or goal:

1. **Tombstone**: Publish a new kind 30078 replaceable event with the **same** `d` tag and content `{"deleted":true}` (encrypted). This overwrites the previous event so other clients that only read replaceable events see “deleted.”
2. **NIP-09**: Publish a **kind 5** deletion event whose `e` tags reference the replaceable event(s) to be deleted, or use the `a` tag (kind:pubkey:d) so relays can prune the replaceable event. FiatLife uses the `a` tag form for parameterized replaceable events.

Clients that support NIP-09 will see the deletion request; clients that only subscribe to kind 30078 will see the tombstone and can remove the item locally when they decrypt `{"deleted":true}`.

---

## Authentication and signing

- **Relay auth**: FiatLife uses **NIP-42** when the relay requests authentication (e.g. `AUTH` challenge). The same Nostr key used for app data is used to sign the auth challenge.
- **Signing**: Events are signed by the user’s key (local or via Amber/NIP-55, or NIP-46 bunker). No NIP-26 delegation is required for normal app use; see [docs/AGENT_SIGNING_AND_CREDIT_LOANS.md](docs/AGENT_SIGNING_AND_CREDIT_LOANS.md) for agent/delegation options.

---

## Blossom (file storage)

FiatLife uses **Blossom** (BUD-01) for attaching files (e.g. bill statements). Upload/download is authenticated with the same Nostr key; the protocol is separate from kind 30078. Bill and (future) credit-account payloads store references to Blossom content (e.g. hashes or `statementEntries`) rather than raw file data in the event content.

---

## References

- [NIP-78](https://nips.nostr.com/78) — Arbitrary custom app data (kind 30078)
- [NIP-33](https://nips.nostr.com/33) — Parameterized replaceable events
- [NIP-44](https://nips.nostr.com/44) — Encrypted payloads (v2)
- [NIP-09](https://nips.nostr.com/9) — Event deletion (kind 5)
- [NIP-42](https://nips.nostr.com/42) — Relay authentication
- [CypherLog NIP.md](https://github.com/homebrew-gh/CypherLog/blob/main/NIP.md) — Subscription kind 37004 and other kinds (for cross-app integration)
