#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"
RUNTIME=false

if [[ "${1:-}" == "--runtime" ]]; then
  RUNTIME=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--runtime]" >&2
  exit 2
fi

for command in rg jq curl java jar mvn docker ss python3; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少 TRACE-01 验收命令：${command}" >&2
    exit 1
  fi
done

start_script="${BACKEND_DIR}/scripts/start-mvp.sh"
prepare_script="${BACKEND_DIR}/scripts/prepare-skywalking-agent.sh"
bridge="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/telemetry/SkyWalkingCorrelation.java"
layout="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/LeetModelJsonLayout.java"
servlet_filter="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/filter/TraceIdServletFilter.java"
gateway_filter="${BACKEND_DIR}/gateway-service/src/main/java/com/leetmodel/gateway/filter/TraceIdFilter.java"
feign_capability="${BACKEND_DIR}/common/common-api/src/main/java/com/leetmodel/common/api/feign/SkyWalkingFeignCapability.java"

for required in "${start_script}" "${prepare_script}" "${bridge}" "${layout}" \
    "${servlet_filter}" "${gateway_filter}" "${feign_capability}"; do
  if [[ ! -s "${required}" ]]; then
    echo "缺少 TRACE-01 契约文件：${required}" >&2
    exit 1
  fi
done

expected_services=(
  user-service problem-service team-service ai-gateway-service
  submission-service ai-review-service ranking-service knowledge-retrieval-service
  ai-suggestion-service ai-assistant-service ai-evaluation-service admin-service gateway-service
)
mapfile -t configured_services < <(awk '
  /^services=\(/ { in_services=1; next }
  in_services && /^\)/ { exit }
  in_services {
    for (i = 1; i <= NF; i++) print $i
  }
' "${start_script}")
if [[ "${configured_services[*]}" != "${expected_services[*]}" ]]; then
  echo "start-mvp.sh 没有且仅有 13 个固定服务。" >&2
  printf '实际: %s\n' "${configured_services[*]}" >&2
  exit 1
fi

for marker in \
    'LEETMODEL_SKYWALKING_ENABLED' \
    'LEETMODEL_SKYWALKING_SAMPLE' \
    'skywalking.agent.service_name' \
    'skywalking.agent.namespace' \
    'skywalking.agent.instance_name' \
    'skywalking.agent.instance_properties_json' \
    'skywalking.agent.sample_n_per_3_secs' \
    'skywalking.correlation.auto_tag_keys=business.trace_id' \
    'skywalking.plugin.exclude_plugins=feign-default-http-9.x,feign-pathvar-9.x' \
    'skywalking.plugin.jdbc.trace_sql_parameters=false'; do
  if ! rg -Fq "${marker}" "${start_script}"; then
    echo "13 服务启动契约缺少：${marker}" >&2
    exit 1
  fi
done

for marker in \
    'AGENT_VERSION="9.7.0"' \
    'ARCHIVE_SHA512=' \
    'apm-spring-cloud-gateway-4.x-plugin' \
    'apm-springmvc-annotation-6.x-plugin' \
    'apm-spring-webflux-6.x-plugin' \
    'apm-toolkit-trace-activation-9.7.0.jar' \
    'apm-toolkit-logback-1.x-activation-9.7.0.jar'; do
  if ! rg -Fq "${marker}" "${prepare_script}"; then
    echo "Agent 准备契约缺少：${marker}" >&2
    exit 1
  fi
done

if ! rg -Fq '<skywalking-toolkit.version>9.6.0</skywalking-toolkit.version>' \
    "${BACKEND_DIR}/pom.xml" \
    || ! rg -Fq '<artifactId>apm-toolkit-trace</artifactId>' \
      "${BACKEND_DIR}/common/common-core/pom.xml" \
      "${BACKEND_DIR}/common/common-api/pom.xml"; then
  echo "公共模块没有统一声明与 Agent 9.7 ABI 兼容的 Toolkit Trace。" >&2
  exit 1
fi

for marker in 'TraceContext.traceId()' 'TraceContext.segmentId()' \
    'TraceContext.putCorrelation' 'BUSINESS_TRACE_TAG'; do
  if ! rg -Fq "${marker}" "${bridge}"; then
    echo "SkyWalking/MDC 桥接缺少：${marker}" >&2
    exit 1
  fi
done
if rg -Fq 'ActiveSpan.tag(BUSINESS_TRACE_TAG' "${bridge}"; then
  echo "业务 traceId 不得由桥接器重复写入 Span tag。" >&2
  exit 1
fi
for source in "${servlet_filter}" "${gateway_filter}"; do
  if ! rg -Fq 'SkyWalkingCorrelation.enrich' "${source}" \
      || ! rg -Fq 'SkyWalkingCorrelation.bindBusinessTraceId' "${source}"; then
    echo "HTTP 入口没有同时建立业务与 SkyWalking 关联：${source}" >&2
    exit 1
  fi
done
for marker in 'SkyWalkingCorrelation.traceId()' 'SkyWalkingCorrelation.spanId()'; do
  if ! rg -Fq "${marker}" "${layout}"; then
    echo "结构化日志不能从活动 Agent 上下文补齐：${marker}" >&2
    exit 1
  fi
done

for marker in 'implements Capability' 'Tracer.createExitSpan' 'ContextCarrierRef' \
    'request.header' 'methodMetadata().configKey()' 'rpc.system' 'error.kind'; do
  if ! rg -Fq "${marker}" "${feign_capability}"; then
    echo "Feign 13 有界增强缺少：${marker}" >&2
    exit 1
  fi
done
if rg -n 'request\.body\(|exception\.getMessage\(|request\.url\(\).*tag' \
    "${feign_capability}" >/dev/null; then
  echo "Feign Span 不得记录请求正文、原始异常或动态 URL。" >&2
  exit 1
fi

agent_jar="$("${prepare_script}")"
agent_home="$(dirname "${agent_jar}")"
rocket_plugin="${agent_home}/plugins/apm-rocketMQ-5.x-plugin-9.7.0.jar"
if [[ ! -f "${rocket_plugin}" ]]; then
  echo "Agent 缺少 RocketMQ 5.x 自动插件。" >&2
  exit 1
fi
rocket_plugin_entries="$(jar tf "${rocket_plugin}")"
for marker in 'MessageSendInterceptor.class' 'MessageConcurrentlyConsumeInterceptor.class'; do
  if ! rg -Fq "${marker}" <<<"${rocket_plugin_entries}"; then
    echo "RocketMQ 插件缺少协议边界：${marker}" >&2
    exit 1
  fi
done

dependency_output="$(cd "${BACKEND_DIR}" && mvn -pl gateway-service,user-service dependency:tree \
  -Dincludes='io.micrometer:micrometer-tracing*,io.opentelemetry:opentelemetry-exporter-*')"
if rg -q 'io\.micrometer:micrometer-tracing|io\.opentelemetry:opentelemetry-exporter-' \
    <<<"${dependency_output}"; then
  echo "检测到被禁止的第二套 Trace Exporter：" >&2
  echo "${dependency_output}" >&2
  exit 1
fi

echo "[通过] 13 服务 Agent 开关、资源、采样、HTTP/Feign/MDC 与唯一 Trace 实现静态契约"

if [[ "${RUNTIME}" != "true" ]]; then
  exit 0
fi

RUN_ID="${SKYWALKING_TRACE_RUN_ID:-$(date -u '+%s')-$$}"
NAMESPACE="trace-contract"
SERVICE_VERSION_VALUE="trace-contract"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime/skywalking-tracing/${RUN_ID}"
USER_PORT="${SKYWALKING_TRACE_USER_PORT:-18081}"
GATEWAY_PORT="${SKYWALKING_TRACE_GATEWAY_PORT:-18082}"
TEAM_PORT="${SKYWALKING_TRACE_TEAM_PORT:-18083}"
PROBLEM_PORT="${SKYWALKING_TRACE_PROBLEM_PORT:-18084}"
TEMP_GROUP="lm-dev%cg-trace-contract-${RUN_ID}"
APP_PIDS=()
TEMP_GROUP_CREATED=false
OAP_STOPPED=false
declare -A APP_PID_BY_NAME=()

cleanup() {
  local pid
  if [[ "${OAP_STOPPED}" == "true" ]]; then
    docker compose -f "${COMPOSE_FILE}" up -d --wait skywalking-oap >/dev/null 2>&1 || true
  fi
  for pid in "${APP_PIDS[@]:-}"; do
    if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
    fi
  done
  for pid in "${APP_PIDS[@]:-}"; do
    if [[ -z "${pid}" ]]; then continue; fi
    for _ in {1..20}; do
      if ! kill -0 "${pid}" 2>/dev/null; then break; fi
      sleep 1
    done
    if kill -0 "${pid}" 2>/dev/null; then kill -9 "${pid}" 2>/dev/null || true; fi
  done
  if [[ "${TEMP_GROUP_CREATED}" == "true" ]]; then
    (cd "${BACKEND_DIR}" && docker compose exec -T rocketmq-broker ./mqadmin deleteSubGroup \
      -n rocketmq-namesrv:9876 -c LeetModelLocalCluster -g "${TEMP_GROUP}") \
      >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

for port in "${USER_PORT}" "${GATEWAY_PORT}" "${TEAM_PORT}" "${PROBLEM_PORT}"; do
  if ss -ltn "sport = :${port}" | tail -n +2 | rg -q .; then
    echo "TRACE-01 临时端口 ${port} 已被占用。" >&2
    exit 1
  fi
done

mkdir -p "${RUNTIME_DIR}"
"${SCRIPT_DIR}/start-observability.sh" >/dev/null
(cd "${BACKEND_DIR}" && docker compose up -d --wait \
  mysql redis cache-redis minio rocketmq-namesrv rocketmq-broker) >/dev/null
"${SCRIPT_DIR}/init-rocketmq.sh" >/dev/null

if [[ "${SKYWALKING_TRACE_SKIP_BUILD:-false}" != "true" ]]; then
  (cd "${BACKEND_DIR}" && mvn -pl user-service,problem-service,team-service,gateway-service \
    -am -DskipTests package) >/dev/null
fi

declare -A APP_JARS=(
  [user]="${BACKEND_DIR}/user-service/target/user-service-0.0.1-SNAPSHOT.jar"
  [problem]="${BACKEND_DIR}/problem-service/target/problem-service-0.0.1-SNAPSHOT.jar"
  [team]="${BACKEND_DIR}/team-service/target/team-service-0.0.1-SNAPSHOT.jar"
  [gateway]="${BACKEND_DIR}/gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar"
)
for artifact in "${APP_JARS[@]}"; do
  if [[ ! -f "${artifact}" ]]; then
    echo "缺少 TRACE-01 运行验收包：${artifact}" >&2
    exit 1
  fi
done

agent_args=(
  "-javaagent:${agent_jar}"
  "-Dskywalking.agent.namespace=${NAMESPACE}"
  -Dskywalking.agent.sample_n_per_3_secs=-1
  -Dskywalking.correlation.auto_tag_keys=business.trace_id
  -Dskywalking.plugin.exclude_plugins=feign-default-http-9.x,feign-pathvar-9.x
  -Dskywalking.plugin.jdbc.trace_sql_parameters=false
  -Dskywalking.collector.backend_service=127.0.0.1:11800
  -Dskywalking.logging.level=WARN
)

start_app() {
  local short_name="$1"
  local port="$2"
  shift 2
  local service_name="trace-${RUN_ID}-${short_name}"
  local instance_name="trace-${RUN_ID}-${short_name}"
  local properties_json
  properties_json="{\"environment\":\"${NAMESPACE}\",\"serviceVersion\":\"${SERVICE_VERSION_VALUE}\",\"instance\":\"${instance_name}\"}"
  : >"${RUNTIME_DIR}/${short_name}.stdout"
  env \
    "LEETMODEL_LOG_DIR=${RUNTIME_DIR}" \
    LEETMODEL_SKYWALKING_LOG_ENABLED=true \
    LEETMODEL_SKYWALKING_LOG_ENDPOINT=http://127.0.0.1:12800/v3/logs \
    "SERVICE_INSTANCE=${instance_name}" \
    "SERVICE_VERSION=${SERVICE_VERSION_VALUE}" \
    java "${agent_args[@]}" \
      "-Dskywalking.agent.service_name=${service_name}" \
      "-Dskywalking.agent.instance_name=${instance_name}" \
      "-Dskywalking.agent.instance_properties_json=${properties_json}" \
      -jar "${APP_JARS[${short_name}]}" \
      "--spring.application.name=${service_name}" \
      "--server.port=${port}" \
      --spring.cloud.nacos.discovery.enabled=false \
      --spring.cloud.nacos.config.enabled=false \
      "$@" >"${RUNTIME_DIR}/${short_name}.stdout" 2>&1 &
  local pid=$!
  APP_PIDS+=("${pid}")
  APP_PID_BY_NAME["${short_name}"]="${pid}"
}

wait_ready() {
  local short_name="$1"
  local port="$2"
  local pid="${APP_PID_BY_NAME[${short_name}]}"
  for _ in {1..120}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "TRACE-01 临时 ${short_name} 提前退出。" >&2
      tail -n 100 "${RUNTIME_DIR}/${short_name}.stdout" >&2
      return 1
    fi
    if curl -fsS "http://127.0.0.1:${port}/actuator/health/readiness" >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  echo "TRACE-01 临时 ${short_name} 未在 120 秒内就绪。" >&2
  tail -n 100 "${RUNTIME_DIR}/${short_name}.stdout" >&2
  return 1
}

start_app user "${USER_PORT}"
start_app problem "${PROBLEM_PORT}"
wait_ready user "${USER_PORT}"
wait_ready problem "${PROBLEM_PORT}"
start_app team "${TEAM_PORT}" \
  "--spring.cloud.openfeign.client.config.user-service.url=http://127.0.0.1:${USER_PORT}" \
  "--spring.cloud.openfeign.client.config.userFeignClient.url=http://127.0.0.1:${USER_PORT}" \
  "--spring.cloud.openfeign.client.config.problem-service.url=http://127.0.0.1:${PROBLEM_PORT}" \
  "--spring.cloud.openfeign.client.config.problemFeignClient.url=http://127.0.0.1:${PROBLEM_PORT}"
wait_ready team "${TEAM_PORT}"
start_app gateway "${GATEWAY_PORT}" \
  '--spring.cloud.gateway.routes[0].id=trace-team' \
  "--spring.cloud.gateway.routes[0].uri=http://127.0.0.1:${TEAM_PORT}" \
  '--spring.cloud.gateway.routes[0].predicates[0]=Path=/api/public/problems/trace-contract' \
  '--spring.cloud.gateway.routes[0].filters[0]=SetPath=/api/teams/public'
wait_ready gateway "${GATEWAY_PORT}"
echo "[通过] Agent 9.7 附加的 WebFlux 与三个 MVC/JDBC 临时服务就绪"

login_response="$(curl -fsS \
  --header 'Content-Type: application/json' \
  --data '{"username":"admin","password":"123456"}' \
  "http://127.0.0.1:${USER_PORT}/api/auth/login")"
token="$(jq -r '.data.token // empty' <<<"${login_response}")"
if [[ -z "${token}" ]]; then
  echo "TRACE-01 测试账号登录失败。" >&2
  echo "${login_response}" >&2
  exit 1
fi

forged_trace="forged-${RUN_ID}"
response_headers="${RUNTIME_DIR}/gateway.headers"
response_body="${RUNTIME_DIR}/gateway.body"
http_status="$(curl --silent --show-error \
  --dump-header "${response_headers}" \
  --output "${response_body}" \
  --write-out '%{http_code}' \
  --header "satoken: ${token}" \
  --header "X-Trace-Id: ${forged_trace}" \
  "http://127.0.0.1:${GATEWAY_PORT}/api/public/problems/trace-contract?page=1&pageSize=2")"
business_trace="$(awk 'BEGIN { IGNORECASE=1 } /^X-Trace-Id:/ { gsub("\\r", ""); print $2 }' \
  "${response_headers}" | tail -n 1)"
if [[ "${http_status}" != "200" ]] \
    || ! jq -e '.code == 20000 and (.data.rows | length > 0)' "${response_body}" >/dev/null \
    || [[ -z "${business_trace}" || "${business_trace}" == "${forged_trace}" ]]; then
  echo "Gateway 同步链请求失败、无业务结果，或接受了伪造 traceId。" >&2
  cat "${response_body}" >&2
  exit 1
fi
echo "[通过] Gateway 清理伪造关联头并完成 team -> user/problem Feign 同步调用"

skywalking_trace=""
for _ in {1..30}; do
  skywalking_trace="$(python3 - "${RUNTIME_DIR}" "${business_trace}" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
business_trace = sys.argv[2]
expected = {"gateway", "team", "user", "problem"}
found = {}
for path in root.glob("trace-*.json.log"):
    short_name = path.name.rsplit("-", 1)[-1].removesuffix(".json.log")
    for line in path.read_text(encoding="utf-8").splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("traceId") == business_trace and event.get("swTraceId"):
            found.setdefault(short_name, set()).add(event["swTraceId"])
if set(found) == expected and len(set().union(*found.values())) == 1:
    print(next(iter(set().union(*found.values()))))
PY
)"
  if [[ -n "${skywalking_trace}" ]]; then break; fi
  sleep 2
done
if [[ -z "${skywalking_trace}" ]]; then
  echo "四个服务的本地 JSON 日志没有共享业务 traceId 与 swTraceId。" >&2
  exit 1
fi
echo "[通过] 四服务结构化日志共享业务 traceId 与 SkyWalking Trace ID"

duration_start="$(date -u -d '30 minutes ago' '+%Y-%m-%d %H%M')"
duration_end="$(date -u -d '10 minutes' '+%Y-%m-%d %H%M')"
query_oap() {
  curl -fsS --header 'Content-Type: application/json' --data-binary "$1" \
    http://127.0.0.1:12800/graphql
}

trace_payload="$(jq -nc --arg trace "${skywalking_trace}" \
  --arg start "${duration_start}" --arg end "${duration_end}" \
  '{query:"query($trace: ID!, $duration: Duration) { trace: queryTrace(traceId: $trace, duration: $duration) { spans { traceId serviceCode serviceInstanceName endpointName type peer component tags { key value } } } }",variables:{trace:$trace,duration:{start:$start,end:$end,step:"MINUTE"}}}')"
trace_response=""
trace_complete=false
for _ in {1..30}; do
  trace_response="$(query_oap "${trace_payload}")"
  if jq -e --arg business "${business_trace}" --arg run "trace-${RUN_ID}-" '
      .data.trace.spans as $spans
      | ([ $spans[].serviceCode | select(startswith($run)) ] | unique | length) == 4
        and any($spans[]; .component == "spring-webflux")
        and any($spans[]; .component == "spring-cloud-gateway" and .type == "Exit")
        and any($spans[]; .component == "SpringMVC" and .endpointName == "GET:/api/teams/public")
        and ([ $spans[] | select(.endpointName | startswith("Feign/")) ] | length) == 2
        and any($spans[]; .component == "mysql-connector-java" and .type == "Exit")
        and any($spans[]; any(.tags[]?; .key == "business.trace_id" and .value == $business))
        and all($spans[]; ([.tags[]? | select(.key == "business.trace_id")] | length) <= 1)
        and all($spans[] | select(.endpointName | startswith("Feign/"));
          all(.tags[]?; .key != "url" and .key != "request.body" and .key != "error.message"))
    ' <<<"${trace_response}" >/dev/null; then
    trace_complete=true
    break
  fi
  sleep 2
done
if [[ "${trace_complete}" != "true" ]]; then
  echo "OAP 同步 Trace 缺少 Gateway、Feign、MVC、JDBC 或安全标签契约。" >&2
  echo "${trace_response}" >&2
  exit 1
fi
echo "[通过] OAP 单 Trace 包含 Gateway -> team -> Feign -> user/problem -> MySQL"

services_payload="$(jq -nc --arg start "${duration_start}" --arg end "${duration_end}" \
  '{query:"query($duration: Duration!) { services: getAllServices(duration: $duration) { id name } }",variables:{duration:{start:$start,end:$end,step:"MINUTE"}}}')"
services_response=""
for _ in {1..30}; do
  services_response="$(query_oap "${services_payload}")"
  discovered="$(jq -r --arg prefix "trace-${RUN_ID}-" --arg suffix "|${NAMESPACE}|" \
    '[.data.services[]? | select((.name | startswith($prefix)) and (.name | endswith($suffix)))] | length' \
    <<<"${services_response}")"
  if [[ "${discovered}" -eq 4 ]]; then break; fi
  sleep 2
done
if [[ "${discovered:-0}" -ne 4 ]]; then
  echo "OAP 没有发现四个同步链服务。" >&2
  exit 1
fi
for short_name in user problem team gateway; do
  service_name="trace-${RUN_ID}-${short_name}|${NAMESPACE}|"
  service_id="$(jq -r --arg name "${service_name}" \
    '.data.services[]? | select(.name == $name) | .id' <<<"${services_response}" | head -n 1)"
  instance_payload="$(jq -nc --arg id "${service_id}" \
    --arg start "${duration_start}" --arg end "${duration_end}" \
    '{query:"query($duration: Duration!, $serviceId: ID!) { instances: getServiceInstances(duration: $duration, serviceId: $serviceId) { name attributes { name value } } }",variables:{duration:{start:$start,end:$end,step:"MINUTE"},serviceId:$id}}')"
  instance_valid=false
  instance_response=""
  for _ in {1..30}; do
    instance_response="$(query_oap "${instance_payload}")"
    if jq -e --arg instance "trace-${RUN_ID}-${short_name}" \
        --arg environment "${NAMESPACE}" --arg version "${SERVICE_VERSION_VALUE}" '
        .data.instances
        | any(.name == $instance
          and any(.attributes[]; .name == "environment" and .value == $environment)
          and any(.attributes[]; .name == "serviceVersion" and .value == $version)
          and any(.attributes[]; .name == "instance" and .value == $instance))
      ' <<<"${instance_response}" >/dev/null; then
      instance_valid=true
      break
    fi
    sleep 2
  done
  if [[ "${instance_valid}" != "true" ]]; then
    echo "OAP 实例资源字段不完整：${short_name}" >&2
    echo "${instance_response}" >&2
    exit 1
  fi
done
echo "[通过] 四服务可按 environment、serviceVersion、instance 资源筛选"

query_logs() {
  local service="$1"
  local key="$2"
  local value="$3"
  local payload
  payload="$(jq -nc --arg service "${service}" --arg key "${key}" --arg value "${value}" \
    --arg start "${duration_start}" --arg end "${duration_end}" \
    '{query:"query($condition: LogQueryConditionByName!) { result: queryLogsByName(condition: $condition) { errorReason logs { traceId content tags { key value } } } }",variables:{condition:{service:{serviceName:$service,layer:"GENERAL"},queryDuration:{start:$start,end:$end,step:"MINUTE"},tags:[{key:$key,value:$value}],paging:{pageNum:1,pageSize:100},queryOrder:"DES"}}}')"
  query_oap "${payload}"
}

central_business=false
central_skywalking=false
for _ in {1..30}; do
  business_logs="$(query_logs "trace-${RUN_ID}-gateway" business_trace_id "${business_trace}")"
  skywalking_logs="$(query_logs "trace-${RUN_ID}-gateway" sw_trace_id "${skywalking_trace}")"
  if jq -e --arg business "${business_trace}" --arg sw "${skywalking_trace}" '
      .data.result.logs | any(.content | fromjson?
        | .traceId == $business and .swTraceId == $sw)' <<<"${business_logs}" >/dev/null; then
    central_business=true
  fi
  if jq -e --arg business "${business_trace}" --arg sw "${skywalking_trace}" '
      .data.result.logs | any(.content | fromjson?
        | .traceId == $business and .swTraceId == $sw)' <<<"${skywalking_logs}" >/dev/null; then
    central_skywalking=true
  fi
  if [[ "${central_business}" == "true" && "${central_skywalking}" == "true" ]]; then break; fi
  sleep 2
done
if [[ "${central_business}" != "true" || "${central_skywalking}" != "true" ]]; then
  echo "中央日志不能按业务 traceId 与 swTraceId 双向定位。" >&2
  exit 1
fi
echo "[通过] 业务 traceId <-> SkyWalking Trace <-> 中央结构化日志双向定位"

(cd "${BACKEND_DIR}" && docker compose exec -T rocketmq-broker ./mqadmin updateSubGroup \
  -n rocketmq-namesrv:9876 -c LeetModelLocalCluster -g "${TEMP_GROUP}" -r 5) >/dev/null
TEMP_GROUP_CREATED=true
mq_service="trace-mq-${RUN_ID}"
mq_test_log="${RUNTIME_DIR}/rocketmq-test.log"
if ! (cd "${BACKEND_DIR}" && \
  RUN_ROCKETMQ_INTEGRATION=true \
  ROCKETMQ_INTEGRATION_GROUP="${TEMP_GROUP}" \
  mvn -pl common/common-messaging -am \
    -Dtest=RocketMqProtocolIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    "-DargLine=-javaagent:${agent_jar} -Dskywalking.agent.service_name=${mq_service} -Dskywalking.agent.namespace=${NAMESPACE} -Dskywalking.agent.instance_name=${mq_service} -Dskywalking.agent.sample_n_per_3_secs=-1 -Dskywalking.collector.backend_service=127.0.0.1:11800 -Dskywalking.logging.level=WARN" \
    test) >"${mq_test_log}" 2>&1; then
  echo "RocketMQ Agent 协议验收失败。" >&2
  tail -n 120 "${mq_test_log}" >&2
  exit 1
fi

mq_service_id=""
for _ in {1..30}; do
  services_response="$(query_oap "${services_payload}")"
  mq_service_id="$(jq -r --arg name "${mq_service}|${NAMESPACE}|" \
    '.data.services[]? | select(.name == $name) | .id' <<<"${services_response}" | head -n 1)"
  if [[ -n "${mq_service_id}" ]]; then break; fi
  sleep 2
done
mq_trace_payload="$(jq -nc --arg id "${mq_service_id}" \
  --arg start "${duration_start}" --arg end "${duration_end}" \
  '{query:"query($condition: TraceQueryCondition!) { result: queryTraces(condition: $condition) { traces { spans { endpointName type component tags { key value } } } } }",variables:{condition:{queryDuration:{start:$start,end:$end,step:"MINUTE"},serviceId:$id,traceState:"ALL",queryOrder:"BY_START_TIME",paging:{pageNum:1,pageSize:100}}}}')"
mq_trace_response=""
for _ in {1..30}; do
  mq_trace_response="$(query_oap "${mq_trace_payload}")"
  if jq -e '.data.result.traces | any(.spans | any(.component == "rocketMQ-producer"))' \
      <<<"${mq_trace_response}" >/dev/null; then break; fi
  sleep 2
done
if ! jq -e '.data.result.traces | any(.spans | any(
      .component == "rocketMQ-producer" and .type == "Exit"
      and (.endpointName | endswith("/Producer"))))' \
    <<<"${mq_trace_response}" >/dev/null; then
  echo "OAP 中未发现 RocketMQ 5.3.1 生产端自动 Span。" >&2
  echo "${mq_trace_response}" >&2
  exit 1
fi
if jq -e '.data.result.traces | any(.spans | any(.component == "rocketMQ-consumer"))' \
    <<<"${mq_trace_response}" >/dev/null; then
  echo "[通过] RocketMQ 5.3.1 Producer Exit 与 Consumer Entry 自动埋点"
else
  echo "[通过] RocketMQ 5.3.1 Producer Exit 自动埋点；Consumer 5.3.1 边界由 TRACE-02 自定义 attempt Span 补齐"
fi

if [[ "${VERIFY_OAP_OUTAGE:-false}" == "true" ]]; then
  docker compose -f "${COMPOSE_FILE}" stop skywalking-oap >/dev/null
  OAP_STOPPED=true
  outage_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --header "satoken: ${token}" \
    "http://127.0.0.1:${GATEWAY_PORT}/api/public/problems/trace-contract?page=1&pageSize=1")"
  if [[ "${outage_status}" != "200" ]]; then
    echo "OAP 中断阻塞了业务同步请求。" >&2
    exit 1
  fi
  echo "[通过] OAP 中断不阻塞业务请求"
  docker compose -f "${COMPOSE_FILE}" up -d --wait skywalking-oap >/dev/null
  OAP_STOPPED=false
  curl -fsS --header 'Content-Type: application/json' \
    --data '{"query":"query { __typename }"}' \
    http://127.0.0.1:12800/graphql \
    | jq -e '.data.__typename == "Query"' >/dev/null
  echo "[通过] OAP 恢复"
fi

echo "TRACE-01 真实协议与关联验收通过。"
