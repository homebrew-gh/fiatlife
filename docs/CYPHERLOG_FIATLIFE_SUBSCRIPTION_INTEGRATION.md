# Feasibility: CypherLog ↔ FiatLife subscription / bills integration

This doc outlines whether and how **subscription** events from **[CypherLog](https://github.com/homebrew-gh/CypherLog)** (home management hub: appliances, vehicles, subscriptions, warranties, etc.) could be pulled into the **Bills** section of FiatLife, and whether updates could flow **both ways** (FiatLife ↔ CypherLog). No code—only feasibility and design thinking.

**CypherLog protocol reference:** CypherLog documents its Nostr event kinds and schemas in **[NIP.md](https://github.com/homebrew-gh/CypherLog/blob/main/NIP.md)**. Subscriptions use **kind 37004** (addressable). Each subscription has a `d` tag (UUID), and tags such as `name`, `subscription_type`, `cost`, `currency`, `billing_frequency`, `company_id` / `company_name`, `linked_asset_*`, `notes`. Optional **NIP-44** encryption is used for sensitive categories (content field). Billing frequencies include `weekly`, `monthly`, `quarterly`, `semi-annually`, `annually`, `one-time`. This gives a concrete schema to map into FiatLife’s bill model (name, amount, frequency, due day, etc.).

---

## 1. Goal

- **Pull**: FiatLife reads subscription-related events that Cypherlog publishes (or that both apps agree to use), and surfaces them in the Bills tab—e.g. as bills with category “Subscription” or a dedicated subset.
- **Bidirectional**: Changes in FiatLife (e.g. edit amount, due day, mark paid) show up in Cypherlog when it syncs; changes in Cypherlog (e.g. new subscription, updated price) show up in FiatLife when it syncs.
- **Single source of truth (optional)**: Ideally both apps converge on the same view of “this subscription” so the user doesn’t maintain two lists.

---

## 2. What has to be true for this to be feasible

### 2.1 Same identity (pubkey)

- Both apps must use the **same Nostr identity** (same pubkey) for the user. If FiatLife uses key A and Cypherlog uses key B, they are writing to different “accounts” and would need a separate linking mechanism (e.g. NIP-26, or one app writing on behalf of the other). **Feasibility is highest** when the user signs into both apps with the same key (or the same NIP-46 bunker), so both publish and subscribe as the same pubkey.

### 2.2 Shared or agreed event model

- **Option A — Same kind and schema**: Cypherlog and FiatLife both use the **same** event kind (e.g. 30078) and the **same** or **compatible** payload schema for “subscription” or “bill” items (e.g. same `d` tag convention, same JSON shape). Then each app can read what the other writes; no translation layer.
- **Option B — Different kinds, explicit mapping**: Cypherlog uses kind X for subscriptions; FiatLife uses kind 30078 for bills. Then integration requires either (i) one app **also** writing the other app’s format (dual-write), or (ii) a **bridge** (service or one of the apps) that subscribes to both, maps Cypherlog events into FiatLife’s bill model (or vice versa), and writes into the other app’s kind. FiatLife would then show “bills” that are either native (30078) or imported from Cypherlog (kind X → local bill representation).
- **Option C — Shared “subscriptions” kind**: The community (or the two apps) define a **shared** kind and schema for “subscriptions” (e.g. a dedicated kind under 30xxx). Cypherlog writes subscriptions there; FiatLife reads that kind and maps into the Bills UI, and can write back the same kind for updates. Then both apps are consumers and producers of the same event type.

Feasibility depends on which of these is realistic. If Cypherlog’s subscription model is closed or undocumented, we’re in Option B and need a clear mapping and a policy for who “owns” which events.

### 2.3 Encryption and readability

- FiatLife today encrypts app data (kind 30078) with **NIP-44** (and the user’s key). If Cypherlog’s subscription events are **unencrypted**, FiatLife could read them but would be displaying plaintext from the relay. If Cypherlog’s events are **encrypted**, FiatLife can only decrypt them if it uses the **same key** (same identity) and the **same encryption method** (e.g. NIP-44, same conventions). So: **same pubkey + same encryption** makes bidirectional read/write straightforward; different encryption or keys block it unless one app explicitly supports the other’s format.

### 2.4 Relay overlap

- Both apps need to **read from and write to at least one common relay** (or the user configures the same relay in both). If Cypherlog and FiatLife use disjoint relay sets, they never see each other’s events unless one app is configured to also use the other’s relays.

---

## 3. Pull-only: Cypherlog → FiatLife (subscriptions as bills)

**Feasibility: high if we know Cypherlog’s format.**

- FiatLife **subscribes** to the event kind(s) and author (user’s pubkey) that Cypherlog uses for subscriptions.
- For each such event, FiatLife **maps** the payload into its internal Bill (or a “subscription” subtype): name, amount, frequency, due day, etc. Missing fields can be defaulted or left optional.
- These appear in the Bills list (e.g. with a “From Cypherlog” or “Subscription” badge). FiatLife can treat them as **read-only** (no edit/delete in FiatLife, or edit only locally and not write back) to avoid conflicts until bidirectional sync is defined.
- **Requirements**: Documented or reverse-engineered Cypherlog subscription event kind + schema; same user pubkey and relay; encryption compatibility (or Cypherlog subscriptions in plaintext and we accept that).

---

## 4. Bidirectional: FiatLife ↔ Cypherlog

**Feasibility: medium to high if ownership and schema are agreed.**

- **Same event kind and schema (Option A or C)**: Both apps read and write the same events. When the user edits a “subscription” in FiatLife, FiatLife publishes an updated replaceable event (same `d` tag); when Cypherlog syncs, it sees the update. When the user adds/edits in Cypherlog, FiatLife sees the new/updated event and refreshes the Bills list. **Conflict resolution**: last-write-wins per `d` tag (replaceable semantics), or include `updated_at` and let both apps prefer the latest.
- **Different kinds (Option B)**: One app would need to **dual-write** (e.g. FiatLife writes both 30078 for “bills” and Cypherlog’s kind for “subscriptions” when the user creates/edits a subscription-type bill), or a bridge syncs between the two. Conflict resolution becomes harder (two sources of truth; need rules for which app “wins” for which field, or which app is authoritative for subscriptions).

**Key question**: Does Cypherlog **allow** external writes to “its” subscription events (same kind + `d` tag), or does it only accept edits from its own client? If it ignores events it didn’t create, FiatLife would need to write in Cypherlog’s exact format and `d` convention so Cypherlog treats them as its own.

---

## 5. Challenges and open questions

| Topic | Issue | Direction |
|------|--------|-----------|
| **Schema ownership** | Who defines the canonical “subscription” or “bill” shape? If each app has its own schema, we need a mapping and possibly a shared convention (e.g. a small shared schema both implement). | Agree with Cypherlog (or community) on a minimal shared schema, or document Cypherlog’s and map one-way. |
| **Identity** | Different keys per app breaks seamless sync. | Prefer same key (or same NIP-46 bunker) for both apps. |
| **Encryption** | Different NIP-44 usage or keys prevent cross-read. | Same key + same NIP-44 usage; or accept plaintext for a “subscriptions” kind if that’s the design. |
| **Conflict resolution** | User edits same subscription in both apps before sync. | Use replaceable events + `updated_at` and last-write-wins; or surface “modified in both” and let user choose. |
| **Scope** | FiatLife Bills include more than subscriptions (utilities, mortgage, etc.). Cypherlog may only care about subscriptions. | Define a clear subset: “subscriptions” = sync with Cypherlog; other bills = FiatLife-only (or future integration with other apps). |
| **Cypherlog as source of truth** | If Cypherlog is the canonical app for subscriptions, FiatLife might only **import** and show them read-only, or write back only with Cypherlog’s schema so Cypherlog remains the editor. | Decide whether FiatLife is a full peer or a consumer of Cypherlog subscription data. |

---

## 6. What we’d need from Cypherlog (or to decide)

To implement pull (Cypherlog → FiatLife):

- Event **kind** used for subscriptions (and any sub-types).
- **Schema**: required and optional fields (name, amount, currency, interval, next due, provider, etc.).
- **`d` tag** (or equivalent) convention for replaceable subscription events (so we can update the same “subscription” and Cypherlog can too).
- Whether events are **encrypted** (NIP-44 or other) and with which key (user’s pubkey?).

To implement bidirectional sync:

- Confirmation that Cypherlog **will accept and display** updates to subscription events that were created or updated by another client (FiatLife) as long as kind + `d` + schema match.
- Any **extra tags or fields** Cypherlog requires (e.g. “source” or “app”) so it doesn’t reject or overwrite FiatLife’s writes.

If Cypherlog is your own project, defining a small **shared contract** (kind + schema + `d` tag) and having both apps implement it gives the cleanest path. If Cypherlog is third-party, we’re limited to what it publishes and whether it documents or allows external writes.

---

## 7. Feasibility summary

| Scenario | Feasible? | Condition |
|----------|-----------|-----------|
| **Pull only (Cypherlog → FiatLife)** | Yes | Same identity + relay; known Cypherlog kind/schema; encryption compatible (or plaintext). FiatLife maps Cypherlog events into Bills (read-only or with local-only edits). |
| **Bidirectional (same kind/schema)** | Yes | Same identity, relay, and encryption; agreed kind + schema + `d` tag; Cypherlog accepts external writes to that kind. Both apps read/write the same events; last-write-wins or `updated_at`-based. |
| **Bidirectional (different kinds)** | Possible but heavier | One app dual-writes, or a bridge translates between Cypherlog’s kind and FiatLife’s 30078. More moving parts and conflict handling. |
| **Different identity per app** | Harder | Would need NIP-26 or a “linked account” model and one app writing on behalf of the other; more complexity. |

**Bottom line:** A cross-platform integration where **subscription events from CypherLog appear in FiatLife’s Bills** and **updates in either app show up in the other** is **feasible** if: (1) both use the same user pubkey and relay, (2) subscription events use a known (or agreed) kind and schema and encryption, and (3) CypherLog accepts events written by FiatLife (or we restrict to pull-only). With CypherLog’s [NIP.md](https://github.com/homebrew-gh/CypherLog/blob/main/NIP.md) (kind 37004, tag schema) known, the next step is to choose an interoperability method and implement it.

---

## 8. Recommended method: kind 37004 as single source of truth

Given CypherLog’s **kind 37004** (addressable, tag-based schema, optional NIP-44), the cleanest way to make the two apps interoperable is:

**Use kind 37004 as the single source of truth for “subscriptions that are relevant to CypherLog” (home-related).** FiatLife uses **kind 30078** for all other bills (utilities, rent, credit cards, etc.) and also for **subscriptions that are FiatLife-only** (e.g. Substack, news, software) so they don’t clutter CypherLog, which is home-focused. Only subscriptions the user marks as home-related (or “sync to CypherLog”) live in 37004 and appear in both apps.

### Why this is best

| Aspect | Benefit |
|--------|--------|
| **No dual-write** | Each subscription has one home: either 37004 (CypherLog + FiatLife) or 30078 (FiatLife only). No need to write both for the same item. |
| **CypherLog unchanged** | CypherLog already uses 37004. No change required on its side; FiatLife adopts CypherLog’s existing schema. |
| **Bidirectional by design** | For home-related subscriptions: replaceable 37004; whoever writes last overwrites by `d` tag. Both apps subscribe to 37004 and refresh. |
| **Clear scope** | FiatLife Bills = 30078 (all non-subscription bills + FiatLife-only subscriptions) + 37004 (home-related subscriptions, mapped into the same list). CypherLog only sees 37004, so it stays focused on home-relevant items. |

### How it works

1. **FiatLife subscribes to kind 37004** (same author = user pubkey, same relay(s)). For each 37004 event, FiatLife **maps** tags → internal “bill” representation and shows it in the Bills list (e.g. with a “CypherLog” or “Home” indicator).
2. **When the user adds or edits a subscription in FiatLife**, they choose whether it is **home-related** (show in CypherLog) or **FiatLife-only** (e.g. Substack, news, software):
   - **Home-related** → FiatLife **publishes 37004** only (no 30078). CypherLog syncs and shows it; FiatLife shows it from 37004.
   - **FiatLife-only** → FiatLife **creates/updates 30078** only (same as any other bill, category e.g. SUBSCRIPTION). CypherLog never sees it; FiatLife shows it in Bills for monthly budgeting.
3. **When the user adds or edits a subscription in CypherLog**, it publishes 37004 as it already does. FiatLife receives it, maps to Bill, and shows it (those are by definition home-related).
4. **Non-subscription bills** in FiatLife (utilities, rent, credit cards, etc.) stay as 30078 only; they do not appear in CypherLog.

### Subscriptions that don’t apply to CypherLog (FiatLife-only)

CypherLog’s main purpose is **home** management (appliances, vehicles, home subscriptions, warranties). Many subscriptions matter for **monthly bills** in FiatLife but are irrelevant to CypherLog (e.g. Substack, Patreon, news, SaaS tools). Pushing those into 37004 would clutter CypherLog with non-home data.

**Implementation in FiatLife:**

| Concept | Implementation |
|--------|------------------|
| **Two subscription “scopes”** | When creating or editing a bill of type “Subscription,” expose a choice: **“Show in CypherLog (home-related)”** vs **“FiatLife only.”** Default can be “FiatLife only” for safety, or “Show in CypherLog” if the user often tracks home subs. |
| **Storage** | **Show in CypherLog** → publish **37004** only; store locally for display from relay (or cache), no 30078. **FiatLife only** → create/update **30078** like any other bill (e.g. `category = SUBSCRIPTION`), no 37004. |
| **Single Bills list** | FiatLife’s Bills list merges 30078 (all bills, including FiatLife-only subscriptions) and 37004 (home-related subscriptions). User sees one list; optional badge or filter for “CypherLog” vs “FiatLife only.” |

**Changing scope (FiatLife only ↔ Show in CypherLog)**

When the user flips the “Show in CypherLog” / “FiatLife only” choice for an existing subscription, FiatLife must move the data from one kind to the other and remove the old representation so there is never two copies.

- **FiatLife only → Show in CypherLog**  
  The subscription currently lives only as a **30078** bill. To show it in CypherLog:
  1. **Publish a new 37004** with the same name, cost, billing_frequency, notes, etc., using CypherLog’s tag schema. Use a **new `d` tag** (new UUID), since this subscription did not previously exist as 37004.
  2. **Delete or tombstone the 30078** for this bill (so it no longer appears as a native bill and is not duplicated). FiatLife will then show this subscription only from the new 37004 (subscribed from relay).  
  Result: CypherLog will see the new 37004 and display the subscription; FiatLife shows it from 37004 in the merged Bills list.

- **Show in CypherLog → FiatLife only**  
  The subscription currently lives as **37004**. To make it FiatLife-only:
  1. **Create a 30078** bill with the same name, amount, frequency, notes, etc. (map from the 37004 tags).
  2. **Remove the 37004** from the relay view: either **NIP-09 delete** the 37004 event, or publish a **tombstone 37004** per CypherLog’s deletion convention (see CypherLog NIP.md).  
  Result: CypherLog will no longer show it; FiatLife shows it only as a 30078 bill.

No change is required in CypherLog: it only ever sees 37004, so it continues to show only home-related subscriptions. FiatLife is the place that decides which subscriptions are written to 37004 vs 30078.

### Field mapping (CypherLog 37004 ↔ FiatLife Bill)

| CypherLog 37004 (tags) | FiatLife Bill / UI |
|------------------------|--------------------|
| `d` | Unique id (use as bill id for 37004-sourced items, or store to match on replace) |
| `name` | `name` |
| `cost` | `amount` |
| `currency` | Assume USD or map; FiatLife typically one currency per user |
| `billing_frequency` | Map to FiatLife frequency: `weekly`→WEEKLY, `monthly`→MONTHLY, `quarterly`→QUARTERLY, `semi-annually`→SEMIANNUALLY, `annually`→ANNUALLY, `one-time`→e.g. ANNUALLY or separate handling |
| `subscription_type` | Map to FiatLife category (e.g. SUBSCRIPTION) or a sub-type for display |
| `company_name` (or company from 37003) | e.g. `accountName` or notes |
| `notes` | `notes` |
| — | `dueDay`: CypherLog doesn’t define due day; default (e.g. 1) or leave configurable in FiatLife when editing |

### Using billing_frequency so subscriptions don’t show as “due” when they’re not

**Kind 37004 does not need to be updated.** CypherLog already includes **`billing_frequency`** (weekly, monthly, quarterly, semi-annually, annually, one-time). FiatLife must:

1. **Map** `billing_frequency` into FiatLife’s internal frequency (e.g. MONTHLY, QUARTERLY, SEMIANNUALLY, ANNUALLY) when mapping 37004 → Bill.
2. **Use that frequency in due-date logic** when deciding “next due” and “is this subscription due this period?”  
   - If the subscription is **annual**, “next due” should be ~12 months from the last due (or start), not every month.  
   - If **semi-annual**, every 6 months; **quarterly**, every 3 months; **monthly**, every month (e.g. by `dueDay`).

If FiatLife ignored `billing_frequency` and treated every subscription as monthly, an annual subscription would incorrectly appear as due every month. So: **no change to the kind** — FiatLife should read `billing_frequency` from 37004, map it to the app’s frequency enum, and use it in the same “next due date” / “is paid” logic it already uses for native bills. Then monthly subs show due monthly, annual once a year, etc.

### Company, vehicle, and other linked tags — do they cause problems?

In CypherLog, kind 37004 subscriptions can **link** to other CypherLog entities via tags, e.g.:

- **`company_id`** — references a Company/Service Provider (kind 37003) by its `d` tag
- **`linked_asset_type`** / **`linked_asset_id`** / **`linked_asset_name`** — reference appliances (32627), vehicles (32628), or “home feature”

FiatLife does **not** store or resolve those kinds (no companies, vehicles, or appliances). That does **not** break FiatLife; it only means:

1. **Reading 37004**  
   FiatLife can’t “resolve” `company_id` or `linked_asset_id` to a human-readable name (that would require subscribing to 37003, 32627, 32628, etc.). For the Bills list that’s fine: show **name**, **cost**, **billing_frequency**, and if present **`company_name`** (manual tag). The subscription still shows up and is usable; the link is just opaque in FiatLife (e.g. “linked to company/vehicle” without resolving which one, or ignore for display).

2. **Editing in FiatLife and writing 37004 back**  
   **This is where it can cause a problem** if FiatLife is careless. When the user edits a subscription that **came from CypherLog** (and that had `company_id`, `linked_asset_*`, etc.), FiatLife must **preserve** those tags when it publishes the updated 37004. If FiatLife only sends the tags it knows (name, cost, billing_frequency, …) and **drops** `company_id` and `linked_asset_*`, then when CypherLog syncs it will see the subscription **no longer linked** to that company or vehicle. So: FiatLife should store the **full tag set** (or at least all tags it doesn’t semantically map) for 37004-sourced items and **round-trip** them on publish — i.e. when building the replacement 37004 event, include every tag from the original event that FiatLife didn’t explicitly change. Then links to companies/vehicles/appliances stay intact.

3. **Creating a new subscription in FiatLife**  
   FiatLife has no company or vehicle picker, so new subscriptions created in FiatLife will typically have **no** `company_id` or `linked_asset_*` tags. That’s acceptable: CypherLog treats those as optional, and the user can open CypherLog later to attach the subscription to a company or asset. No kind change needed.

**Summary:** Linked tags don’t require FiatLife to understand companies or vehicles. The only requirement is **preserve and re-emit** them when publishing 37004 updates so CypherLog doesn’t lose the links.

CypherLog’s optional **encrypted content** (NIP-44) for 37004: if CypherLog stores extra fields in encrypted content, FiatLife can still use the **tags** for the mapping above. If FiatLife writes 37004, it can use tags-only (no encrypted content) so CypherLog’s current tag-based reads still work; if CypherLog’s NIP.md specifies encrypted content for subscriptions, FiatLife would need to match that format when writing.

### Implementation outline (FiatLife side)

- **Subscribe**: In addition to existing 30078 subscription (bills), subscribe to `kinds = [37004]` for the same user. Merge 37004 events into the bills list with a flag or type (e.g. `source = CYPHERLOG` or `kind37004 = true`).
- **Map**: For each 37004 event, parse tags → Bill-like model (id from `d`, name, amount, frequency, etc.). Store or derive so the list can show “Bills + Subscriptions” in one place.
- **Display**: In Bills UI, show subscription items with a badge or filter; optional “Open in CypherLog” link (e.g. deep link or CypherLog web URL with context) if desired.
- **Create/Edit subscription in FiatLife**: When user creates a bill with type “Subscription,” show a form (name, cost, billing_frequency, etc.) and a choice: **“Show in CypherLog (home-related)”** vs **“FiatLife only.”** On save: if **Show in CypherLog** → **publish 37004** only (replaceable, `d` = new or existing UUID), tag set from CypherLog’s NIP.md; if **FiatLife only** → **create/update 30078** only (same as other bills, e.g. category SUBSCRIPTION). Never create both 30078 and 37004 for the same subscription.
- **Delete**: To delete a subscription from FiatLife, publish a 37004 replacement with CypherLog’s deletion convention if any (e.g. empty or tombstone tags), or NIP-09 delete the 37004 event; see CypherLog NIP.md for deletion behavior.

### Pitfalls and unforeseen consequences

These can cause one or both apps to fail, error, or show wrong data if not handled.

| Risk | What goes wrong | Mitigation |
|------|------------------|------------|
| **Duplicate creation** | User adds the same subscription in CypherLog, then (before sync) adds it again in FiatLife. Two 37004 events with different `d` values; both apps show two entries. No automatic merge. | Prefer creating a given subscription in only one app; or implement heuristic dedupe (e.g. same name + cost + frequency) and offer “merge/remove duplicate” in UI. |
| **Deletion asymmetry** | FiatLife deletes via NIP-09 (kind 5); CypherLog only hides subscriptions when it sees a tombstone 37004 (or vice versa). One app keeps showing an item the user deleted in the other. | Align deletion behavior with CypherLog: either both support NIP-09 for 37004, or both treat a specific 37004 tombstone as “deleted.” Confirm and document in both apps. |
| **Encrypted content mismatch** | CypherLog stores required or important fields only in NIP-44 encrypted content. FiatLife reads only tags and writes tags-only. CypherLog then shows incomplete/wrong data for FiatLife-edited events, or rejects them. | Confirm with CypherLog whether 37004 has required encrypted content and its shape. If yes, FiatLife must produce the same encrypted payload when writing 37004. |
| **Last-write-wins / lost edits** | User edits a subscription in CypherLog (e.g. cost), then in FiatLife (e.g. name) before either syncs. Replaceable semantics: the later publish overwrites the earlier; one edit is lost. | Accept as inherent to replaceable events, or add `updated_at` and show “modified elsewhere” when local copy is stale. Avoid editing the same subscription in both apps in quick succession. |
| **Schema / validation** | CypherLog validates 37004 (e.g. `cost` numeric, `name` non-empty, `billing_frequency` from enum). FiatLife sends an invalid or missing value → relay accepts but CypherLog errors, hides the event, or shows wrong data. | FiatLife must validate outgoing 37004 against CypherLog’s schema (from NIP.md) before publish. Preserve unknown tags when editing to avoid stripping future-required tags. |
| **Relay kind allowlist** | Some relays allow only certain kinds. If a relay used by both apps rejects kind 37004 on write, FiatLife’s publish fails to that relay; other relays may succeed. Result: partial visibility (e.g. CypherLog sees it, FiatLife doesn’t, or only on some relays). | Use relays that allow 37004, or handle partial write failure (retry other relays, surface “sync may be incomplete” if needed). |
| **Double-create in FiatLife** | Bug: when user creates a “subscription” in FiatLife, code path creates both a 30078 bill and a 37004. FiatLife then shows the same subscription twice (once as native bill, once as 37004). | Strict separation: subscription-type bills must **only** write 37004; never create 30078 for the same logical subscription. |
| **created_at / clock skew** | NIP-33 replaceable: latest `created_at` wins. If FiatLife publishes with a stale or past `created_at`, an older event could “win” and the user’s latest edit is overwritten. | FiatLife should set `created_at` to “now” when publishing 37004. Be aware of device clock skew in edge cases. |

### What to confirm with CypherLog

- That **replaceable** 37004 events with the same `d` tag are always replaced (standard NIP-33). No extra “version” or “owner” check that would reject FiatLife’s writes.
- Whether CypherLog uses **encrypted content** for 37004 and, if so, the exact JSON shape so FiatLife can write it when creating/editing from FiatLife.
- Whether CypherLog supports **NIP-09** deletion for 37004 (so FiatLife can send a kind 5 to remove a subscription and have CypherLog reflect that).

### Working on both projects and verifying interoperability

**Can Cursor work on both projects at once?** Yes. Open a **multi-root workspace** that includes both repos so the AI and you can see both codebases in one place:

1. In Cursor: **File → Add Folder to Workspace…** and add the other project (e.g. add `CypherLog` when you’re in `fiatlife`, or vice versa). Save the workspace (e.g. `fiatlife-cypherlog.code-workspace`) so you can reopen it.
2. Or clone both under one parent folder (e.g. `repos/fiatlife` and `repos/CypherLog`) and open the parent folder as the workspace. Then both projects are visible to Cursor.

With both in the same workspace, you can ask Cursor to e.g. “In FiatLife, implement publishing 37004 using the tag schema from CypherLog’s NIP.md in this workspace” and it can read CypherLog’s schema and generate compatible code.

**How to make sure interoperability is implemented properly:**

| Step | What to do |
|------|------------|
| **1. Single contract** | Treat **CypherLog’s NIP.md** (kind 37004, tags, enums) plus **this doc** (field mapping, pitfalls, confirmations) as the shared contract. Any change to 37004 in CypherLog should be reflected in NIP.md and, if it affects FiatLife, in this doc too. |
| **2. Implement against the contract** | When implementing in FiatLife: have Cursor (or yourself) read CypherLog’s NIP.md and this doc. Generate 37004 events and tag names/values that **exactly** match the schema (e.g. `billing_frequency` values: `weekly`, `monthly`, …, not custom strings). When implementing in CypherLog: ensure 37004 reads accept what FiatLife will write (tags-only, same tag keys). |
| **3. Interop checklist** | Keep a short checklist both sides must satisfy (e.g. “37004 tags: `d`, `name`, `cost`, `billing_frequency`, …”; “FiatLife preserves unknown tags when editing”; “Deletion: NIP-09 or tombstone — same in both”). Before release, verify each item. You can add this checklist to this doc or to a `INTEROP_CHECKLIST.md` in either repo. |
| **4. Optional: contract tests** | In FiatLife, add a test that builds a 37004 event (or the tag set) and asserts it matches the expected schema (tag keys and allowed values from CypherLog NIP.md). In CypherLog, if possible, a test that parses a “minimal” 37004 (tags-only, as FiatLife would write) and doesn’t crash. That guards against schema drift. |
| **5. One change, both sides** | If you change 37004 in CypherLog (new required tag, new enum value), update NIP.md and this integration doc, then update FiatLife to send the new tag/value. Doing this in one workspace makes it easy to search both codebases for “37004” or “billing_frequency” and update both. |

**Minimal interop checklist (both sides):**

- [ ] **Kind**: 37004 only for subscription events; replaceable by `d` (NIP-33).
- [ ] **Tags**: At least `d`, `name`, `cost`, `billing_frequency`; optional `currency`, `subscription_type`, `company_name`, `company_id`, `linked_asset_*`, `notes`. Values for `billing_frequency`: `weekly` | `monthly` | `quarterly` | `semi-annually` | `annually` | `one-time`.
- [ ] **FiatLife**: When editing a 37004-sourced subscription, preserve and re-emit all tags not explicitly changed (so CypherLog links are not dropped).
- [ ] **FiatLife**: Subscription-type bills are either **37004 only** (home-related / show in CypherLog) or **30078 only** (FiatLife-only); never both for the same item.
- [ ] **Deletion**: Same method in both apps (NIP-09 for 37004, or agreed tombstone 37004); confirm in CypherLog NIP.md and this doc.
- [ ] **Encryption**: If CypherLog requires encrypted content for 37004, FiatLife writes the same format; otherwise tags-only is acceptable.

With both projects in one Cursor workspace and the contract (NIP.md + this doc) as the single source of truth, interoperability can be implemented and verified in one place.

---

With this approach, the two applications stay interoperable with minimal coordination: FiatLife adopts CypherLog’s existing 37004 schema and uses it as the single source of truth for subscriptions, while keeping all other bills in 30078.
