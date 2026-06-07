#!/usr/bin/env bash
# Build the web frontend and Rust server on the host (no Docker RUN steps).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WEB="$ROOT/apps/web/web"
SERVER="$ROOT/apps/web/server"
OUT="$ROOT/apps/web/.pack"

echo ">> npm run build (web)"
(cd "$WEB" && npm run build)

echo ">> cargo build --release (server)"
(cd "$SERVER" && cargo build --release)

TARGET_DIR="${CARGO_TARGET_DIR:-$SERVER/target}"
BIN="$TARGET_DIR/release/fiatlife-web"
if [[ ! -f "$BIN" ]]; then
  echo "Error: $BIN not found after cargo build" >&2
  exit 1
fi

mkdir -p "$OUT"
cp "$BIN" "$OUT/fiatlife-web"
rm -rf "$OUT/dist"
cp -a "$WEB/dist" "$OUT/dist"
cp "$(dirname "$0")/../Dockerfile.pack" "$OUT/Dockerfile"
echo ">> wrote $OUT"
