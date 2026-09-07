#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8090}"
json() { curl -fsS -H "Authorization: Bearer ${TOKEN:-}" "$@"; }
TOKEN="$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')"
test -n "$TOKEN" && test "$TOKEN" != "null"
export TOKEN
json "$BASE/api/tower/overview" | jq -e '.code==0 and .data.kpi' >/dev/null
json "$BASE/api/trace/page?stuck=true" | jq -e '.code==0 and (.data|type=="array")' >/dev/null
json -X POST "$BASE/api/alert/evaluate" | jq -e '.code==0' >/dev/null
json -X POST "$BASE/api/action" -H 'Content-Type: application/json' -d '{"type":"OMS_HOLD","targetKey":"SO000043"}' | jq -e '.code==0 and .data.status=="SUCCESS"' >/dev/null
json -X POST "$BASE/api/forecast/run" -H 'Content-Type: application/json' -d '{"sku":"SKU001","warehouseCode":"WH-SH","horizon":14,"method":"AUTO"}' | jq -e '.code==0 and (.data.forecast|length)==14' >/dev/null
json "$BASE/api/forecast/replenish?warehouseCode=WH-SH&horizon=14&serviceDays=3" | jq -e '.code==0' >/dev/null
BASELINE="$(json -X POST "$BASE/api/sandbox/baseline" | jq -r '.data.id')"
SCENARIO="$(json -X POST "$BASE/api/sandbox/scenario" -H 'Content-Type: application/json' -d '{"name":"2倍需求","params":{"demandMultiplier":2,"allocationStrategy":"SINGLE_WAREHOUSE","singleWarehouse":"WH-SH"}}' | jq -r '.data.id')"
json -X POST "$BASE/api/sandbox/scenario/$SCENARIO/run" | jq -e '.code==0' >/dev/null
json "$BASE/api/sandbox/compare?ids=$BASELINE,$SCENARIO" | jq -e '.code==0 and (.data|length)>=2' >/dev/null
json "$BASE/api/cost/summary?days=30" | jq -e '.code==0 and .data.total' >/dev/null
json -X POST "$BASE/api/integration/system/OMS/health" | jq -e '.code==0 and .data.ok==true' >/dev/null
json -X POST "$BASE/api/integration/sync" -H 'Content-Type: application/json' -d '{}' | jq -e '.code==0' >/dev/null
echo "smoke ok: baseline=$BASELINE scenario=$SCENARIO"
