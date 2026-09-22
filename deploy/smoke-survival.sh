#!/bin/bash
# Real Paper startup is opt-in and reuses an operator's existing EULA file.
# Never manufacture acceptance in CI, change the supplied file, or touch a
# production world. All game data belongs to a new isolated test volume.
set -euo pipefail
base=${1:?image prefix}
tag=${2:?tag}
eula=${3:?path to existing operator-accepted eula.txt}
source "$(dirname "$0")/smoke-runtime-flags.sh"
python3 - "$eula" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1])
if not source.is_file() or source.is_symlink():
    raise SystemExit("An existing regular operator EULA file is required")
values = dict(line.split("=", 1) for line in source.read_text().splitlines()
              if "=" in line and not line.lstrip().startswith("#"))
if values.get("eula", "").strip() != "true":
    raise SystemExit("Operator EULA acceptance required; input was not changed")
PY
mkdir -p .build
name="poppy-survival-smoke-$(date +%s)-$RANDOM"
volume="$name-data"
scratch=$(mktemp -d)
created=false
volume_created=false

cleanup() {
  result=$?
  trap - EXIT
  set +e
  if [[ "$created" == true ]]; then
    if [[ "$(docker inspect --format '{{.State.Running}}' "$name" 2>/dev/null)" == true ]]; then
      # Console shutdown only. Never force-kill a running Minecraft world writer.
      docker exec "$name" python3 /opt/poppy/runtime.py stop
      timeout 120 docker wait "$name" > .build/survival-cleanup-exit.log
    fi
    docker logs "$name" > .build/survival-startup.log 2>&1
    cat .build/survival-startup.log
    if [[ "$(docker inspect --format '{{.State.Running}}' "$name" 2>/dev/null)" == false ]]; then
      docker rm "$name" >/dev/null
    else
      echo "Survival fixture still running; preserving $name and $volume for inspection" >&2
      result=1
      volume_created=false
    fi
  fi
  if [[ "$volume_created" == true ]]; then docker volume rm "$volume" >/dev/null; fi
  # This is our mktemp directory, containing only a non-secret EULA copy.
  rm -f "$scratch/eula.txt"
  rmdir "$scratch"
  exit "$result"
}
trap cleanup EXIT

# Only the copy receives a readable mode; the operator's source is unmodified.
install -m 0644 "$eula" "$scratch/eula.txt"
docker volume create --label ascending.test=survival-startup "$volume" >/dev/null
volume_created=true
docker create --name "$name" --network bridge "${runtime_flags[@]}" \
  --mount "type=volume,source=$volume,target=/data" \
  --mount "type=bind,source=$scratch/eula.txt,target=/run/operator-eula.txt,readonly" \
  --entrypoint sh "$base/survival:$tag" -c \
  'test ! -e /data/eula.txt && cp /run/operator-eula.txt /data/eula.txt && exec /usr/local/bin/poppy-entrypoint' >/dev/null
created=true
docker start "$name" >/dev/null

# Match deploy.py's per-service deadline. runtime.py verifies the plugin-ready
# message, absence of startup errors, and the actual protocol-777 status reply.
deadline=$((SECONDS + 240))
healthy=false
while (( SECONDS < deadline )); do
  if docker exec "$name" python3 /opt/poppy/runtime.py health > .build/survival-health.log 2>&1; then
    healthy=true
    break
  fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$name")" != true ]]; then break; fi
  sleep 2
done
if [[ "$healthy" != true ]]; then
  cat .build/survival-health.log >&2
  echo "Survival did not reach strict production readiness within 240 seconds" >&2
  exit 1
fi
docker exec "$name" python3 /opt/poppy/runtime.py stop
timeout 120 docker wait "$name" > .build/survival-exit.log
grep -qx 0 .build/survival-exit.log
echo "Survival passed real startup, protocol-777 readiness and normal shutdown."
