#!/usr/bin/env bash
# Assemble a Debian bookworm rootfs on the host and load it with `docker import`.
# Use when `docker build` fails with veth / bridge networking errors.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PACK="$ROOT/apps/web/.pack"
ROOTFS="$PACK/rootfs"
IMAGE_TAG="${1:-start9/fiatlife/main:0.4.0}"
DOCKER="${DOCKER:-docker}"

echo ">> building web + server artifacts"
"$(dirname "$0")/build-artifacts.sh"

run_docker() {
  if "$DOCKER" info >/dev/null 2>&1; then
    "$DOCKER" "$@"
  elif sg docker -c "$DOCKER info" >/dev/null 2>&1; then
    local cmd
    cmd=$(printf '%q ' "$DOCKER" "$@")
    sg docker -c "${cmd% }"
  else
    echo "Cannot talk to Docker for import/save." >&2
    exit 1
  fi
}

cleanup_rootfs() {
  local dir="$1"
  [[ -e "$dir" ]] || return 0
  if rm -rf "$dir" 2>/dev/null; then
    return 0
  fi
  if command -v fakeroot >/dev/null && fakeroot rm -rf "$dir" 2>/dev/null; then
    return 0
  fi
  echo ">> removing previous rootfs (sudo — one-time cleanup from an earlier build)" >&2
  sudo rm -rf "$dir"
}

mmdebstrap_mode() {
  if [[ $(id -u) -eq 0 ]]; then
    echo root
  elif command -v sudo >/dev/null; then
    echo sudo-wrap
  else
    echo "mmdebstrap needs sudo on this host." >&2
    exit 1
  fi
}

run_mmdebstrap() {
  local keyring_dir="$1"
  local mirror="$2"
  local -a args=(
    --mode=root
    --variant=minbase
    --include=ca-certificates,curl,libssl3,libgcc-s1,util-linux
    --keyring="$keyring_dir"
    bookworm "$ROOTFS" "$mirror"
  )
  if [[ $(id -u) -eq 0 ]]; then
    mmdebstrap "${args[@]}"
  else
    sudo mmdebstrap "${args[@]}"
  fi
}

rootfs_needs_sudo() {
  [[ ! -w "$ROOTFS" ]] || [[ $(stat -c '%u' "$ROOTFS" 2>/dev/null || echo 0) -ne $(id -u) ]]
}

install_app_files() {
  local use_sudo="${1:-0}"
  if [[ "$use_sudo" == "1" ]]; then
    sudo install -D -m 0755 "$PACK/fiatlife-web" "$ROOTFS/usr/local/bin/fiatlife-web"
    sudo rm -rf "$ROOTFS/srv/dist"
    sudo cp -a "$PACK/dist" "$ROOTFS/srv/dist"
    sudo mkdir -p "$ROOTFS/data"
  else
    mkdir -p "$ROOTFS/srv/dist" "$ROOTFS/data" "$ROOTFS/usr/local/bin"
    install -D -m 0755 "$PACK/fiatlife-web" "$ROOTFS/usr/local/bin/fiatlife-web"
    rm -rf "$ROOTFS/srv/dist"
    cp -a "$PACK/dist" "$ROOTFS/srv/dist"
    chmod -R a+rX "$ROOTFS/srv/dist"
  fi
}

assemble_rootfs() {
  cleanup_rootfs "$ROOTFS"
  mkdir -p "$ROOTFS"
  local keyring_dir mirror mode

  if command -v mmdebstrap >/dev/null; then
    keyring_dir="$("$(dirname "$0")/debian-keyring.sh" "$PACK")"
    mirror="deb [signed-by=${keyring_dir}/debian-archive-keyring.gpg] http://deb.debian.org/debian bookworm main"
    mode="$(mmdebstrap_mode)"
    echo ">> mmdebstrap bookworm rootfs (mode=$mode)"
    run_mmdebstrap "$keyring_dir" "$mirror"
    echo ">> install app files into rootfs"
    install_app_files 1
  elif command -v debootstrap >/dev/null; then
    echo ">> debootstrap bookworm rootfs (sudo)"
    sudo debootstrap --no-check-gpg --variant=minbase bookworm "$ROOTFS" http://deb.debian.org/debian
    sudo chroot "$ROOTFS" apt-get update
    sudo chroot "$ROOTFS" apt-get install -y --no-install-recommends ca-certificates curl libssl3 libgcc-s1 util-linux
    sudo chroot "$ROOTFS" rm -rf /var/lib/apt/lists/*
    echo ">> install app files into rootfs"
    install_app_files 1
  else
    echo "Install mmdebstrap or debootstrap to build without docker build:" >&2
    echo "  sudo apt install mmdebstrap debian-archive-keyring" >&2
    echo "  sudo apt install debootstrap        # alternative" >&2
    exit 1
  fi
}

if [[ -f "$PACK/Dockerfile" && -f "$PACK/fiatlife-web" && -d "$PACK/dist" ]]; then
  echo ">> docker build from prebuilt .pack (no sudo/mmdebstrap)"
  run_docker build -t "$IMAGE_TAG" "$PACK"
  echo ">> built $IMAGE_TAG"
else
  echo ">> assemble rootfs"
  assemble_rootfs

  TAR="$PACK/rootfs.tar"
  echo ">> tar rootfs"
  if rootfs_needs_sudo; then
    sudo tar -C "$ROOTFS" -c . > "$TAR"
    sudo chown "$(id -u):$(id -g)" "$TAR"
  else
    tar -C "$ROOTFS" -c . > "$TAR"
  fi

  echo ">> docker import -> $IMAGE_TAG"
  run_docker import \
    --change 'ENV FL_DATA_DIR=/data FL_STATIC_DIR=/srv/dist FL_PORT=3000 FL_BIND=0.0.0.0 FL_LOG=info FL_INSECURE_RELAY_TLS=1' \
    --change 'EXPOSE 3000' \
    --change 'VOLUME ["/data"]' \
    --change 'HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 CMD curl -fsS http://127.0.0.1:3000/api/health || exit 1' \
    --change 'ENTRYPOINT ["/usr/local/bin/fiatlife-web"]' \
    - "$IMAGE_TAG" < "$TAR"

  rm -f "$TAR"
  cleanup_rootfs "$ROOTFS"
  echo ">> imported $IMAGE_TAG"
fi
