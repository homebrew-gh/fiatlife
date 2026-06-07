#!/usr/bin/env bash
# Run start-cli with Docker socket access (sg docker when the group is not active).
set -euo pipefail

export PATH="${HOME}/.local/bin:${PATH:-}"

START_CLI="$(command -v start-cli || true)"
if [[ -z "$START_CLI" ]]; then
  echo "Error: start-cli not found. See packages/start9/README.md" >&2
  exit 1
fi

if ! command -v tar2sqfs >/dev/null || ! command -v mksquashfs >/dev/null; then
  echo "Error: tar2sqfs/mksquashfs not found. Install squashfs-tools and squashfs-tools-ng:" >&2
  echo "  sudo apt install squashfs-tools squashfs-tools-ng" >&2
  exit 1
fi

if docker info >/dev/null 2>&1; then
  exec "$START_CLI" "$@"
fi

if sg docker -c "docker info" >/dev/null 2>&1; then
  cmd=$(printf '%q ' "$START_CLI" "$@")
  exec sg docker -c "PATH=${HOME}/.local/bin:\$PATH; ${cmd% }"
fi

echo "Cannot talk to the Docker daemon." >&2
echo "If you just ran: sudo usermod -aG docker \"\$USER\"" >&2
echo "  open a new terminal, or run:  newgrp docker" >&2
echo "Otherwise try:  DOCKER='sudo docker' make x86-import" >&2
exit 1
