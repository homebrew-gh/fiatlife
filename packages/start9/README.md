# FiatLife StartOS package (0.4.x)

Bundles [`apps/web/`](../../apps/web/) into a `.s9pk` for **StartOS 0.4.x**.

The web UI reads from your **existing Nostr relay** (same nsec + relay as the Android app). It does not bundle a relay or Blossom server.

## Prereqs

1. **start-cli** 0.4+ — [packaging guide](https://docs.start9.com/packaging/0.4.0.x/environment-setup.html)
2. **Node.js 20+**, npm, Docker
3. **squashfs-tools** + **squashfs-tools-ng** (`mksquashfs`, `tar2sqfs`)
4. **mmdebstrap** + **debian-archive-keyring** (for the import build path)

```bash
sudo apt install mmdebstrap debian-archive-keyring squashfs-tools squashfs-tools-ng
```

## Build (recommended — same as NoMoXcel)

Uses host-built artifacts + `docker import` instead of `docker buildx` (avoids the `unknown shorthand flag: 'f'` error on some hosts):

```bash
cd packages/start9
npm ci
make x86-import    # → fiatlife_x86_64.s9pk
```

If the Docker image already exists locally:

```bash
make x86-pack
make verify
```

For **aarch64** Start9 hardware: `make arm-import` → `fiatlife_aarch64.s9pk`

If `docker buildx` works on your machine, `make x86` also works (standard path).

## Sideload

StartOS → **Sideload** → upload `fiatlife_x86_64.s9pk` (or `_aarch64` on Pi hardware).

Or install over LAN:

```yaml
# ~/.startos/config.yaml
host: http://your-start9.local
```

```bash
make x86-import install
```

## Local dev (web + server)

```bash
# Terminal 1 — backend (serves built SPA from apps/web/web/dist)
cd apps/web/server && cargo run

# Terminal 2 — frontend
cd apps/web/web && npm install && npm run dev
```

Open http://localhost:5173 (Vite proxies `/api` to port 3000).

## Layout

| Path | Purpose |
|------|---------|
| `startos/` | TypeScript SDK (manifest, main, interfaces) |
| `scripts/` | Host-build + docker-import pack helpers |
| `instructions.md` | Shown at install time |
| `../../apps/web/` | Rust Axum backend + React SPA |
