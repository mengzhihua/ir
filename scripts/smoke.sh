#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE_URL:-http://localhost:8090}"
json() { curl -fsS -H "Authorization: Bearer ${TOKEN:-}" "$@"; }
TOKEN="$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')"
test -n "$TOKEN" && test "$TOKEN" != "null"
export TOKEN
json "$BASE/api/tower/overview" | jq -e '.code==0 and .data.kpi and (.data.systems|length)>=5' >/dev/null
json "$BASE/api/trace/page" | jq -e '.code==0 and (.data.records|length)>0 and .data.total>0' >/dev/null
json "$BASE/api/trace/page?stuck=true&size=20" | jq -e '.code==0 and (.data.records|length)>0 and .data.total>0' >/dev/null
json -X POST "$BASE/api/alert/evaluate" | jq -e '.code==0 and (.data|length)>0' >/dev/null
json "$BASE/api/alert/page" | jq -e '.code==0 and (.data.records|length)>0 and .data.total>0' >/dev/null
json "$BASE/api/alert/stats" | jq -e '.code==0 and .data.total>0 and .data.high>0 and .data.today>0' >/dev/null
json -X POST "$BASE/api/action" -H 'Content-Type: application/json' -d '{"type":"OMS_HOLD","targetKey":"SO000043"}' | jq -e '.code==0 and .data.status=="SUCCESS"' >/dev/null
json "$BASE/api/trace/SO000043" | jq -e '.code==0 and .data.oms.status=="HOLD"' >/dev/null
json -X POST "$BASE/api/action" -H 'Content-Type: application/json' -d '{"type":"OMS_UNHOLD","targetKey":"SO000043"}' | jq -e '.code==0 and .data.status=="SUCCESS"' >/dev/null
json "$BASE/api/trace/SO000043" | jq -e '.code==0 and .data.oms.status=="CREATED"' >/dev/null
STUCK_ID="$(json "$BASE/api/alert/page?status=OPEN&type=ORDER_STUCK&size=5" | jq -r '.data.records[0].id')"
test -n "$STUCK_ID" && test "$STUCK_ID" != "null"
json -X POST "$BASE/api/alert/$STUCK_ID/execute-suggested" | jq -e '.code==0 and .data.status=="SUCCESS"' >/dev/null
DELAY_ID="$(json "$BASE/api/alert/page?status=OPEN&type=TMS_DELAY&size=5" | jq -r '.data.records[0].id')"
test -n "$DELAY_ID" && test "$DELAY_ID" != "null"
json -X POST "$BASE/api/alert/$DELAY_ID/execute-suggested" | jq -e '.code==0 and .data.status=="SUCCESS"' >/dev/null
LOW_ID="$(json "$BASE/api/alert/page?status=OPEN&type=LOW_STOCK&size=5" | jq -r '.data.records[0].id')"
test -n "$LOW_ID" && test "$LOW_ID" != "null"
json -X POST "$BASE/api/alert/$LOW_ID/execute-suggested" | jq -e '.code==0 and .data.status=="SUCCESS" and .data.type=="SRM_PURCHASE_SUGGEST"' >/dev/null
json "$BASE/api/forecast/replenish?warehouseCode=WH-SH&sku=SKU002&horizon=14&serviceDays=3" | jq -e '.code==0 and (.data[0].inTransit|tonumber)==12' >/dev/null
json "$BASE/api/forecast/replenish?warehouseCode=WH-BJ&sku=SKU002&horizon=14&serviceDays=3" | jq -e '.code==0 and (.data[0].inTransit|tonumber)==0' >/dev/null
json -X POST "$BASE/api/action" -H 'Content-Type: application/json' -d '{"type":"SRM_PURCHASE_SUGGEST","targetKey":"SKU-SMOKE","params":{"sku":"SKU-SMOKE","qty":9,"warehouseCode":"WH-GZ"}}' | jq -e '.code==0 and .data.status=="SUCCESS"' >/dev/null
json "$BASE/api/integration/snapshot/page?systemCode=SRM&dataType=PO&size=50" | jq -e '.code==0 and ([.data.records[].bizKey]|index("IR-PO-SRM-SKU-SMOKE-WH-GZ"))!=null' >/dev/null
json "$BASE/api/action/types" | jq -e '.code==0 and (.data|length)>=12' >/dev/null
json -X POST "$BASE/api/forecast/run" -H 'Content-Type: application/json' -d '{"sku":"SKU001","warehouseCode":"WH-SH","horizon":14,"method":"AUTO"}' | jq -e '.code==0 and (.data.forecast|length)==14' >/dev/null
json "$BASE/api/forecast/replenish?warehouseCode=WH-SH&horizon=14&serviceDays=3" | jq -e '.code==0' >/dev/null
json "$BASE/api/forecast/history?sku=SKU001&days=30" | jq -e '.code==0 and (.data|length)>0' >/dev/null
BASELINE="$(json -X POST "$BASE/api/sandbox/baseline" | jq -r '.data.id')"
SCENARIO_NAME="2倍需求-$(date +%H%M%S)"
SCENARIO="$(json -X POST "$BASE/api/sandbox/scenario" -H 'Content-Type: application/json' -d "{\"name\":\"$SCENARIO_NAME\",\"params\":{\"demandMultiplier\":2,\"allocationStrategy\":\"SINGLE_WAREHOUSE\",\"singleWarehouse\":\"WH-SH\"}}" | jq -r '.data.id')"
json -X POST "$BASE/api/sandbox/scenario/$SCENARIO/run" | jq -e '.code==0' >/dev/null
json "$BASE/api/sandbox/compare?ids=$BASELINE,$SCENARIO" | jq -e '.code==0 and (.data|length)>=2' >/dev/null
json "$BASE/api/sandbox/capital/tiers" | jq -e '.code==0 and (.data.presets|length)==5 and .data.maxSku==100000 and (.data.presets[0].label|test("10")) and (.data.presets[4].label|test("十亿"))' >/dev/null
json -X POST "$BASE/api/sandbox/capital" -H 'Content-Type: application/json' -d '{"workingCapital":100000000}' | jq -e '.code==0 and .data.verdict=="RELIABLE" and .data.reliable==true and .data.baseline.capitalVerdict=="RELIABLE" and .data.demand5x.capitalVerdict=="RELIABLE" and (.data.headroom|tonumber)>20 and (.data.optimizations|length)>0 and (.data.recommended.name|test("短交期")) and .data.recommended.replenishLeadDays==1 and (.data.recommended.serviceLevel|tonumber)>=0.995' >/dev/null
json -X POST "$BASE/api/sandbox/capital/sweep" -H 'Content-Type: application/json' -d '{"customAmount":500000}' | jq -e '.code==0 and .data.flowOk==true and (.data.rows|length)==6 and ([.data.rows[].issues[]?]|length)==0' >/dev/null
json -X POST "$BASE/api/sandbox/capital/adopt" -H 'Content-Type: application/json' -d '{"workingCapital":100000000}' | jq -e '.code==0 and (.data.name|test("短交期")) and .data.kind=="MANUAL" and .data.id!=null' >/dev/null
AUTO="$(json -X POST "$BASE/api/sandbox/auto/run" | jq -r '.data.recommended.id')"
test -n "$AUTO" && test "$AUTO" != "null"
json "$BASE/api/sandbox/auto/latest" | jq -e '.code==0 and .data.recommended.id!=null and (.data.recommended.serviceLevel|tonumber)>=0.995 and (.data.recommended.stockoutUnits|tonumber)==0' >/dev/null
json -X POST "$BASE/api/sandbox/scenario/$AUTO/apply?execute=false" | jq -e '.code==0 and (.data|type=="array")' >/dev/null
json "$BASE/api/tower/overview" | jq -e '.code==0 and .data.recommendation.id!=null and .data.recommendation.replenishLeadDays!=null' >/dev/null
json "$BASE/api/cost/summary?days=30" | jq -e '.code==0 and (.data.total|numbers) and (.data.byType|length)>0' >/dev/null
json "$BASE/api/cost/page" | jq -e '.code==0 and (.data.records|length)>0 and .data.total>0' >/dev/null
json "$BASE/api/cost/saving" | jq -e '.code==0' >/dev/null
json "$BASE/api/cost/target" | jq -e '.code==0 and (.data|length)>0' >/dev/null
json "$BASE/api/integration/system" | jq -e '.code==0 and (.data|length)>=5' >/dev/null
json -X POST "$BASE/api/integration/system/OMS/health" | jq -e '.code==0 and .data.ok==true' >/dev/null
json -X POST "$BASE/api/integration/sync" -H 'Content-Type: application/json' -d '{}' | jq -e '.code==0' >/dev/null
json "$BASE/api/integration/sync-log/page" | jq -e '.code==0 and .data.total>0' >/dev/null
json "$BASE/api/system/user" | jq -e '.code==0 and (.data.records|length)>=3 and .data.total>=3' >/dev/null
json "$BASE/api/system/op-log/page" | jq -e '.code==0 and (.data.records|type=="array")' >/dev/null
echo "smoke ok: baseline=$BASELINE scenario=$SCENARIO"
