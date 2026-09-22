#!/bin/bash
# Keep smoke containers equivalent to Compose's non-root, read-only runtime.
# /data is provided by each test: a disposable tmpfs or a fresh named volume.
runtime_flags=(--user 10001:10001 --read-only --cap-drop=ALL
  --security-opt=no-new-privileges
  --tmpfs /tmp:rw,nosuid,nodev,noexec,size=256m,mode=1777
  --tmpfs /run/poppy:rw,nosuid,nodev,noexec,size=1m,uid=10001,gid=10001)
