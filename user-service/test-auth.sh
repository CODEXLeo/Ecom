#!/usr/bin/env bash

# Backward-compatible entry point. The canonical User Service
# regression suite lives in test-user-service.sh.

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$SCRIPT_DIR/test-user-service.sh" "$@"
