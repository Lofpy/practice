#!/bin/sh
set -eu
if [ "$#" -eq 0 ]; then set -- run; fi
exec python3 /opt/poppy/runtime.py "$@"
