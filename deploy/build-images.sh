#!/bin/bash
set -euo pipefail
base=${1:?image prefix required}
tag=${2:?tag required}
python3 deploy/prepare-build.py
resolve() {
  local image="$1" digest
  digest=$(docker buildx imagetools inspect "$image" --format '{{json .Manifest}}' | jq -er .digest)
  [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]
  printf '%s@%s' "$image" "$digest"
}
build_java=$(resolve eclipse-temurin:8-jdk)
backend_java=$(resolve eclipse-temurin:17-jre)
proxy_java=$(resolve eclipse-temurin:25-jre)
modern_build_java=$(resolve eclipse-temurin:25-jdk)
jq -n --arg build "$build_java" --arg backend "$backend_java" --arg proxy "$proxy_java" \
  --arg modern_build "$modern_build_java" \
  '{build:$build,backend:$backend,proxy:$proxy,modern_build:$modern_build}' > .build/base-images.json
for service in pvp lobby survival proxy; do
  docker build --build-arg "BUILD_JAVA=$build_java" --build-arg "BACKEND_JAVA=$backend_java" \
    --build-arg "PROXY_JAVA=$proxy_java" --build-arg "MODERN_BUILD_JAVA=$modern_build_java" --target "$service" \
    --label "org.opencontainers.image.revision=$(git rev-parse HEAD)" \
    -f deploy/Dockerfile -t "$base/$service:$tag" .
done
