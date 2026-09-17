#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="${OBSERVABILITY_CORRELATION_RUN_ID:-$(date -u '+%Y%m%d%H%M%S')-$$}"
RUN_ID="$(printf '%s' "${RUN_ID}" | tr -c 'A-Za-z0-9_-' '-')"
RUN_DIR="${BACKEND_DIR}/.observability-runtime/correlation-drill/${RUN_ID}"
WEBHOOK_PORT="${OBSERVABILITY_CORRELATION_WEBHOOK_PORT:-19094}"
WEBHOOK_URL="http://127.0.0.1:${WEBHOOK_PORT}"
TARGET_FILE="${BACKEND_DIR}/.observability-runtime/prometheus-targets/correlation-drill.json"
RULE_FILE="${BACKEND_DIR}/.observability-runtime/prometheus-rules/correlation-drill.yml"
WEBHOOK_PID=""
TEST_PID=""

cleanup() {
  set +e
  [[ -n "${TEST_PID}" ]] && kill "${TEST_PID}" 2>/dev/null || true
  [[ -n "${WEBHOOK_PID}" ]] && kill "${WEBHOOK_PID}" 2>/dev/null || true
  printf '[]\n' >"${TARGET_FILE}"
  printf 'groups: []\n' >"${RULE_FILE}"
  curl --silent --request POST http://127.0.0.1:19090/-/reload >/dev/null 2>&1 || true
}
trap cleanup EXIT

for command in curl docker jq mvn python3 ss; do
  command -v "${command}" >/dev/null 2>&1 || { echo "缺少 TRACE-03 联合演练命令：${command}" >&2; exit 1; }
done
if ss -ltn "sport = :${WEBHOOK_PORT}" | tail -n +2 | rg -q .; then
  echo "TRACE-03 演练端口 ${WEBHOOK_PORT} 已被占用。" >&2
  exit 1
fi

mkdir -p "${RUN_DIR}" "$(dirname "${TARGET_FILE}")" "$(dirname "${RULE_FILE}")"
"${SCRIPT_DIR}/verify-alerting-contract.sh" >/dev/null
"${SCRIPT_DIR}/start-observability.sh" >/dev/null

python3 "${SCRIPT_DIR}/alert-webhook.py" --port "${WEBHOOK_PORT}" >"${RUN_DIR}/webhook.log" 2>&1 &
WEBHOOK_PID=$!
for _ in {1..30}; do
  curl --fail --silent "${WEBHOOK_URL}/healthz" >/dev/null 2>&1 && break
  sleep 1
done
curl --fail --silent "${WEBHOOK_URL}/healthz" >/dev/null

cat >"${TARGET_FILE}" <<JSON
[{"targets":["127.0.0.1:${WEBHOOK_PORT}"],"labels":{"service":"ai-gateway-service"}}]
JSON
cat >"${RULE_FILE}" <<'YAML'
groups:
  - name: leetmodel-correlation-drill
    interval: 1s
    rules:
      - alert: LeetModelCorrelationOutboxBacklog
        expr: leetmodel_correlation_outbox_backlog == 1
        labels:
          severity: critical
          component: outbox
          drill: "true"
        annotations:
          summary: "TRACE-03 Outbox backlog drill"
          impact: "隔离演练：验证积压告警到物理发布 attempt 的联合定位。"
          current_value: "backlog=1"
          dashboard: "http://127.0.0.1:13000/d/leetmodel-reliable-messaging"
          investigation: "http://127.0.0.1:8080/api/admin/messaging"
          runbook: "docs/runbooks/observability/reliable-messaging.md#leetmodeloutboxpublishdelayed"
          recovery: "演练指标归零并核验 PENDING 事实。"
          correlation_contract: "leetmodel.correlation.v1"
          trace_operation: "Messaging/OutboxPublishAttempt"
          log_event_code: "OUTBOX_PUBLISH_RETRY"
          fact_query: "/api/admin/messaging/overview"
      - alert: LeetModelCorrelationAiUnknown
        expr: leetmodel_correlation_ai_unknown == 1
        labels:
          severity: critical
          component: ai-result
          drill: "true"
        annotations:
          summary: "TRACE-03 AI UNKNOWN drill"
          impact: "隔离演练：验证 UNKNOWN 告警到恢复 attempt、日志和 AI Call 事实的联合定位。"
          current_value: "unknown=1"
          dashboard: "http://127.0.0.1:13000/d/leetmodel-ai-resources"
          investigation: "http://127.0.0.1:8080/api/admin/ai/queue?state=FAILED"
          runbook: "docs/runbooks/observability/ai-queue-and-unknown.md#leetmodelaiupstreamresultunknown"
          recovery: "演练指标归零并保留 UNKNOWN，不自动重放。"
          correlation_contract: "leetmodel.correlation.v1"
          trace_operation: "AI/RecoveryAttempt"
          log_event_code: "AI_CALL_RESULT_UNKNOWN"
          fact_query: "/api/admin/ai/queue?state=FAILED"
YAML
curl --fail --silent --request POST http://127.0.0.1:19090/-/reload >/dev/null

OUTBOX_FACT="${RUN_DIR}/outbox-fact.json"
AI_FACT="${RUN_DIR}/ai-fact.json"
OUTBOX_ALERT="${RUN_DIR}/outbox-alert.json"
AI_ALERT="${RUN_DIR}/ai-alert.json"
TEST_LOG="${RUN_DIR}/integration.log"
agent_jar="$(${SCRIPT_DIR}/prepare-skywalking-agent.sh)"
agent_args="-javaagent:${agent_jar} -Dskywalking.agent.namespace=trace-correlation-drill -Dskywalking.agent.service_name=ai-gateway-service -Dskywalking.agent.instance_name=trace-correlation-${RUN_ID} -Dskywalking.agent.sample_n_per_3_secs=-1 -Dskywalking.correlation.auto_tag_keys=business.trace_id -Dskywalking.collector.backend_service=127.0.0.1:11800 -Dskywalking.logging.level=WARN"

(cd "${BACKEND_DIR}" && \
  RUN_OBSERVABILITY_CORRELATION_INTEGRATION=true \
  OBSERVABILITY_CORRELATION_RUN_ID="${RUN_ID}" \
  OBSERVABILITY_OUTBOX_FACT_OUTPUT="${OUTBOX_FACT}" \
  OBSERVABILITY_AI_FACT_OUTPUT="${AI_FACT}" \
  LEETMODEL_SKYWALKING_LOG_ENABLED=true \
  mvn -pl ai-gateway-service -am \
    -Dtest=ObservabilityCorrelationProtocolIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    "-DargLine=${agent_args}" test) >"${TEST_LOG}" 2>&1 &
TEST_PID=$!

for _ in {1..120}; do
  [[ -s "${OUTBOX_FACT}" && -s "${AI_FACT}" ]] && break
  if ! kill -0 "${TEST_PID}" 2>/dev/null; then
    tail -n 120 "${TEST_LOG}" >&2
    exit 1
  fi
  sleep 1
done
[[ -s "${OUTBOX_FACT}" && -s "${AI_FACT}" ]] || { echo "联合事实夹具未生成。" >&2; exit 1; }

curl --fail --silent --request POST --data-binary $'# TYPE leetmodel_correlation_outbox_backlog gauge\nleetmodel_correlation_outbox_backlog 1\n# TYPE leetmodel_correlation_ai_unknown gauge\nleetmodel_correlation_ai_unknown 1\n' "${WEBHOOK_URL}/test/metrics" >/dev/null
curl --fail --silent --request DELETE "${WEBHOOK_URL}/events" >/dev/null

firing=false
for _ in {1..60}; do
  events="$(curl --fail --silent "${WEBHOOK_URL}/events")"
  if jq -e '[.[] | select(.status == "firing") | .alerts[]?.labels.alertname] | index("LeetModelCorrelationOutboxBacklog") != null and index("LeetModelCorrelationAiUnknown") != null' <<<"${events}" >/dev/null; then
    firing=true
    break
  fi
  sleep 1
done
[[ "${firing}" == true ]] || { echo "未收到 TRACE-03 两条 firing 告警。" >&2; exit 1; }

events="$(curl --fail --silent "${WEBHOOK_URL}/events")"
jq -e '[.[] | .alerts[] | select(.labels.alertname == "LeetModelCorrelationOutboxBacklog")] | first | {status,labels,annotations}' <<<"${events}" >"${OUTBOX_ALERT}"
jq -e '[.[] | .alerts[] | select(.labels.alertname == "LeetModelCorrelationAiUnknown")] | first | {status,labels,annotations}' <<<"${events}" >"${AI_ALERT}"

correlation_ready=false
for _ in {1..90}; do
  "${SCRIPT_DIR}/query-observability-correlation.sh" --alert-file "${OUTBOX_ALERT}" --fact-file "${OUTBOX_FACT}" --allow-gaps >"${RUN_DIR}/outbox-result.json"
  "${SCRIPT_DIR}/query-observability-correlation.sh" --alert-file "${AI_ALERT}" --fact-file "${AI_FACT}" --allow-gaps >"${RUN_DIR}/ai-result.json"
  if jq -e '.joinStatus == "complete" or .joinStatus == "fallback_complete"' "${RUN_DIR}/outbox-result.json" >/dev/null \
      && jq -e '.joinStatus == "complete" or .joinStatus == "fallback_complete"' "${RUN_DIR}/ai-result.json" >/dev/null; then
    correlation_ready=true
    break
  fi
  sleep 1
done
[[ "${correlation_ready}" == true ]] || {
  echo "OAP Trace/中央日志未在时限内形成可解释联合定位。" >&2
  jq -c '{scenario,joinStatus,gaps}' "${RUN_DIR}/outbox-result.json" >&2
  jq -c '{scenario,joinStatus,gaps}' "${RUN_DIR}/ai-result.json" >&2
  exit 1
}

curl --fail --silent --request POST --data-binary $'# TYPE leetmodel_correlation_outbox_backlog gauge\nleetmodel_correlation_outbox_backlog 0\n# TYPE leetmodel_correlation_ai_unknown gauge\nleetmodel_correlation_ai_unknown 0\n' "${WEBHOOK_URL}/test/metrics" >/dev/null
resolved=false
for _ in {1..60}; do
  events="$(curl --fail --silent "${WEBHOOK_URL}/events")"
  if jq -e '[.[] | select(.status == "resolved") | .alerts[]?.labels.alertname] | index("LeetModelCorrelationOutboxBacklog") != null and index("LeetModelCorrelationAiUnknown") != null' <<<"${events}" >/dev/null; then
    resolved=true
    break
  fi
  sleep 1
done
[[ "${resolved}" == true ]] || { echo "未收到 TRACE-03 两条 resolved 告警。" >&2; exit 1; }

echo "[通过] 告警 firing/resolved、SkyWalking attempt、中央日志与持久化事实联合定位"
echo "产物目录：${RUN_DIR}"
