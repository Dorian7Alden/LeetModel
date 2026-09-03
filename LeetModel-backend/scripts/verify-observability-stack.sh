#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"
OBSERVABILITY_DIR="${BACKEND_DIR}/docker/observability"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime"
TOKEN_FILE="${LEETMODEL_MANAGEMENT_TOKEN_FILE:-${RUNTIME_DIR}/management-token}"
RUNTIME_TARGETS_DIR="${RUNTIME_DIR}/prometheus-targets"
RUNTIME_TARGET_FILE="${RUNTIME_TARGETS_DIR}/runtime.json"
SMOKE_PORT="${OBSERVABILITY_METRICS_SMOKE_PORT:-18094}"
SMOKE_INSTANCE="127.0.0.1:${SMOKE_PORT}"
SMOKE_LOG="${RUNTIME_DIR}/metrics-gateway-smoke.log"
SMOKE_PID=""
PROMETHEUS_STOPPED=false
STATIC_ONLY=false

export LEETMODEL_MANAGEMENT_TOKEN_FILE="${TOKEN_FILE}"
export LEETMODEL_MANAGEMENT_TOKEN_GID="${LEETMODEL_MANAGEMENT_TOKEN_GID:-$(id -g)}"

if [[ "${1:-}" == "--static" ]]; then
  STATIC_ONLY=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--static]" >&2
  exit 2
fi

for command in docker curl jq rg; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少验收命令：${command}" >&2
    exit 1
  fi
done

cleanup() {
  if [[ "${PROMETHEUS_STOPPED}" == "true" ]]; then
    docker compose -f "${COMPOSE_FILE}" up -d --wait prometheus >/dev/null 2>&1 || true
  fi
  if [[ -n "${SMOKE_PID}" ]] && kill -0 "${SMOKE_PID}" 2>/dev/null; then
    kill "${SMOKE_PID}" 2>/dev/null || true
    for _ in {1..20}; do
      if ! kill -0 "${SMOKE_PID}" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "${SMOKE_PID}" 2>/dev/null; then
      kill -9 "${SMOKE_PID}" 2>/dev/null || true
    fi
  fi
  if [[ -d "${RUNTIME_TARGETS_DIR}" ]]; then
    printf '[]\n' >"${RUNTIME_TARGET_FILE}"
    curl --fail --silent --show-error --request POST \
      http://127.0.0.1:19090/-/reload >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

docker compose -f "${COMPOSE_FILE}" config --quiet
echo "[通过] Compose 配置"

dashboard_count=0
for dashboard in "${OBSERVABILITY_DIR}"/grafana/dashboards/*.json; do
  jq -e '.uid and .title and (.panels | length > 0)' "${dashboard}" >/dev/null
  dashboard_count=$((dashboard_count + 1))
done
if [[ "${dashboard_count}" -ne 6 ]]; then
  echo "Grafana 版本化看板应为 6 个，实际为 ${dashboard_count} 个。" >&2
  exit 1
fi
echo "[通过] 六类 Grafana 看板 JSON"

targets_file="${OBSERVABILITY_DIR}/prometheus-targets/leetmodel-services.json"
expected_services='["admin-service","ai-assistant-service","ai-evaluation-service","ai-gateway-service","ai-review-service","ai-suggestion-service","audit-service","gateway-service","knowledge-retrieval-service","problem-service","ranking-service","submission-service","team-service","user-service"]'
if ! jq -e --argjson expected "${expected_services}" \
    'length == 14 and ([.[].labels.service] | sort | unique) == $expected' \
    "${targets_file}" >/dev/null; then
  echo "Prometheus 服务发现文件没有且仅有 14 个固定服务。" >&2
  exit 1
fi
echo "[通过] 14 个服务抓取目标"

if rg -n --glob 'application*.yml' 'Path=/actuator|Path=/actuator/\*\*' \
    "${BACKEND_DIR}/gateway-service/src/main/resources" >/dev/null; then
  echo "Gateway 路由不得代理 Actuator 管理端点。" >&2
  exit 1
fi
if git -C "${BACKEND_DIR}" ls-files --error-unmatch .observability-runtime/management-token \
    >/dev/null 2>&1; then
  echo "管理 Token 不得进入版本库。" >&2
  exit 1
fi
echo "[通过] 管理端点未路由且密钥未入库"

docker run --rm --entrypoint=/bin/promtool \
  -v "${OBSERVABILITY_DIR}/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-rules:/etc/prometheus/rules:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-targets:/etc/prometheus/targets:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-targets:/etc/prometheus/runtime-targets:ro" \
  -v "/etc/hostname:/run/secrets/management_token:ro" \
  prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml >/dev/null
docker run --rm --entrypoint=/bin/amtool \
  -v "${OBSERVABILITY_DIR}/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
  prom/alertmanager:v0.34.0 check-config /etc/alertmanager/alertmanager.yml >/dev/null
echo "[通过] Prometheus 规则加载与 Alertmanager 路由"

if [[ "${STATIC_ONLY}" == "true" ]]; then
  echo "MET-03 静态验收通过。"
  exit 0
fi

if ss -ltn "sport = :${SMOKE_PORT}" | tail -n +2 | rg -q .; then
  echo "指标冒烟端口 ${SMOKE_PORT} 已被占用。" >&2
  exit 1
fi

"${SCRIPT_DIR}/start-observability.sh" >/dev/null
management_token="$(<"${TOKEN_FILE}")"
if [[ -z "${management_token}" ]]; then
  echo "管理 Token 文件为空。" >&2
  exit 1
fi

gateway_jar="${BACKEND_DIR}/gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar"
if [[ ! -f "${gateway_jar}" ]]; then
  (cd "${BACKEND_DIR}" && mvn -pl gateway-service -am -DskipTests package)
fi

: >"${SMOKE_LOG}"
MANAGEMENT_TOKEN="${management_token}" java -jar "${gateway_jar}" \
  "--server.port=${SMOKE_PORT}" \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  >"${SMOKE_LOG}" 2>&1 &
SMOKE_PID=$!

for _ in {1..120}; do
  if ! kill -0 "${SMOKE_PID}" 2>/dev/null; then
    echo "临时 Gateway 启动失败。" >&2
    tail -n 80 "${SMOKE_LOG}" >&2
    exit 1
  fi
  if curl --fail --silent --show-error \
      "http://127.0.0.1:${SMOKE_PORT}/actuator/health/readiness" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if ! curl --fail --silent --show-error \
    "http://127.0.0.1:${SMOKE_PORT}/actuator/health/readiness" >/dev/null; then
  echo "临时 Gateway 未在 120 秒内就绪。" >&2
  tail -n 80 "${SMOKE_LOG}" >&2
  exit 1
fi
echo "[通过] 临时 WebFlux 服务就绪"

without_token_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  "http://127.0.0.1:${SMOKE_PORT}/actuator/prometheus")"
if [[ "${without_token_status}" == "200" ]]; then
  echo "配置管理 Token 后，无 Header 的 Prometheus 请求不应成功。" >&2
  exit 1
fi
if ! curl --fail --silent --show-error \
    --header "X-LeetModel-Management-Token: ${management_token}" \
    "http://127.0.0.1:${SMOKE_PORT}/actuator/prometheus" | rg -q '^jvm_info'; then
  echo "携带管理 Token 的 Prometheus 请求失败。" >&2
  exit 1
fi
echo "[通过] 管理 Token 正向与负向验证"

mkdir -p "${RUNTIME_TARGETS_DIR}"
jq -n --arg target "${SMOKE_INSTANCE}" \
  '[{"targets": [$target], "labels": {"service": "gateway-metrics-smoke"}}]' \
  >"${RUNTIME_TARGET_FILE}"
curl --fail --silent --show-error --request POST \
  http://127.0.0.1:19090/-/reload >/dev/null

prometheus_query() {
  curl --fail --silent --show-error --get \
    --data-urlencode "query=$1" http://127.0.0.1:19090/api/v1/query
}

service_targets_ready=false
for _ in {1..30}; do
  targets_response="$(curl --fail --silent --show-error \
    'http://127.0.0.1:19090/api/v1/targets?state=any')"
  static_services="$(jq -c '[.data.activeTargets[] | select(.labels.job == "leetmodel-services" and .labels.service != "gateway-metrics-smoke") | .labels.service] | sort | unique' <<<"${targets_response}")"
  smoke_up="$(prometheus_query "up{job=\"leetmodel-services\",instance=\"${SMOKE_INSTANCE}\"}")"
  if [[ "${static_services}" == "${expected_services}" ]] \
      && jq -e '.data.result | any(.value[1] == "1")' <<<"${smoke_up}" >/dev/null; then
    service_targets_ready=true
    break
  fi
  sleep 2
done
if [[ "${service_targets_ready}" != "true" ]]; then
  echo "Prometheus 未发现 14 个固定服务，或未抓取临时服务。" >&2
  exit 1
fi
if ! prometheus_query "jvm_info{job=\"leetmodel-services\",instance=\"${SMOKE_INSTANCE}\"}" \
    | jq -e '.data.result | length > 0' >/dev/null; then
  echo "Prometheus 未查询到临时服务 JVM 指标。" >&2
  exit 1
fi
echo "[通过] 14 服务发现、受保护抓取与 PromQL 查询"

infra_ready=false
for _ in {1..20}; do
  infra_up="$(prometheus_query 'max by (job) (up{job=~"prometheus|skywalking-oap|skywalking-log-metrics|alertmanager|grafana"})')"
  if jq -e '.data.result | length == 5 and all(.value[1] == "1")' \
      <<<"${infra_up}" >/dev/null; then
    infra_ready=true
    break
  fi
  sleep 2
done
if [[ "${infra_ready}" != "true" ]]; then
  echo "Prometheus 未成功抓取全部五个观测组件。" >&2
  echo "${infra_up}" >&2
  exit 1
fi
rules_response="$(curl --fail --silent --show-error http://127.0.0.1:19090/api/v1/rules)"
if ! jq -e '.data.groups | any(.name == "leetmodel-platform-recording" and (.rules | length == 4))' \
    <<<"${rules_response}" >/dev/null; then
  echo "Prometheus 未加载平台记录规则。" >&2
  exit 1
fi
echo "[通过] 观测组件自监测与记录规则"

while IFS= read -r expression; do
  prometheus_query "${expression}" | jq -e '.status == "success"' >/dev/null
done < <(jq -r '.panels[].targets[]?.expr' "${OBSERVABILITY_DIR}"/grafana/dashboards/*.json)
echo "[通过] 六类看板 PromQL 可解析"

dashboards_response="$(curl --fail --silent --show-error \
  'http://127.0.0.1:13000/api/search?tag=leetmodel')"
if ! jq -e '[.[] | select(.type == "dash-db")] | length == 6' \
    <<<"${dashboards_response}" >/dev/null; then
  echo "Grafana 未加载六类 LeetModel 看板。" >&2
  echo "${dashboards_response}" >&2
  exit 1
fi
echo "[通过] Grafana provisioning 加载六类看板"

docker compose -f "${COMPOSE_FILE}" stop prometheus >/dev/null
PROMETHEUS_STOPPED=true
curl --fail --silent --show-error \
  "http://127.0.0.1:${SMOKE_PORT}/actuator/health/liveness" >/dev/null
curl --fail --silent --show-error \
  "http://127.0.0.1:${SMOKE_PORT}/doc.html" >/dev/null
echo "[通过] Prometheus 中断不影响应用请求"
docker compose -f "${COMPOSE_FILE}" up -d --wait prometheus >/dev/null
PROMETHEUS_STOPPED=false
curl --fail --silent --show-error http://127.0.0.1:19090/-/ready >/dev/null
echo "[通过] Prometheus 恢复"

echo "MET-03 真实运行验收通过。"
