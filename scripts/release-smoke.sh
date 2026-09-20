#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${SERVER_PORT:-8090}"
cd "$DIR"
./start.sh > "$DIR/smoke.log" 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; }
trap cleanup EXIT
ok=0
for _ in $(seq 1 90); do
  if curl -sf "http://127.0.0.1:${PORT}/" >/dev/null 2>&1; then ok=1; break; fi
  sleep 1
done
if [[ "$ok" != "1" ]]; then
  echo "SMOKE FAIL ir: app did not start on :$PORT"
  tail -n 80 "$DIR/smoke.log" || true
  exit 1
fi
html="$(curl -sS "http://127.0.0.1:${PORT}/")"
echo "$html" | grep -qiE '<html|<div id=.app' || { echo "SMOKE FAIL ir: / is not HTML"; exit 1; }
spa="$(curl -sS -o /tmp/ir-spa.body -w "%{http_code}" "http://127.0.0.1:${PORT}/dashboard")"
test "$spa" = "200"
body="$(curl -sS -X POST "http://127.0.0.1:${PORT}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')"
echo "$body" | grep -q '"code":0' || { echo "SMOKE FAIL ir: login code != 0: $body"; exit 1; }
echo "$body" | grep -q '"token"' || { echo "SMOKE FAIL ir: login has no token: $body"; exit 1; }

echo "SMOKE OK ir :$PORT"
