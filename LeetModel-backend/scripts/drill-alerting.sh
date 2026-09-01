#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime"
TOKEN_FILE="${LEETMODEL_MANAGEMENT_TOKEN_FILE:-${RUNTIME_DIR}/management-token}"
TARGET_DIR="${RUNTIME_DIR}/prometheus-targets"
RULE_DIR="${RUNTIME_DIR}/prometheus-rules"
TARGET_FILE="${TARGET_DIR}/runtime.json"
RULE_FILE="${RULE_DIR}/runtime.yml"
WEBHOOK_PORT="${ALERT_DRILL_WEBHOOK_PORT:-19094}"
WEBHOOK_URL="http://127.0.0.1:${WEBHOOK_PORT}"
WEBHOOK_LOG="${RUNTIME_DIR}/alert-webhook.log"
WEBHOOK_PID=""

export LEETMODEL_MANAGEMENT_TOKEN_FILE="${TOKEN_FILE}"
export LEETMODEL_MANAGEMENT_TOKEN_GID="${LEETMODEL_MANAGEMENT_TOKEN_GID:-$(id -g)}"

cleanup() {
  mkdir -p "${TARGET_DIR}" "${RULE_DIR}"
  printf '[]\n' >"${TARGET_FILE}"
  printf 'groups: []\n' >"${RULE_FILE}"
  curl --silent --request POST http://127.0.0.1:19090/-/reload >/dev/null 2>&1 || true
  if [[ -n "${WEBHOOK_PID}" ]] && kill -0 "${WEBHOOK_PID}" 2>/dev/null; then
    kill "${WEBHOOK_PID}" 2>/dev/null || true
    for _ in {1..20}; do
      if ! kill -0 "${WEBHOOK_PID}" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "${WEBHOOK_PID}" 2>/dev/null; then
      kill -9 "${WEBHOOK_PID}" 2>/dev/null || true
    fi
  fi
}
trap cleanup EXIT

for command in curl docker jq python3 rg ss; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少告警演练命令：${command}" >&2
    exit 1
  fi
done

if ss -ltn "sport = :${WEBHOOK_PORT}" | tail -n +2 | rg -q .; then
  echo "告警演练端口 ${WEBHOOK_PORT} 已被占用。" >&2
  exit 1
fi

"${SCRIPT_DIR}/verify-alerting-contract.sh"
"${SCRIPT_DIR}/start-observability.sh" >/dev/null

: >"${WEBHOOK_LOG}"
python3 "${SCRIPT_DIR}/alert-webhook.py" --port "${WEBHOOK_PORT}" \
  >"${WEBHOOK_LOG}" 2>&1 &
WEBHOOK_PID=$!
for _ in {1..30}; do
  if ! kill -0 "${WEBHOOK_PID}" 2>/dev/null; then
    echo "隔离 webhook 启动失败。" >&2
    cat "${WEBHOOK_LOG}" >&2
    exit 1
  fi
  if curl --fail --silent --show-error "${WEBHOOK_URL}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error "${WEBHOOK_URL}/healthz" >/dev/null
echo "[通过] loopback 隔离通知接收器"

mkdir -p "${TARGET_DIR}" "${RULE_DIR}"
cat >"${TARGET_FILE}" <<JSON
[
  {
    "targets": ["127.0.0.1:${WEBHOOK_PORT}"],
    "labels": {"service": "alert-pipeline-drill"}
  }
]
JSON
cat >"${RULE_FILE}" <<'YAML'
groups:
  - name: leetmodel-alert-pipeline-drill
    interval: 1s
    rules:
      - alert: LeetModelAlertPipelineDrill
        expr: leetmodel_alert_pipeline_drill == 1
        labels:
          severity: warning
          component: alert-drill
          drill: "true"
        annotations:
          summary: "LeetModel 告警链路隔离演练"
          impact: "仅验证本地 Prometheus 到 Alertmanager 的通知路径，不代表业务故障。"
          current_value: "value={{ $value }}"
          dashboard: "http://127.0.0.1:13000/d/leetmodel-telemetry-pipeline"
          investigation: "http://127.0.0.1:19090/alerts"
          runbook: "docs/runbooks/observability/service-and-telemetry.md"
          recovery: "演练指标归零后收到 resolved 通知。"
YAML

curl --fail --silent --show-error --request POST \
  --data-binary $'# TYPE leetmodel_alert_pipeline_drill gauge\nleetmodel_alert_pipeline_drill{scenario="one"} 1\nleetmodel_alert_pipeline_drill{scenario="two"} 1\n' \
  "${WEBHOOK_URL}/test/metrics" >/dev/null
curl --fail --silent --show-error --request DELETE "${WEBHOOK_URL}/events" >/dev/null
curl --fail --silent --show-error --request POST http://127.0.0.1:19090/-/reload >/dev/null

pipeline_firing=false
for _ in {1..45}; do
  events="$(curl --fail --silent --show-error "${WEBHOOK_URL}/events")"
  if jq -e 'any(.[]; .status == "firing" and .receiver == "local-drill-webhook" and (.alerts | length == 2))' \
      <<<"${events}" >/dev/null; then
    pipeline_firing=true
    break
  fi
  sleep 1
done
if [[ "${pipeline_firing}" != "true" ]]; then
  echo "未收到按 alertname+service 分组的 firing 通知。" >&2
  curl --silent http://127.0.0.1:19090/api/v1/alerts >&2 || true
  exit 1
fi
echo "[通过] Prometheus firing、Alertmanager 路由与分组"

curl --fail --silent --show-error --request POST \
  --data-binary $'# TYPE leetmodel_alert_pipeline_drill gauge\nleetmodel_alert_pipeline_drill{scenario="one"} 0\nleetmodel_alert_pipeline_drill{scenario="two"} 0\n' \
  "${WEBHOOK_URL}/test/metrics" >/dev/null
pipeline_resolved=false
for _ in {1..45}; do
  events="$(curl --fail --silent --show-error "${WEBHOOK_URL}/events")"
  if jq -e 'any(.[]; .status == "resolved" and .receiver == "local-drill-webhook")' \
      <<<"${events}" >/dev/null; then
    pipeline_resolved=true
    break
  fi
  sleep 1
done
if [[ "${pipeline_resolved}" != "true" ]]; then
  echo "未收到 resolved 通知。" >&2
  exit 1
fi
echo "[通过] 恢复通知"

now="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
future="$(date -u -d '5 minutes' '+%Y-%m-%dT%H:%M:%SZ')"
past="$(date -u -d '1 minute ago' '+%Y-%m-%dT%H:%M:%SZ')"
started_past="$(date -u -d '2 minutes ago' '+%Y-%m-%dT%H:%M:%SZ')"

curl --fail --silent --show-error --request DELETE "${WEBHOOK_URL}/events" >/dev/null
inhibit_payload="$(jq -nc --arg now "${now}" --arg future "${future}" '[
  {labels:{alertname:"LeetModelInhibitDrill",service:"alert-inhibit-drill",severity:"warning",drill:"true"},annotations:{summary:"warning"},startsAt:$now,endsAt:$future},
  {labels:{alertname:"LeetModelInhibitDrill",service:"alert-inhibit-drill",severity:"critical",drill:"true"},annotations:{summary:"critical"},startsAt:$now,endsAt:$future}
]')"
curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data "${inhibit_payload}" http://127.0.0.1:19093/api/v2/alerts >/dev/null
inhibition_verified=false
for _ in {1..15}; do
  active_alerts="$(curl --fail --silent --show-error http://127.0.0.1:19093/api/v2/alerts)"
  events="$(curl --fail --silent --show-error "${WEBHOOK_URL}/events")"
  if jq -e 'any(.[]; .labels.alertname == "LeetModelInhibitDrill" and .labels.severity == "warning" and (.status.inhibitedBy | length > 0))' \
      <<<"${active_alerts}" >/dev/null \
      && jq -e '[.[] | .alerts[] | select(.labels.alertname == "LeetModelInhibitDrill") | .labels.severity] | index("critical") != null and index("warning") == null' \
      <<<"${events}" >/dev/null; then
    inhibition_verified=true
    break
  fi
  sleep 1
done
if [[ "${inhibition_verified}" != "true" ]]; then
  echo "critical 对 warning 的抑制验证失败。" >&2
  exit 1
fi
echo "[通过] critical 抑制同服务同告警 warning"

resolve_inhibit="$(jq -nc --arg started "${started_past}" --arg past "${past}" '[
  {labels:{alertname:"LeetModelInhibitDrill",service:"alert-inhibit-drill",severity:"warning",drill:"true"},annotations:{summary:"warning"},startsAt:$started,endsAt:$past},
  {labels:{alertname:"LeetModelInhibitDrill",service:"alert-inhibit-drill",severity:"critical",drill:"true"},annotations:{summary:"critical"},startsAt:$started,endsAt:$past}
]')"
curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data "${resolve_inhibit}" http://127.0.0.1:19093/api/v2/alerts >/dev/null

silence_payload="$(jq -nc --arg now "${now}" --arg future "${future}" '{
  matchers:[{name:"service",value:"alert-silence-drill",isRegex:false,isEqual:true}],
  startsAt:$now,endsAt:$future,createdBy:"LeetModel alert drill",comment:"isolated silence verification"
}')"
silence_response="$(curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data "${silence_payload}" http://127.0.0.1:19093/api/v2/silences)"
silence_id="$(jq -r '.silenceID' <<<"${silence_response}")"
if [[ -z "${silence_id}" || "${silence_id}" == "null" ]]; then
  echo "创建隔离静默失败。" >&2
  exit 1
fi
curl --fail --silent --show-error --request DELETE "${WEBHOOK_URL}/events" >/dev/null
silenced_alert="$(jq -nc --arg now "${now}" --arg future "${future}" '[{
  labels:{alertname:"LeetModelSilenceDrill",service:"alert-silence-drill",severity:"warning",drill:"true"},
  annotations:{summary:"silence"},startsAt:$now,endsAt:$future
}]')"
curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data "${silenced_alert}" http://127.0.0.1:19093/api/v2/alerts >/dev/null
sleep 3
active_alerts="$(curl --fail --silent --show-error http://127.0.0.1:19093/api/v2/alerts)"
events="$(curl --fail --silent --show-error "${WEBHOOK_URL}/events")"
if ! jq -e 'any(.[]; .labels.alertname == "LeetModelSilenceDrill" and (.status.silencedBy | length > 0))' \
    <<<"${active_alerts}" >/dev/null \
    || jq -e 'any(.[]; any(.alerts[]; .labels.alertname == "LeetModelSilenceDrill"))' \
      <<<"${events}" >/dev/null; then
  echo "静默验证失败。" >&2
  echo "${active_alerts}" >&2
  echo "${events}" >&2
  exit 1
fi
curl --fail --silent --show-error --request DELETE \
  "http://127.0.0.1:19093/api/v2/silence/${silence_id}" >/dev/null
resolved_silence="$(jq -nc --arg started "${started_past}" --arg past "${past}" '[{
  labels:{alertname:"LeetModelSilenceDrill",service:"alert-silence-drill",severity:"warning",drill:"true"},
  annotations:{summary:"silence"},startsAt:$started,endsAt:$past
}]')"
curl --fail --silent --show-error --header 'Content-Type: application/json' \
  --data "${resolved_silence}" http://127.0.0.1:19093/api/v2/alerts >/dev/null
echo "[通过] 静默创建、生效与删除"

echo "MET-04 告警触发、分组、抑制、静默与恢复演练通过。"
