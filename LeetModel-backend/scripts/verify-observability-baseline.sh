#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"
RUN_ID="${OBSERVABILITY_RUN_ID:-$(date -u '+%s')-$$}"
SERVICE_NAME="leetmodel-obs-user-${RUN_ID}"
GATEWAY_SERVICE_NAME="leetmodel-obs-gateway-${RUN_ID}"
SMOKE_PORT="${OBSERVABILITY_SMOKE_PORT:-18081}"
GATEWAY_PORT="${OBSERVABILITY_GATEWAY_PORT:-18082}"
SMOKE_LOG="${RUNTIME_DIR}/smoke-user-service.log"
GATEWAY_LOG="${RUNTIME_DIR}/smoke-gateway-service.log"
SMOKE_PID=""
GATEWAY_PID=""

cleanup() {
  local pid
  for pid in "${GATEWAY_PID}" "${SMOKE_PID}"; do
    if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then
      continue
    fi
    kill "${pid}" 2>/dev/null || true
    for _ in {1..20}; do
      if ! kill -0 "${pid}" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "${pid}" 2>/dev/null; then
      kill -9 "${pid}" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

assert_http_ready() {
  local url="$1"
  local label="$2"
  for _ in {1..120}; do
    if curl --fail --silent --show-error "${url}" >/dev/null 2>&1; then
      echo "[通过] ${label}"
      return
    fi
    sleep 1
  done
  echo "[失败] ${label}" >&2
  return 1
}

query_oap() {
  local payload="$1"
  curl --fail --silent --show-error \
    --header 'Content-Type: application/json' \
    --data "${payload}" \
    http://127.0.0.1:12800/graphql
}

"${SCRIPT_DIR}/start-observability.sh" >/dev/null
agent_jar="$("${SCRIPT_DIR}/prepare-skywalking-agent.sh")"

cd "${BACKEND_DIR}"
docker compose up -d --wait mysql redis minio nacos >/dev/null

user_jar="${BACKEND_DIR}/user-service/target/user-service-0.0.1-SNAPSHOT.jar"
gateway_jar="${BACKEND_DIR}/gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar"
if [[ ! -f "${user_jar}" || ! -f "${gateway_jar}" ]]; then
  mvn -pl user-service,gateway-service -am -DskipTests package
fi

for port in "${SMOKE_PORT}" "${GATEWAY_PORT}"; do
  if ss -ltn "sport = :${port}" | tail -n +2 | grep -q .; then
    echo "冒烟端口 ${port} 已被占用。" >&2
    exit 1
  fi
done

mkdir -p "${RUNTIME_DIR}"
: >"${SMOKE_LOG}"

java \
  "-javaagent:${agent_jar}" \
  "-Dskywalking.agent.service_name=${SERVICE_NAME}" \
  -Dskywalking.agent.instance_name=local-smoke \
  -Dskywalking.collector.backend_service=127.0.0.1:11800 \
  -jar "${user_jar}" \
  "--server.port=${SMOKE_PORT}" \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  >"${SMOKE_LOG}" 2>&1 &
SMOKE_PID=$!

assert_http_ready "http://127.0.0.1:${SMOKE_PORT}/actuator/health/readiness" "Spring Boot 3.3/JDK 17 Agent 附加启动"
curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/internal/users/count" >/dev/null
echo "[通过] Spring MVC 请求与 MySQL JDBC 查询"

duration_start="$(date -u -d '1 hour ago' '+%Y-%m-%d %H%M')"
duration_end="$(date -u -d '5 minutes' '+%Y-%m-%d %H%M')"
for _ in {1..30}; do
  services_payload="$(jq -nc --arg start "${duration_start}" --arg end "${duration_end}" '{query:"query($duration: Duration!) { services: getAllServices(duration: $duration) { id name } }",variables:{duration:{start:$start,end:$end,step:"MINUTE"}}}')"
  services_response="$(query_oap "${services_payload}")"
  service_id="$(jq -r --arg name "${SERVICE_NAME}" '.data.services[]? | select(.name == $name) | .id' <<<"${services_response}" | head -n 1)"
  if [[ -n "${service_id}" ]]; then
    break
  fi
  sleep 2
done

if [[ -z "${service_id:-}" ]]; then
  echo "OAP 中未发现冒烟服务。" >&2
  echo "${services_response:-}" >&2
  exit 1
fi
echo "[通过] SkyWalking 服务与 JVM 指标上报"

trace_payload="$(jq -nc --arg service_id "${service_id}" --arg start "${duration_start}" --arg end "${duration_end}" '{query:"query($condition: TraceQueryCondition!) { result: queryTraces(condition: $condition) { traces { spans { traceId endpointName component } } } }",variables:{condition:{queryDuration:{start:$start,end:$end,step:"MINUTE"},serviceId:$service_id,traceState:"ALL",queryOrder:"BY_START_TIME",paging:{pageNum:1,pageSize:100}}}}')"
trace_response="$(query_oap "${trace_payload}")"
if ! jq -e '.data.result.traces | any(.spans | any((.component == "SpringMVC" or .component == "Tomcat") and .endpointName == "GET:/internal/users/count"))' <<<"${trace_response}" >/dev/null; then
  echo "OAP 中未发现 HTTP Trace。" >&2
  echo "${trace_response}" >&2
  exit 1
fi
echo "[通过] Spring MVC HTTP Trace"

if ! jq -e '.data.result.traces | any(.spans | any(.component == "mysql-connector-java"))' <<<"${trace_response}" >/dev/null; then
  echo "OAP 中未发现 MySQL JDBC Span。" >&2
  echo "${trace_response}" >&2
  exit 1
fi
echo "[通过] MySQL JDBC Span"

: >"${GATEWAY_LOG}"
java \
  "-javaagent:${agent_jar}" \
  "-Dskywalking.agent.service_name=${GATEWAY_SERVICE_NAME}" \
  -Dskywalking.agent.instance_name=local-gateway-smoke \
  -Dskywalking.collector.backend_service=127.0.0.1:11800 \
  -jar "${gateway_jar}" \
  "--server.port=${GATEWAY_PORT}" \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  >"${GATEWAY_LOG}" 2>&1 &
GATEWAY_PID=$!

assert_http_ready "http://127.0.0.1:${GATEWAY_PORT}/actuator/health/readiness" "Spring Cloud Gateway 4/WebFlux 6 Agent 附加启动"

gateway_trace_verified=false
for _ in {1..30}; do
  services_response="$(query_oap "${services_payload}")"
  gateway_service_id="$(jq -r --arg name "${GATEWAY_SERVICE_NAME}" '.data.services[]? | select(.name == $name) | .id' <<<"${services_response}" | head -n 1)"
  if [[ -n "${gateway_service_id}" ]]; then
    gateway_trace_payload="$(jq -nc --arg service_id "${gateway_service_id}" --arg start "${duration_start}" --arg end "${duration_end}" '{query:"query($condition: TraceQueryCondition!) { result: queryTraces(condition: $condition) { traces { spans { endpointName component } } } }",variables:{condition:{queryDuration:{start:$start,end:$end,step:"MINUTE"},serviceId:$service_id,traceState:"ALL",queryOrder:"BY_START_TIME",paging:{pageNum:1,pageSize:20}}}}')"
    gateway_trace_response="$(query_oap "${gateway_trace_payload}")"
    if jq -e '.data.result.traces | any(.spans | any((.component | ascii_downcase) | contains("gateway") or contains("webflux")))' <<<"${gateway_trace_response}" >/dev/null; then
      gateway_trace_verified=true
      break
    fi
  fi
  sleep 2
done

if [[ "${gateway_trace_verified}" != "true" ]]; then
  echo "OAP 中未发现 Gateway/WebFlux HTTP Trace。" >&2
  echo "${gateway_trace_response:-}" >&2
  exit 1
fi
echo "[通过] Spring Cloud Gateway 4/WebFlux 6 HTTP Trace"

prometheus_response="$(curl --fail --silent --show-error --get --data-urlencode 'query=up{job="skywalking-oap"}' http://127.0.0.1:19090/api/v1/query)"
if ! jq -e '.data.result | any(.value[1] == "1")' <<<"${prometheus_response}" >/dev/null; then
  echo "Prometheus 未成功抓取 SkyWalking OAP 指标。" >&2
  exit 1
fi
echo "[通过] Prometheus 指标抓取"

python3 - "${SMOKE_LOG}" <<'PY'
import json
import pathlib
import sys

lines = [line for line in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines() if line]
events = [json.loads(line) for line in lines]
if not events or any(event.get("schemaVersion") != "leetmodel.log.v1" for event in events):
    raise SystemExit("本地日志不是统一 JSON schema")
if not any("Started UserApplication" in event.get("message", "") for event in events):
    raise SystemExit("本地日志缺少服务启动证据")
PY
echo "[通过] 本地结构化运行日志；JDBC 执行证据由 OAP Span 验证，不输出 SQL 参数"

dependency_output="$(mvn -pl user-service dependency:tree -Dincludes='io.micrometer:micrometer-tracing*,io.opentelemetry:opentelemetry-exporter-*')"
if rg -q 'io\.micrometer:micrometer-tracing|io\.opentelemetry:opentelemetry-exporter-' <<<"${dependency_output}"; then
  echo "检测到被禁止的 Trace Exporter 依赖：" >&2
  echo "${dependency_output}" >&2
  exit 1
fi
echo "[通过] 未启用 Micrometer Tracing 或 OpenTelemetry Trace Exporter"

if [[ "${VERIFY_OAP_OUTAGE:-false}" == "true" ]]; then
  docker compose -f "${COMPOSE_FILE}" stop skywalking-oap >/dev/null
  curl --fail --silent --show-error "http://127.0.0.1:${SMOKE_PORT}/internal/users/count" >/dev/null
  echo "[通过] OAP 中断不阻塞业务请求"
  docker compose -f "${COMPOSE_FILE}" up -d --wait skywalking-oap >/dev/null
  echo "[通过] OAP 恢复"
fi

echo "OBS-01 最小运行验收通过。"
