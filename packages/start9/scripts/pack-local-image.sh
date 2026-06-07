#!/usr/bin/env bash
# Pack using a pre-built local Docker image (after `make image-import`).
# Use when `docker build` fails on the host (veth/bridge errors).
set -euo pipefail

ARCH="${1:-x86_64}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/startos/manifest/index.ts"
BACKUP="$ROOT/startos/manifest/index.ts.bak-local"
TAG="start9/fiatlife/main:0.4.0"

cd "$ROOT"
export PATH="${HOME}/.local/bin:${PATH:-}"

if ! command -v start-cli >/dev/null; then
  echo "Install start-cli 0.4+ (see README.md)" >&2
  exit 1
fi

if ! sg docker -c "docker image inspect '$TAG'" >/dev/null 2>&1; then
  echo "Local image $TAG not found. Run: make image-import" >&2
  exit 1
fi

cp "$MANIFEST" "$BACKUP"
trap 'mv -f "$BACKUP" "$MANIFEST"; npm run build >/dev/null' EXIT

python3 << PY
import re
from pathlib import Path
p = Path("$MANIFEST")
text = p.read_text()
new = """      source: {
        dockerTag: '$TAG',
      },"""
if "dockerTag:" in text and "dockerBuild:" not in text:
    pass  # already patched
else:
    patched, n = re.subn(
        r"source:\s*\{\s*dockerBuild:\s*\{\s*workdir:\s*'../../apps/web',\s*\},\s*\},",
        new.strip(),
        text,
        count=1,
    )
    if n != 1:
        raise SystemExit("manifest/index.ts layout changed; update pack-local-image.sh")
    p.write_text(patched)
PY

npm run build
make "arch/$ARCH"

mv -f "$BACKUP" "$MANIFEST"
trap - EXIT
npm run build >/dev/null
echo "Built fiatlife_${ARCH}.s9pk (local image)"
