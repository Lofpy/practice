#!/bin/bash
set -euo pipefail
base=${1:?image prefix}
tag=${2:?tag}
source "$(dirname "$0")/smoke-runtime-flags.sh"
runtime_flags+=(--tmpfs /data:rw,noexec,nosuid,nodev,size=256m,uid=10001,gid=10001)
mkdir -p .build
for service in pvp lobby survival proxy; do
  image="$base/$service:$tag"
  docker run --rm "${runtime_flags[@]}" --entrypoint sh "$image" -c 'test "$(id -u)" = 10001 && test -x /usr/local/bin/poppy-entrypoint && python3 -c "import yaml,toml"'
done
# Do not accept Minecraft EULA in CI. Backends MUST refuse an unaccepted EULA.
for service in pvp lobby survival; do
  if docker run --rm "${runtime_flags[@]}" "$base/$service:$tag" > ".build/$service-eula.log" 2>&1; then
    echo "Backend unexpectedly started without EULA acceptance" >&2
    exit 1
  fi
  grep -q 'Operator EULA acceptance required' ".build/$service-eula.log"
done
# Reproduce the original failure: extracting JNA into /tmp must still fail
# under noexec. Then exercise the real bundled JNA/OSHI using immutable native
# libraries, without running Minecraft or accepting its EULA.
if docker run --rm "${runtime_flags[@]}" --entrypoint java "$base/survival:$tag" \
  -Djna.nosys=true -Djna.tmpdir=/tmp \
  -cp '/opt/poppy/native/probe:/opt/poppy/native/probe-libs/*' \
  NativeStartupProbe /opt/poppy/native > .build/survival-noexec-regression.log 2>&1; then
  echo "JNA extraction unexpectedly succeeded on noexec /tmp" >&2
  exit 1
fi
grep -q 'UnsatisfiedLinkError' .build/survival-noexec-regression.log
grep -Eq 'failed to map segment|Operation not permitted|Permission denied' .build/survival-noexec-regression.log
docker run --rm "${runtime_flags[@]}" --entrypoint python3 "$base/survival:$tag" \
  /opt/poppy/native-libraries.py probe > .build/survival-native.log 2>&1
cat .build/survival-native.log
grep -q '^SURVIVAL_NATIVE_READY ' .build/survival-native.log
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
