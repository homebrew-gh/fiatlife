# AI agent signing workflow (credit/loans and app data)

This document covers how an **AI agent** could add or edit events (e.g. credit/loan account data) on the user’s behalf so that FiatLife shows updated information after the agent parses a statement. It applies to kind 30078 app data and, in principle, to any replaceable app data the agent might update. The focus is **signing**: the agent cannot hold the user’s main private key, so we need a delegation or remote-signing flow.

**Context:** In the Credit & Loans tab, users can attach statements to each account (Blossom). A future workflow could let an agent parse those statements (or ones the user forwards), extract balance/APR/payments/etc., and publish updated credit/loan events to the relay (and optionally upload the statement to Blossom). When the user opens FiatLife, the app would sync and show the updated data. The hard part is how the agent is allowed to sign (or get events signed) for the user.

---

## 1. Desired workflow

- User (or an automated step) makes a statement available to an **AI agent** (e.g. email forward, drop in a folder, or the agent monitors a mailbox).
- The agent **parses** the statement (OCR + extraction or structured data), infers updates: balance, APR, minimum due, payments, next due date, etc.
- The agent **publishes** those updates to the user’s Nostr relay (and optionally uploads the statement to Blossom on the user’s behalf).
- The **next time the user opens FiatLife**, the app syncs from the relay and presents the updated credit/loan data without the user having to type it in.

The constraint: today, only the user’s key (or Amber) can publish and update kind 30078 events and upload to Blossom. An agent running elsewhere does not (and should not) hold the user’s main private key.

---

## 2. NIP-26 delegated event signing

**NIP-26** defines **delegated event signing**. A user (delegator) authorizes another keypair (delegatee) to sign events on their behalf within **conditions**:

- **Delegation tag** on the event: `["delegation", <delegator_pubkey>, <conditions>, <delegation_token>]`.
- **Conditions** are a query string, e.g.:
  - `kind=30078` — delegatee may only sign events of that kind (app data).
  - `created_at>T1&created_at<T2` — only events in that time window.
- **Delegation token**: a Schnorr signature by the **delegator** over `nostr:delegation:<delegatee_pubkey>:<conditions>`. The delegatee does not learn the delegator’s private key; the delegator creates this token once and gives it (with the conditions) to the delegatee.
- The **delegatee** signs the event with its **own** private key and includes the delegation tag. Relays and clients that support NIP-26 can verify: (1) the event is signed by the delegatee, (2) the delegation token is a valid signature from the delegator for that delegatee and those conditions. The event is then treated as authorized by the delegator (e.g. for replaceable event `d` tags, it’s still “the user’s” event).

So in theory:

1. **User** (in FiatLife or in a companion flow, e.g. cypherlog) creates a **NIP-26 delegation** from their root pubkey to the **agent’s pubkey**, with conditions such as: `kind=30078&created_at>now&created_at<now+90days` (or a rolling window the user renews).
2. **Agent** holds the delegatee keypair and the delegation token + conditions. When the agent has new statement-derived data, it builds a kind 30078 replaceable event (same structure as FiatLife today: encrypted payload, `d` = credit account id, etc.), signs with the **delegatee** key, and adds the delegation tag.
3. **Relay** receives the event; if the relay supports NIP-26, it accepts it and stores it; when the user’s client subscribes to their pubkey’s events, it may receive events that are signed by a delegatee but carry a valid delegation from the user.
4. **FiatLife app** today only considers events whose `pubkey` equals the user’s pubkey. To support the agent workflow, the app would need to **accept events that have a valid NIP-26 delegation tag** from the user’s pubkey to the event’s signer (delegatee), and treat those events as if they were from the user (merge into local state, overwrite replaceable events by `d` tag, etc.). Verification would require: (a) parsing the delegation tag, (b) checking `conditions` (kind, created_at), (c) verifying the delegation token (Schnorr verify with delegator pubkey). Only then would the app trust the event.

This gives a **constrained** way for the agent to update the user’s data: the agent can only publish kind 30078 (and whatever other kinds you add to conditions) within the time window. The user never gives the agent their root key; they only issue a time- and kind-limited delegation.

### 2.1 Cypherlog and reusing a sub-key flow

A flow where a **sub-key** is issued using NIP-26 so that a future agent could update events fits the above:

- Generate or designate an “agent” keypair (sub-key), create a NIP-26 delegation from the user’s root key to that sub-key with conditions (e.g. kind and time), and have the agent (or a future service) hold the sub-key and the delegation token so it can sign events that relays and clients accept as authorized by the user.

For FiatLife:

- **Option A — FiatLife creates the delegation**: A settings or “Agent access” flow could let the user “Issue agent key”: generate a new keypair (or import a known agent pubkey), choose conditions (e.g. kind=30078, 30-day window), sign the delegation string with the user’s key (Amber or local), and export or send to the agent the delegatee private key (if FiatLife generated it), delegation token, and conditions. The agent then uses these to publish updates.
- **Option B — Cypherlog (or another app) creates the delegation**: User creates the delegation in cypherlog to an agent pubkey; the agent has the matching private key and the token. FiatLife doesn’t need to issue delegations; it only needs to **accept** events that have a valid delegation from the user’s pubkey (when syncing from the relay). So FiatLife would add NIP-26 verification on read; the agent could be entirely external.

Either way, the **critical piece for FiatLife** is: when consuming events (e.g. in `syncFromNostr`), treat events signed by a delegatee with a valid NIP-26 delegation from the user’s pubkey (and passing the condition check) as valid app data and merge them.

### 2.2 Caveats and status of NIP-26

- NIP-26 is marked **“unrecommended”** in the NIPs repo (adds complexity and burden; when delegation is used broadly, clients that don’t implement it see “random” keys and broken identity). In practice it is still the only standard way for a **different key** to sign on behalf of the user without a bunker.
- **Relay support**: not all relays validate or store delegation tags; the app would need to work with relays that do, or accept that agent-updated events might only appear on some relays.
- **Key management**: with NIP-26, the agent’s (delegatee) private key must be kept secure; if compromised, the attacker can publish within the delegation’s conditions until the time bound. Short, renewable delegation windows limit exposure.

---

## 3. NIP-46 (remote signer / bunker) — often better than NIP-26 for agents

**NIP-46** (Nostr Connect / remote signing) describes a **bunker**: a service or daemon that holds the user’s private key and responds to **signing requests** from **clients** over Nostr (encrypted kind 24133). The client sends `sign_event` with an **unsigned** event (kind, content, tags, created_at); the bunker signs it with the **user’s key** and returns the **signed** event. The client then publishes that event to the relay. The event on the relay has the **user’s pubkey** and a normal signature — **no delegation tag, no NIP-26**.

For an **agent** adding/editing events:

1. The **agent** is a NIP-46 **client**: it has its own keypair (client identity) and knows the user’s **bunker URL** (relay + remote-signer pubkey, optional secret).
2. The **user** has configured their bunker (e.g. nsecBunker, self-hosted, or a signer app that supports NIP-46) to **allow** the agent’s client pubkey, with permissions such as `sign_event:30078` (and optionally a time window or other policy).
3. When the agent has new data (e.g. from parsing a statement), it builds an **unsigned** kind 30078 event, sends a `sign_event` request to the bunker, and the bunker (after checking policy) signs with the **user’s key** and returns the signed event.
4. The agent publishes that signed event to the relay. **FiatLife and every other client see a normal event from the user** — no NIP-26, no delegation parsing, no app changes.

### 3.1 NIP-26 vs NIP-46 for agents

| Aspect | NIP-26 (delegated signing) | NIP-46 (bunker / remote signer) |
|--------|----------------------------|----------------------------------|
| **Event shape** | Event signed by **delegatee**; includes delegation tag; some relays/clients don’t handle it. | Event signed by **user’s key**; identical to user-authored events; no client changes. |
| **NIP status** | Marked **“unrecommended”** (adds burden; when used widely, non-implementing clients see broken identity). | NIP-46 is a current, supported approach for key isolation. |
| **Where the key lives** | User’s key never leaves user; delegatee has a **sub-key** + delegation token. | User’s key lives in the **bunker**; agent never sees it; agent only gets back signed events. |
| **Agent needs** | Agent holds delegatee private key + delegation token; can sign without talking to user. | Agent must be able to **reach the bunker** when it runs (network, bunker online). |
| **Revocation** | Expires by time condition; or rotate delegatee key. | Bunker revokes the client pubkey; no new signatures for that agent. |
| **FiatLife changes** | App must **verify NIP-26** and accept delegated events in sync. | **None** — events are normal user events. |

**When to use which:**

- **Prefer NIP-46 (bunker)** when: the user can run or use a remote signer (nsecBunker, Nostria, self-hosted, etc.) and the agent can call it when it needs to publish. Then the agent is “just another NIP-46 client” with limited permissions; events are standard; no NIP-26 or app changes.
- **Use NIP-26** when: the agent must be able to publish **without** the user’s key or a bunker being reachable (e.g. fully offline agent with a pre-issued delegation, or no bunker in the user’s setup). Then FiatLife (and relays) must support delegation.

---

## 4. Blossom (statement upload by agent)

If the agent should also **upload statements** to Blossom on the user’s behalf:

- Blossom (BUD-01) uploads are typically signed by the user’s key. For the agent to upload, either:
  - **Blossom supports NIP-26**: the upload request includes a delegation tag and Blossom verifies it, then accepts the upload as belonging to the delegator’s “account”. This would be a Blossom spec/implementation question.
  - **Or** the agent doesn’t upload to Blossom; it only publishes to the relay a **reference** to a statement (e.g. “user uploaded this hash to Blossom elsewhere”) or the agent uploads to a different storage and puts a URL in the event. Then FiatLife could still show “statement from agent” if we add a convention for that.
  - **Or** a **proxy**: a small service that holds the user’s key (or uses Amber) and is called by the agent with the statement file; that service signs and uploads to Blossom. That reintroduces a trusted service with access to signing; NIP-26 avoids that for relay events but Blossom would need its own story.

So: **relay updates by an agent** are well aligned with NIP-26 or NIP-46. **Blossom uploads by an agent** are possible only if Blossom (or a proxy) supports a similar delegation or remote-signing model; otherwise they remain a separate design problem.

---

## 5. Implementation (planning only)

No code in this doc. Implementation would involve either:

- **NIP-46 path**: Agent as NIP-46 client; user configures bunker to allow agent’s pubkey with `sign_event:30078`; agent requests signatures and publishes; FiatLife unchanged.
- **NIP-26 path**: NIP-26 verification in the app’s Nostr sync path; optionally UI to create/export a delegation for an agent key; agent-side code to sign and publish kind 30078 with the delegation tag.

**Summary:** For “agent updates my credit/loan data from a statement,” **NIP-46 (bunker)** is the better default when the user has or is willing to use a bunker and the agent can reach it. **NIP-26** (e.g. cypherlog sub-key flow) is the option when the agent cannot or should not call a bunker. Blossom uploads by the agent would require either Blossom supporting one of these flows or a separate design (e.g. proxy or user-only Blossom uploads).
