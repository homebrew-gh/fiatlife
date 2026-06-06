# FiatLife StartOS package (0.4.x)

Bundles [`apps/web/`](../../apps/web/) into a `.s9pk` for **StartOS 0.4.x**.

The web UI reads from your **existing Nostr relay** (same nsec + relay as the Android app). It does not bundle a relay or Blossom server.

## Prereqs

1. **start-cli** 0.4+ — [packaging guide](https://docs.start9.com/packaging/0.4.0.x/environment-setup.html)
2. **Node.js 20+**, npm, Docker (BuildKit), squashfs-tools + squashfs-tools-ng

## Build

```bash
cd packages/start9
npm ci
make x86-import    # → fiatlife_x86_64.s9pk
```

If Docker build works directly: `make x86`

## Sideload

StartOS → **Sideload** → upload `fiatlife_x86_64.s9pk` (or `_aarch64` on Pi hardware).

## Local dev (web + server)

```bash
# Terminal 1 — backend (serves built SPA from apps/web/web/dist)
cd apps/web/server && cargo run
# Or set explicitly: FL_STATIC_DIR=../web/dist cargo run

# Terminal 2 — frontend
cd apps/web/web && npm install && npm run dev
```

Open http://localhost:5173 (Vite proxies `/api` to port 3000).

## Layout

| Path | Purpose |
|------|---------|
| `startos/` | TypeScript SDK (manifest, main, interfaces) |
| `instructions.md` | Shown at install time |
| `../../apps/web/` | Rust Axum backend + React SPA |
