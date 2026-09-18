#!/bin/bash
set -euo pipefail
base=${1:?image prefix}
tag=${2:?tag}
runtime_flags=(--read-only --cap-drop=ALL --security-opt=no-new-privileges
  --tmpfs /tmp:rw,nosuid,nodev,size=256m,mode=1777
  --tmpfs /run/poppy:rw,nosuid,nodev,size=1m,uid=10001,gid=10001
  --tmpfs /data:rw,nosuid,nodev,size=256m,uid=10001,gid=10001)
for service in pvp lobby proxy; do
  image="$base/$service:$tag"
  docker run --rm "${runtime_flags[@]}" --entrypoint sh "$image" -c 'test "$(id -u)" = 10001 && test -x /usr/local/bin/poppy-entrypoint && python3 -c "import yaml,toml"'
done
# Do not accept Minecraft EULA in CI. Backends MUST refuse an unaccepted EULA.
for service in pvp lobby; do
  if docker run --rm "${runtime_flags[@]}" "$base/$service:$tag" > ".build/$service-eula.log" 2>&1; then
    echo "Backend unexpectedly started without EULA acceptance" >&2
    exit 1
  fi
  grep -q 'Operator EULA acceptance required' ".build/$service-eula.log"
done
# Velocity has no Minecraft world writer: test actual start/readiness/console shutdown.
name="poppy-proxy-smoke-$RANDOM"
docker run -d --name "$name" --network host "${runtime_flags[@]}" "$base/proxy:$tag"
trap 'docker logs "$name" || true; docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
healthy=false
for attempt in $(seq 1 60); do
  if docker exec "$name" python3 /opt/poppy/runtime.py health; then healthy=true; break; fi
  sleep 2
done
test "$healthy" = true
docker exec "$name" python3 /opt/poppy/runtime.py stop
timeout 60 docker wait "$name" | grep -x 0
