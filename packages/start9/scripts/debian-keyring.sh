#!/usr/bin/env bash
# Return a directory of Debian archive keyrings (cached under .pack/).
set -euo pipefail

PACK="${1:?pack dir}"
CACHED="$PACK/debian-apt-keyrings"

if [[ -d "$CACHED" && -f "$CACHED/debian-archive-keyring.gpg" ]]; then
  echo "$CACHED"
  exit 0
fi

if [[ -d /usr/share/keyrings && -f /usr/share/keyrings/debian-archive-keyring.gpg ]]; then
  mkdir -p "$CACHED"
  cp -a /usr/share/keyrings/debian-archive-*.gpg "$CACHED/" 2>/dev/null || true
  cp /usr/share/keyrings/debian-archive-keyring.gpg "$CACHED/"
  echo "$CACHED"
  exit 0
fi

if ! command -v dpkg-deb >/dev/null; then
  echo "Install Debian archive keys: sudo apt install debian-archive-keyring" >&2
  exit 1
fi

mkdir -p "$PACK"
DEB="$PACK/debian-archive-keyring.deb"
EXTRACT="$PACK/keyring-extract"
URL="http://deb.debian.org/debian/pool/main/d/debian-archive-keyring/debian-archive-keyring_2023.3+deb12u2_all.deb"

echo ">> downloading debian-archive-keyring for apt verification" >&2
curl -fsSL -o "$DEB" "$URL"
rm -rf "$EXTRACT" "$CACHED"
mkdir -p "$EXTRACT"
dpkg-deb -x "$DEB" "$EXTRACT"
mkdir -p "$CACHED"
cp -a "$EXTRACT/usr/share/keyrings/"*.gpg "$CACHED/"
rm -rf "$EXTRACT" "$DEB"

if [[ ! -f "$CACHED/debian-archive-keyring.gpg" ]]; then
  echo "Could not extract Debian keyrings from $URL" >&2
  echo "Try: sudo apt install debian-archive-keyring" >&2
  exit 1
fi

echo "$CACHED"
