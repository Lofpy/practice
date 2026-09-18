#!/bin/bash
set -euo pipefail
IMAGE_BASE=${1:?image base required}
RELEASE_SHA=${2:?release sha required}
ROOT=/opt/poppy/deploy
DATA=/srv/poppy
REGISTRY_HOST=${IMAGE_BASE%%/*}
mkdir -p "$DATA"/{pvp,lobby,proxy,backups} "$ROOT"
cd "$ROOT"
gcloud auth configure-docker "$REGISTRY_HOST" --quiet
if [ -f .env ]; then cp .env .env.previous; fi
printf 'IMAGE_BASE=%s\nRELEASE_SHA=%s\n' "$IMAGE_BASE" "$RELEASE_SHA" > .env.next
tar --exclude='backups' -C "$DATA" -czf "$DATA/backups/pre-${RELEASE_SHA}-$(date -u +%Y%m%dT%H%M%SZ).tgz" pvp lobby proxy
docker compose --env-file .env.next pull
docker compose --env-file .env.next down --timeout 90
mv .env.next .env
if ! docker compose --env-file .env up -d --wait --wait-timeout 120; then
  docker compose --env-file .env down --timeout 30 || true
  if [ -f .env.previous ]; then
    mv .env.previous .env
    docker compose --env-file .env up -d
  fi
  exit 1
fi
docker image prune -f --filter 'until=168h'
