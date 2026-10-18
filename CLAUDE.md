# FiatLife

Privacy-first personal finance app (paycheck/tax calculator, bills, goals, budgets, credit/debt, bank accounts, bitcoin wallets, net worth, SimpleFIN balance sync). All user data is NIP-44 self-encrypted and stored as Nostr kind-30078 events on the user's own relay; attachments go to a Blossom server. Part of the "homebrew" suite of self-hosted Nostr apps that share one identity and relay (CypherLog, NoMoXcel, …).

There are **two clients over the same relay data**, and they must stay compatible:

- **Android app** (`app/`) — Kotlin, the original client.
- **Web app + server** (`apps/web/`) — React SPA + Rust backend, shipped to StartOS via `packages/start9/`.

## Layout

```
app/                      Android app (only Gradle module; settings.gradle.kts includes :app)
  src/main/java/com/fiatlife/app/
    data/blossom|local|network|nostr|notification|repository|security|sync
    di/                   Hilt modules (AppModule, DatabaseModule incl. Room migrations)
    domain/model/         Domain models + pure business logic (tax math, net worth, SimpleFIN…)
    ui/components|navigation|screens|theme|viewmodel
  src/test/               JVM unit tests (domain/model only)
apps/web/web/             React 18 + Vite + TypeScript + Tailwind SPA (src/lib = domain logic, src/routes, src/components)
apps/web/server/          Rust (axum/tokio) backend: seals nsec at rest, talks to relay, SimpleFIN, BTC price, Blossom
apps/web/Dockerfile       Builds SPA + server into one image
packages/start9/          StartOS 0.4.x package (TypeScript start-sdk in startos/, Makefile → .s9pk)
docs/                     Design/plan docs (sync reliability, credit-card bill logic, CypherLog interop, ingestion plan…)
NIP.md                    Nostr data contract: d-tags, payload schemas, tombstones — read before touching sync
gradle/libs.versions.toml Version catalog for all Android deps
```

## Commands

Android (JDK 17; `local.properties` points at the SDK):

```bash
./gradlew assembleDebug              # debug APK (applicationId com.fiatlife.app.debug)
./gradlew :app:testDebugUnitTest     # unit tests — what CI runs
./gradlew :app:testDebugUnitTest --tests 'com.fiatlife.app.domain.model.NetWorthTest'
./gradlew lintDebug                  # Android lint (no ktlint/detekt configured)
./gradlew installDebug
```

Release APKs are built and signed **locally** (`SIGNING_STORE_FILE` etc. env vars; falls back to the debug keystore). CI only compiles and runs unit tests.

Web (`apps/web/web`): `npm ci`, `npm run dev`, `npm run typecheck`, `npm run build` (CI runs `build`, which is `tsc -b && vite build`).

Server (`apps/web/server`): `cargo test --locked` (CI), `cargo run`. Configured by `FL_*` env vars (`FL_DATA_DIR`, `FL_STATIC_DIR`, `FL_PORT`, …; see `src/config.rs` and the Dockerfile).

Start9 (`packages/start9`): `npm ci && make x86-import` → `fiatlife_x86_64.s9pk` (`make arm-import` for aarch64; `make verify`). See its README for prereqs. Package version lives in `startos/versions/` and `startos/manifest/`.

## Architecture & conventions (Android)

- **MVVM + clean-ish layering**: Compose screens → Hilt `@HiltViewModel`s (`ui/viewmodel`) → `@Singleton` repositories (`data/repository`) → Room DAOs + `NostrClient`. Business logic belongs in `domain/model` as pure Kotlin so it's unit-testable.
- **DI**: Hilt everywhere (`@Inject constructor`, modules in `di/`). WorkManager workers use `androidx.hilt.work` (`BillReminderWorker`, `SimpleFinSyncWorker`).
- **Room as a JSON cache**: entities mostly store the whole domain object as `jsonData` (kotlinx-serialization) plus a few indexed columns (`id`, `category`/`type`, `updatedAt`). Domain models are `@Serializable` data classes.
- **Room migrations**: `exportSchema = false`, no destructive fallback. When adding/changing an entity: bump `version` in `FiatLifeDatabase`, add a `MIGRATION_N_N+1` in `di/DatabaseModule.kt`, and register it in `addMigrations(...)`. Never edit an old migration.
- **Repository pattern**: save = upsert locally first, then (if `nostrClient.hasSigner`) `publishEncryptedAppData("fiatlife/<type>/<uuid>", json)`; publish failures are logged, not thrown. `syncFromNostr()` pulls kind-30078 events, decrypts, and writes to Room. Decode defensively (see `GoalRepository.decodeGoalSafely`) — unknown/broken payloads are skipped, not fatal. Use `Log` with a file-level `TAG`.
- **Nostr**: kind 30078 parameterized-replaceable events, `d` tag = `fiatlife/<type>/<id>` (`fiatlife/salary` is singular). Content is NIP-44 v2 encrypted to the user's own pubkey. Auth via NIP-42. Signers implement `NostrSigner`: `LocalSigner` (in-app key) or `AmberSigner` (NIP-55 external signer). Deletes = tombstone `{"deleted":true}` + NIP-09 kind-5. CypherLog subscription events are interop data — see `docs/CYPHERLOG_*`.
- **UI**: Jetpack Compose + Material 3, strong skipping enabled. Navigation via `navigation-compose` (`ui/navigation`).

## Gotchas

- **Android ↔ web parity**: domain logic (paycheck/tax tables, net worth, bill schedules, debt payoff) is implemented twice — Kotlin `domain/model` and TS `apps/web/web/src/lib`. A change to one usually needs the same change in the other, and both must read/write the same JSON shape on the relay. Update `NIP.md` when a payload schema changes; keep new fields optional/defaulted so older clients still decode.
- **Relay sync reliability** has bitten before (relay default limits, dropped events, early EOSE, timeouts) — read `docs/RELAY_SYNC_RELIABILITY.md` before changing `NostrClient` or sync code. Reads are paged and sequential on purpose.
- Money/finance math: tax tables are per-year; be careful with date math (paydays are DST-safe — keep it that way).
- Built artifacts (`*.apk`, `*.s9pk`) sit in the tree but are gitignored; don't commit them.
- Commit messages: short imperative summary, often prefixed by area (`Android:`, `Start9:`, `CI:`, `docs:`).
