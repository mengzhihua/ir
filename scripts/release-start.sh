#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
PORT="${SERVER_PORT:-8090}"
echo "Starting IR 供应链控制塔 on http://127.0.0.1:$PORT"
exec java ${JAVA_OPTS:-} -jar ir-backend-1.0.0.jar --server.port="$PORT" 
