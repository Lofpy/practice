#!/bin/sh
set -eu
# The Python transaction never invokes compose down or a timed force-kill.
exec python3 /opt/poppy/deploy.py "$@"
