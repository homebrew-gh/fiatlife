#!/usr/bin/env bash
# Run docker, using `sg docker` when the user is in the docker group but the
# current shell session has not picked up the new group yet (after usermod).
set -euo pipefail

if docker info >/dev/null 2>&1; then
  exec docker "$@"
fi

if sg docker -c "docker info" >/dev/null 2>&1; then
  cmd=$(printf '%q ' docker "$@")
  exec sg docker -c "${cmd% }"
fi

echo "Cannot talk to the Docker daemon." >&2
echo "If you just ran: sudo usermod -aG docker \"\$USER\"" >&2
echo "  open a new terminal, or run:  newgrp docker" >&2
echo "Otherwise try:  DOCKER='sudo docker' make x86-import" >&2
exit 1
