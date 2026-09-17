#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"
OBSERVABILITY_DIR="${BACKEND_DIR}/docker/observability"
RUNTIME=false

if [[ "${1:-}" == "--runtime" ]]; then
  RUNTIME=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--runtime]" >&2
  exit 2
fi

reporter="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/SkyWalkingLogReporterAppender.java"
metrics="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/SkyWalkingLogReporterMetrics.java"
logback="${BACKEND_DIR}/common/common-core/src/main/resources/leetmodel-logback-spring.xml"
logging_config="${BACKEND_DIR}/common/common-core/src/main/resources/leetmodel-logging.yml"
lal="${OBSERVABILITY_DIR}/skywalking/lal/leetmodel.yaml"
oap_log4j="${OBSERVABILITY_DIR}/skywalking/log4j2.xml"
mtail_program="${OBSERVABILITY_DIR}/skywalking/mtail/leetmodel_oap_lal.mtail"

for required in "${reporter}" "${metrics}" "${logback}" "${logging_config}" "${lal}" \
    "${oap_log4j}" "${mtail_program}" "${COMPOSE_FILE}"; do
  if [[ ! -s "${required}" ]]; then
    echo "缺少 SkyWalking 日志链路文件：${required}" >&2
    exit 1
  fi
done

for marker in 'LinkedBlockingDeque' 'setDaemon(true)' 'connectTimeout' \
    'requestTimeoutMillis' 'maxAttempts' 'queueCapacity' 'droppedQueueLow' \
    'droppedQueueHigh' 'droppedSend' 'droppedShutdown'; do
  if ! rg -Fq "${marker}" "${reporter}" "${metrics}"; then
    echo "Reporter 缺少有界或可度量故障语义：${marker}" >&2
    exit 1
  fi
done

for marker in 'CONSOLE' 'LOCAL_ROLLING' 'SKYWALKING_REPORTER' \
    'leetmodel.logging.reporter.enabled' '/v3/logs'; do
  if ! rg -q "${marker}" "${logback}"; then
    echo "统一 Logback 缺少输出链路：${marker}" >&2
    exit 1
  fi
done

if ! rg -Fq 'enabled: ${LEETMODEL_SKYWALKING_LOG_ENABLED:false}' "${logging_config}"; then
  echo "Reporter 配置缺少环境变量到 Spring 属性的映射。" >&2
  exit 1
fi

for marker in 'abortOnFailure true' 'leetmodel.log.v1' 'business_trace_id' \
    'sw_trace_id' 'domain_task_id' 'ai_call_id' 'dropper'; do
  if ! rg -q "${marker}" "${lal}"; then
    echo "OAP LAL 缺少 schema 或关联字段契约：${marker}" >&2
    exit 1
  fi
done

if ! rg -q 'SW_NAMESPACE: leetmodel-observability' "${COMPOSE_FILE}" \
    || ! rg -q 'SW_SEARCHABLE_LOGS_TAG_KEYS:.*business_trace_id.*sw_trace_id.*domain_task_id.*ai_call_id' \
      "${COMPOSE_FILE}" \
    || ! rg -q 'skywalking-oap-logs:/skywalking/logs' "${COMPOSE_FILE}" \
    || ! rg -q 'skywalking-log-metrics:' "${COMPOSE_FILE}"; then
  echo "OAP 独立存储、搜索标签或解析错误指标出口未完整配置。" >&2
  exit 1
fi

docker compose -f "${COMPOSE_FILE}" config --quiet
docker run --rm \
  -v "${OBSERVABILITY_DIR}/skywalking/mtail:/etc/mtail:ro" \
  ghcr.io/google/mtail:3.0.8 \
  --progs=/etc/mtail --compile_only --logtostderr >/dev/null
docker run --rm --entrypoint=/bin/promtool \
  -v "${OBSERVABILITY_DIR}/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-rules:/etc/prometheus/rules:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-targets:/etc/prometheus/targets:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-targets:/etc/prometheus/runtime-targets:ro" \
  -v "${OBSERVABILITY_DIR}/prometheus-rules:/etc/prometheus/runtime-rules:ro" \
  -v "/etc/hostname:/run/secrets/management_token:ro" \
  prom/prometheus:v3.14.0 check config /etc/prometheus/prometheus.yml >/dev/null

echo "[通过] Reporter 有界/fail-open、OAP LAL、独立存储与解析错误指标静态契约"

if [[ "${RUNTIME}" != "true" ]]; then
  exit 0
fi

RUN_ID="${SKYWALKING_LOG_RUN_ID:-$(date -u '+%s')-$$}"
SERVICE_NAME="leetmodel-log-runtime-${RUN_ID}"
INSTANCE_NAME="log-runtime-${RUN_ID}"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime/skywalking-logging/${RUN_ID}"
SERVICE_PORT="${SKYWALKING_LOG_SERVICE_PORT:-18096}"
FAKE_OAP_PORT="${SKYWALKING_LOG_FAKE_OAP_PORT:-18098}"
TOKEN_FILE="${LEETMODEL_MANAGEMENT_TOKEN_FILE:-${BACKEND_DIR}/.observability-runtime/management-token}"
STDOUT_LOG="${RUNTIME_DIR}/stdout.log"
ROLLING_LOG="${RUNTIME_DIR}/${SERVICE_NAME}.json.log"
FAKE_LOG="${RUNTIME_DIR}/fake-oap.log"
FAKE_MODE="${RUNTIME_DIR}/fake-oap.mode"
SERVICE_PID=""
FAKE_PID=""
management_curl_args=()

cleanup() {
  local pid
  for pid in "${SERVICE_PID}" "${FAKE_PID}"; do
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

for port in "${SERVICE_PORT}" "${FAKE_OAP_PORT}"; do
  if ss -ltn "sport = :${port}" | tail -n +2 | grep -q .; then
    echo "LOG-03 验收端口 ${port} 已被占用。" >&2
    exit 1
  fi
done

mkdir -p "${RUNTIME_DIR}"
: >"${STDOUT_LOG}"
: >"${FAKE_LOG}"

"${SCRIPT_DIR}/start-observability.sh" >/dev/null
if [[ -s "${TOKEN_FILE}" ]]; then
  MANAGEMENT_TOKEN_VALUE="$(<"${TOKEN_FILE}")"
  management_curl_args=(-H "X-LeetModel-Management-Token: ${MANAGEMENT_TOKEN_VALUE}")
else
  MANAGEMENT_TOKEN_VALUE=""
fi

cd "${BACKEND_DIR}"
if [[ "${SKYWALKING_LOG_SKIP_BUILD:-false}" != "true" ]]; then
  mvn -pl knowledge-retrieval-service -am -DskipTests package >/dev/null
fi
artifact="${BACKEND_DIR}/knowledge-retrieval-service/target/knowledge-retrieval-service-0.0.1-SNAPSHOT.jar"
if [[ ! -f "${artifact}" ]]; then
  echo "缺少 knowledge-retrieval-service 可执行包。" >&2
  exit 1
fi

wait_ready() {
  for _ in {1..90}; do
    if [[ -n "${SERVICE_PID}" ]] && ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
      echo "LOG-03 临时服务提前退出。" >&2
      tail -n 60 "${STDOUT_LOG}" >&2
      return 1
    fi
    if curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/health/readiness" \
        >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  echo "LOG-03 临时服务未在 90 秒内就绪。" >&2
  return 1
}

stop_service() {
  if [[ -z "${SERVICE_PID}" ]] || ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
    SERVICE_PID=""
    return
  fi
  kill "${SERVICE_PID}" 2>/dev/null || true
  for _ in {1..30}; do
    if ! kill -0 "${SERVICE_PID}" 2>/dev/null; then
      SERVICE_PID=""
      return
    fi
    sleep 1
  done
  kill -9 "${SERVICE_PID}" 2>/dev/null || true
  SERVICE_PID=""
}

start_service() {
  local endpoint="$1"
  local queue_capacity="$2"
  : >"${STDOUT_LOG}"
  env \
    "MANAGEMENT_TOKEN=${MANAGEMENT_TOKEN_VALUE}" \
    "LEETMODEL_LOG_DIR=${RUNTIME_DIR}" \
    LEETMODEL_SKYWALKING_LOG_ENABLED=true \
    "LEETMODEL_SKYWALKING_LOG_ENDPOINT=${endpoint}" \
    "LEETMODEL_SKYWALKING_LOG_QUEUE_CAPACITY=${queue_capacity}" \
    LEETMODEL_SKYWALKING_LOG_BATCH_SIZE=1 \
    LEETMODEL_SKYWALKING_LOG_FLUSH_MS=10 \
    LEETMODEL_SKYWALKING_LOG_CONNECT_TIMEOUT_MS=100 \
    LEETMODEL_SKYWALKING_LOG_REQUEST_TIMEOUT_MS=150 \
    LEETMODEL_SKYWALKING_LOG_MAX_ATTEMPTS=1 \
    LEETMODEL_SKYWALKING_LOG_RETRY_BACKOFF_MS=10 \
    "SERVICE_INSTANCE=${INSTANCE_NAME}" \
    SERVICE_VERSION=0.0.1-log-contract \
    java -jar "${artifact}" \
    "--spring.application.name=${SERVICE_NAME}" \
    "--server.port=${SERVICE_PORT}" \
    --spring.profiles.active=test \
    --spring.cloud.nacos.discovery.enabled=false \
    --spring.cloud.nacos.config.enabled=false \
    >"${STDOUT_LOG}" 2>&1 &
  SERVICE_PID=$!
  wait_ready
}

emit_request() {
  local trace_id="$1"
  curl --silent --show-error \
    --header 'Content-Type: application/json' \
    --header "X-Trace-Id: ${trace_id}" \
    --data '{"query":"log contract","topK":1,"tokenBudget":128}' \
    "http://127.0.0.1:${SERVICE_PORT}/internal/knowledge-retrieval/runs" >/dev/null || true
}

prometheus_text() {
  curl -fsS "${management_curl_args[@]}" \
    "http://127.0.0.1:${SERVICE_PORT}/actuator/prometheus"
}

metric_value() {
  local text="$1"
  local outcome="$2"
  local cause="$3"
  awk -v outcome="${outcome}" -v cause="${cause}" '
    /^leetmodel_logging_reporter_events_total\{/ &&
      index($0, "outcome=\"" outcome "\"") &&
      index($0, "cause=\"" cause "\"") { print $NF; found=1 }
    END { if (!found) print 0 }
  ' <<<"${text}" | tail -n 1
}

query_logs() {
  local service="$1"
  local key="$2"
  local value="$3"
  local start end payload
  start="$(date -u -d '15 minutes ago' '+%Y-%m-%d %H%M')"
  end="$(date -u -d '10 minutes' '+%Y-%m-%d %H%M')"
  payload="$(jq -nc --arg service "${service}" --arg key "${key}" --arg value "${value}" \
    --arg start "${start}" --arg end "${end}" \
    '{query:"query($condition: LogQueryConditionByName!) { result: queryLogsByName(condition: $condition) { errorReason logs { traceId content tags { key value } } } }",variables:{condition:{service:{serviceName:$service,layer:"GENERAL"},queryDuration:{start:$start,end:$end,step:"MINUTE"},tags:[{key:$key,value:$value}],paging:{pageNum:1,pageSize:100},queryOrder:"DES"}}}')"
  curl -fsS --header 'Content-Type: application/json' --data-binary "${payload}" \
    http://127.0.0.1:12800/graphql
}

runtime_trace="log-runtime-${RUN_ID}"
start_service 'http://127.0.0.1:12800/v3/logs' 64
emit_request "${runtime_trace}"

reporter_ready=false
for _ in {1..30}; do
  reporter_metrics="$(prometheus_text)"
  success="$(metric_value "${reporter_metrics}" succeeded none)"
  if awk -v value="${success}" 'BEGIN { exit !(value > 0) }'; then
    reporter_ready=true
    break
  fi
  sleep 1
done
if [[ "${reporter_ready}" != "true" ]]; then
  echo "Reporter 未暴露成功指标。" >&2
  exit 1
fi

indexed=false
for _ in {1..40}; do
  response="$(query_logs "${SERVICE_NAME}" business_trace_id "${runtime_trace}")"
  if jq -e --arg trace "${runtime_trace}" \
      '.data.result.logs | any(.content | fromjson | .traceId == $trace)' \
      <<<"${response}" >/dev/null; then
    indexed=true
    break
  fi
  sleep 1
done
if [[ "${indexed}" != "true" ]]; then
  echo "OAP 中未发现 Reporter 上报的业务 traceId。" >&2
  echo "${response:-}" >&2
  exit 1
fi
echo "[通过] 应用 Reporter -> OAP -> BanyanDB -> GraphQL，并可按业务 traceId 查询"

stop_service

printf 'slow\n' >"${FAKE_MODE}"
python3 - "${FAKE_OAP_PORT}" "${FAKE_MODE}" >"${FAKE_LOG}" 2>&1 <<'PY' &
import http.server
import pathlib
import sys
import time

port = int(sys.argv[1])
mode_file = pathlib.Path(sys.argv[2])

class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        mode = mode_file.read_text(encoding="utf-8").strip()
        if mode == "slow":
            time.sleep(2)
            status = 503
        else:
            status = 200
        self.send_response(status)
        self.end_headers()

    def log_message(self, _format, *_args):
        return

http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
FAKE_PID=$!

for _ in {1..30}; do
  if ss -ltn "sport = :${FAKE_OAP_PORT}" | tail -n +2 | grep -q .; then
    break
  fi
  sleep 1
done

start_service "http://127.0.0.1:${FAKE_OAP_PORT}/v3/logs" 4
started_ns="$(date +%s%N)"
for index in {1..60}; do
  emit_request "slow-${RUN_ID}-${index}"
done
elapsed_ns="$(( $(date +%s%N) - started_ns ))"
elapsed_ms="$(( elapsed_ns / 1000000 ))"
if (( elapsed_ns > 15000000000 )); then
  echo "慢 OAP 使业务请求耗时异常：${elapsed_ms}ms" >&2
  exit 1
fi

failure_observed=false
for _ in {1..30}; do
  reporter_metrics="$(prometheus_text)"
  failed="$(metric_value "${reporter_metrics}" failed transport)"
  dropped="$(metric_value "${reporter_metrics}" dropped queue_full_low_priority)"
  if awk -v failed="${failed}" -v dropped="${dropped}" \
      'BEGIN { exit !(failed > 0 && dropped > 0) }'; then
    failure_observed=true
    break
  fi
  sleep 1
done
if [[ "${failure_observed}" != "true" ]]; then
  echo "慢消费/队列溢出未产生失败与丢弃指标。" >&2
  exit 1
fi
if ! awk '/^leetmodel_logging_reporter_queue_capacity(\{| )/ { if ($NF != 4) exit 1; seen=1 }
    /^leetmodel_logging_reporter_queue_depth(\{| )/ { if ($NF > 4) exit 1; depth=1 }
    END { exit !(seen && depth) }' <<<"${reporter_metrics}"; then
  echo "Reporter 队列容量或深度不满足有界契约。" >&2
  exit 1
fi
if [[ ! -s "${STDOUT_LOG}" || ! -s "${ROLLING_LOG}" ]]; then
  echo "慢消费期间 stdout 或本地轮转日志缺失。" >&2
  exit 1
fi
echo "[通过] 慢消费不反压业务，队列有界且优先丢弃低级别日志"

kill "${FAKE_PID}" 2>/dev/null || true
wait "${FAKE_PID}" 2>/dev/null || true
FAKE_PID=""
before_disconnect="$(metric_value "${reporter_metrics}" failed transport)"
for index in {1..5}; do
  emit_request "disconnect-${RUN_ID}-${index}"
done
disconnect_observed=false
for _ in {1..20}; do
  reporter_metrics="$(prometheus_text)"
  after_disconnect="$(metric_value "${reporter_metrics}" failed transport)"
  if awk -v before="${before_disconnect}" -v after="${after_disconnect}" \
      'BEGIN { exit !(after > before) }'; then
    disconnect_observed=true
    break
  fi
  sleep 1
done
if [[ "${disconnect_observed}" != "true" ]]; then
  echo "OAP 断开未产生传输失败指标。" >&2
  exit 1
fi
if ! curl -fsS "http://127.0.0.1:${SERVICE_PORT}/actuator/health/liveness" >/dev/null; then
  echo "OAP 断开污染了业务 Liveness。" >&2
  exit 1
fi
echo "[通过] OAP 断开时业务存活且失败可度量"

printf 'success\n' >"${FAKE_MODE}"
python3 - "${FAKE_OAP_PORT}" "${FAKE_MODE}" >"${FAKE_LOG}" 2>&1 <<'PY' &
import http.server
import pathlib
import sys

port = int(sys.argv[1])
mode_file = pathlib.Path(sys.argv[2])

class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        self.rfile.read(length)
        self.send_response(200 if mode_file.read_text().strip() == "success" else 503)
        self.end_headers()

    def log_message(self, _format, *_args):
        return

http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
PY
FAKE_PID=$!
for _ in {1..30}; do
  if ss -ltn "sport = :${FAKE_OAP_PORT}" | tail -n +2 | grep -q .; then
    break
  fi
  sleep 1
done
emit_request "recovery-${RUN_ID}"
recovered=false
for _ in {1..30}; do
  reporter_metrics="$(prometheus_text)"
  recovered_count="$(metric_value "${reporter_metrics}" recovered transport)"
  if awk -v value="${recovered_count}" 'BEGIN { exit !(value > 0) }' \
      && rg -q '^leetmodel_logging_reporter_connected(\{[^}]*\})? 1(\.0)?$' \
        <<<"${reporter_metrics}"; then
    recovered=true
    break
  fi
  sleep 1
done
if [[ "${recovered}" != "true" ]]; then
  echo "Reporter 恢复后未产生恢复计数或 connected=1。" >&2
  exit 1
fi
echo "[通过] Reporter 恢复可度量"

direct_id="log-direct-${RUN_ID}"
now_ms="$(( $(date +%s) * 1000 ))"
valid_body="$(jq -nc --arg id "${direct_id}" '{schemaVersion:"leetmodel.log.v1",timestamp:"2026-09-02T00:00:00Z",level:"INFO",service:"leetmodel-log-contract",environment:"verification",serviceVersion:"test",instance:"direct",logger:"verify",thread:"main",message:("valid-"+$id),eventCode:"LOG_REPORT_VALIDATED",traceId:("trace-"+$id),swTraceId:("sw-"+$id),domainTaskId:("task-"+$id),aiCallId:("call-"+$id),durationMs:17,statusCode:202,retryCount:2}')"
invalid_body="$(jq -nc --arg id "${direct_id}" '{schemaVersion:"invalid.schema",service:"leetmodel-log-contract",instance:"direct",message:("invalid-"+$id)}')"
for kind in valid invalid malformed; do
  if [[ "${kind}" == valid ]]; then
    body="${valid_body}"
  elif [[ "${kind}" == invalid ]]; then
    body="${invalid_body}"
  else
    body="{malformed-${direct_id}"
  fi
  payload="$(jq -nc --argjson timestamp "${now_ms}" --arg body "${body}" \
    '[{timestamp:$timestamp,service:"leetmodel-log-contract",serviceInstance:"direct",layer:"GENERAL",body:{json:{json:$body}}}]')"
  curl -fsS --header 'Content-Type: application/json' --data-binary "${payload}" \
    http://127.0.0.1:12800/v3/logs >/dev/null
done

for key in business_trace_id sw_trace_id domain_task_id ai_call_id; do
  case "${key}" in
    business_trace_id) value="trace-${direct_id}" ;;
    sw_trace_id) value="sw-${direct_id}" ;;
    domain_task_id) value="task-${direct_id}" ;;
    ai_call_id) value="call-${direct_id}" ;;
  esac
  found=false
  for _ in {1..40}; do
    response="$(query_logs leetmodel-log-contract "${key}" "${value}")"
    if jq -e --arg marker "valid-${direct_id}" \
        '.data.result.logs | any(.content | contains($marker))' <<<"${response}" >/dev/null; then
      found=true
      break
    fi
    sleep 1
  done
  if [[ "${found}" != "true" ]]; then
    echo "关联标签不可查询：${key}" >&2
    exit 1
  fi
done

all_logs="$(query_logs leetmodel-log-contract schema_version leetmodel.log.v1)"
if jq -e --arg invalid "invalid-${direct_id}" --arg malformed "malformed-${direct_id}" \
    '.data.result.logs | any(.content | contains($invalid) or contains($malformed))' \
    <<<"${all_logs}" >/dev/null; then
  echo "非法 schema 或畸形 JSON 污染了正常日志索引。" >&2
  exit 1
fi
if ! jq -e --arg marker "valid-${direct_id}" \
    '.data.result.logs[] | select(.content | contains($marker)) | .content | fromjson |
     (.durationMs | type) == "number" and (.statusCode | type) == "number" and
     (.retryCount | type) == "number"' <<<"${all_logs}" >/dev/null; then
  echo "OAP 中结构化数字字段类型未保留。" >&2
  exit 1
fi

lal_metrics=false
for _ in {1..30}; do
  oap_metrics="$(curl -fsS http://127.0.0.1:13903/metrics)"
  if rg -q '^leetmodel_oap_lal_events_total\{[^}]*cause="invalid_json"[^}]*outcome="rejected"' \
      <<<"${oap_metrics}" \
      && rg -q '^leetmodel_oap_lal_events_total\{[^}]*cause="schema_or_policy"[^}]*outcome="rejected"' \
        <<<"${oap_metrics}"; then
    lal_metrics=true
    break
  fi
  sleep 1
done
if [[ "${lal_metrics}" != "true" ]]; then
  echo "OAP LAL 非法 JSON/schema 未产生解析拒绝指标。" >&2
  exit 1
fi

prometheus_lal=false
for _ in {1..10}; do
  response="$(curl -fsS --get --data-urlencode 'query=leetmodel_oap_lal_events_total' \
    http://127.0.0.1:19090/api/v1/query)"
  if jq -e '.data.result | length >= 2' <<<"${response}" >/dev/null; then
    prometheus_lal=true
    break
  fi
  sleep 3
done
if [[ "${prometheus_lal}" != "true" ]]; then
  echo "Prometheus 未抓取 OAP LAL 拒绝指标。" >&2
  exit 1
fi

echo "[通过] 四类关联查询、字段类型、非法 schema/JSON 拒绝及 Prometheus 解析错误指标"
echo "LOG-03 真实运行验收通过。"
