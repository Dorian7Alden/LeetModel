#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME=false

if [[ "${1:-}" == "--runtime" ]]; then
  RUNTIME=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--runtime]" >&2
  exit 2
fi

helper="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/telemetry/SkyWalkingExecutionSpan.java"
catalog="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/telemetry/ExecutionSpanOperation.java"
declare -A expected=(
  ["common/common-messaging/src/main/java/com/leetmodel/common/messaging/internal/OutboxRelay.java"]="OUTBOX_PUBLISH"
  ["common/common-messaging/src/main/java/com/leetmodel/common/messaging/internal/ObservedMessageInbox.java"]="INBOX_CONSUME"
  ["ai-review-service/src/main/java/com/leetmodel/review/service/ReviewTaskWorkerCoordinator.java"]="REVIEW_WORKER"
  ["ai-suggestion-service/src/main/java/com/leetmodel/suggestion/service/SuggestionTaskWorkerCoordinator.java"]="SUGGESTION_WORKER"
  ["ai-evaluation-service/src/main/java/com/leetmodel/evaluation/service/EvaluationWorkerCoordinator.java"]="EVALUATION_WORKER"
  ["ranking-service/src/main/java/com/leetmodel/ranking/service/RankingRebuildCoordinator.java"]="RANKING_REBUILD_WORKER"
  ["ai-gateway-service/src/main/java/com/leetmodel/aigateway/scheduling/AiQueueDispatcher.java"]="AI_PROVIDER"
  ["ai-gateway-service/src/main/java/com/leetmodel/aigateway/scheduling/AiQueueRecoveryService.java"]="AI_RECOVERY"
)

for required in "${helper}" "${catalog}"; do
  if [[ ! -s "${required}" ]]; then
    echo "缺少 TRACE-02 公共契约：${required}" >&2
    exit 1
  fi
done
for relative in "${!expected[@]}"; do
  source_file="${BACKEND_DIR}/${relative}"
  if [[ ! -s "${source_file}" ]] \
      || ! rg -Fq "ExecutionSpanOperation.${expected[${relative}]}" "${source_file}"; then
    echo "异步物理边界没有使用固定 operation：${relative}" >&2
    exit 1
  fi
done

for marker in \
    'Tracer.createEntrySpan(operation.operationName(), new ContextCarrierRef())' \
    'SkyWalkingCorrelation.bindBusinessTraceId' \
    'CorrelationContext.replace(SkyWalkingCorrelation.enrich' \
    'attempt.kind' 'ai.call_type' 'ai.priority' 'outcome' 'error.kind'; do
  if ! rg -Fq "${marker}" "${helper}"; then
    echo "公共异步 Span 契约缺少：${marker}" >&2
    exit 1
  fi
done
if rg -n 'ActiveSpan\.error\([^)]|\.tag\([^,]+,\s*(taskId|eventId|callId|traceId)|span\.log\(' \
    "${helper}" >/dev/null; then
  echo "异步 Span 不得记录原始异常或业务标识。" >&2
  exit 1
fi
raw_entry_calls="$(rg -l 'Tracer\.createEntrySpan' "${BACKEND_DIR}" \
  -g '*.java' -g '!**/target/**' -g '!**/src/test/**' || true)"
if [[ "${raw_entry_calls}" != "${helper}" ]]; then
  echo "Entry Span 必须由公共低基数契约统一创建：" >&2
  echo "${raw_entry_calls}" >&2
  exit 1
fi
if rg -n 'ExecutionSpanOperation\.[A-Z_]+.*\+|operationName\(\).*\+' \
    "${BACKEND_DIR}" -g '*.java' -g '!**/target/**' >/dev/null; then
  echo "检测到动态拼接异步 operation name。" >&2
  exit 1
fi

echo "[通过] Outbox、Inbox、四类租约 Worker 与 AI attempt 使用固定低基数 Span 契约"
if [[ "${RUNTIME}" != "true" ]]; then
  exit 0
fi

for command in jq curl mvn docker; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少 TRACE-02 运行验收命令：${command}" >&2
    exit 1
  fi
done

RUN_ID="${SKYWALKING_ASYNC_RUN_ID:-$(date -u '+%s')-$$}"
NAMESPACE="trace-async-contract"
HELPER_SERVICE="trace-async-helper-${RUN_ID}"
MQ_SERVICE="trace-async-mq-${RUN_ID}"
TEMP_GROUP="lm-dev%cg-trace-async-${RUN_ID}"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime/skywalking-async/${RUN_ID}"
mkdir -p "${RUNTIME_DIR}"
TEMP_GROUP_CREATED=false

cleanup() {
  if [[ "${TEMP_GROUP_CREATED}" == "true" ]]; then
    (cd "${BACKEND_DIR}" && docker compose exec -T rocketmq-broker ./mqadmin deleteSubGroup \
      -n rocketmq-namesrv:9876 -c LeetModelLocalCluster -g "${TEMP_GROUP}") \
      >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

"${SCRIPT_DIR}/start-observability.sh" >/dev/null
(cd "${BACKEND_DIR}" && docker compose up -d --wait \
  rocketmq-namesrv rocketmq-broker) >/dev/null
"${SCRIPT_DIR}/init-rocketmq.sh" >/dev/null
(cd "${BACKEND_DIR}" && docker compose exec -T rocketmq-broker ./mqadmin updateSubGroup \
  -n rocketmq-namesrv:9876 -c LeetModelLocalCluster -g "${TEMP_GROUP}" -r 5) >/dev/null
TEMP_GROUP_CREATED=true

agent_jar="$("${SCRIPT_DIR}/prepare-skywalking-agent.sh")"
agent_args="-javaagent:${agent_jar} -Dskywalking.agent.namespace=${NAMESPACE} -Dskywalking.agent.sample_n_per_3_secs=-1 -Dskywalking.correlation.auto_tag_keys=business.trace_id -Dskywalking.collector.backend_service=127.0.0.1:11800 -Dskywalking.logging.level=WARN"

helper_log="${RUNTIME_DIR}/helper-test.log"
if ! (cd "${BACKEND_DIR}" && \
  RUN_SKYWALKING_ASYNC_INTEGRATION=true \
  mvn -pl common/common-core \
    -Dtest=SkyWalkingExecutionSpanAgentIntegrationTest \
    "-DargLine=${agent_args} -Dskywalking.agent.service_name=${HELPER_SERVICE} -Dskywalking.agent.instance_name=${HELPER_SERVICE}" \
    test) >"${helper_log}" 2>&1; then
  echo "公共 attempt Agent 验收失败。" >&2
  tail -n 120 "${helper_log}" >&2
  exit 1
fi

mq_log="${RUNTIME_DIR}/messaging-test.log"
if ! (cd "${BACKEND_DIR}" && \
  RUN_ROCKETMQ_INTEGRATION=true \
  ROCKETMQ_INTEGRATION_GROUP="${TEMP_GROUP}" \
  mvn -pl common/common-messaging -am \
    -Dtest=OutboxRelayTest,RocketMqProtocolIntegrationTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    "-DargLine=${agent_args} -Dskywalking.agent.service_name=${MQ_SERVICE} -Dskywalking.agent.instance_name=${MQ_SERVICE}" \
    test) >"${mq_log}" 2>&1; then
  echo "消息 attempt Agent/RocketMQ 验收失败。" >&2
  tail -n 160 "${mq_log}" >&2
  exit 1
fi

duration_start="$(date -u -d '30 minutes ago' '+%Y-%m-%d %H%M')"
duration_end="$(date -u -d '10 minutes' '+%Y-%m-%d %H%M')"
query_oap() {
  curl -fsS --header 'Content-Type: application/json' --data-binary "$1" \
    http://127.0.0.1:12800/graphql
}
services_payload="$(jq -nc --arg start "${duration_start}" --arg end "${duration_end}" \
  '{query:"query($duration: Duration!) { services: getAllServices(duration: $duration) { id name } }",variables:{duration:{start:$start,end:$end,step:"MINUTE"}}}')"

service_id() {
  local name="$1"
  local response
  local id=""
  for _ in {1..30}; do
    response="$(query_oap "${services_payload}")"
    id="$(jq -r --arg name "${name}|${NAMESPACE}|" \
      '.data.services[]? | select(.name == $name) | .id' <<<"${response}" | head -n 1)"
    if [[ -n "${id}" ]]; then
      echo "${id}"
      return 0
    fi
    sleep 2
  done
  return 1
}

query_service_traces() {
  local id="$1"
  local payload
  payload="$(jq -nc --arg id "${id}" --arg start "${duration_start}" --arg end "${duration_end}" \
    '{query:"query($condition: TraceQueryCondition!) { result: queryTraces(condition: $condition) { traces { spans { traceId endpointName startTime endTime isError type component tags { key value } } } } }",variables:{condition:{queryDuration:{start:$start,end:$end,step:"MINUTE"},serviceId:$id,traceState:"ALL",queryOrder:"BY_START_TIME",paging:{pageNum:1,pageSize:200}}}}')"
  query_oap "${payload}"
}

helper_id="$(service_id "${HELPER_SERVICE}")" || {
  echo "OAP 未发现公共 attempt 验收服务。" >&2
  exit 1
}
mq_id="$(service_id "${MQ_SERVICE}")" || {
  echo "OAP 未发现消息 attempt 验收服务。" >&2
  exit 1
}

helper_traces=""
mq_traces=""
for _ in {1..30}; do
  helper_traces="$(query_service_traces "${helper_id}")"
  mq_traces="$(query_service_traces "${mq_id}")"
  if jq -e '
      ([.data.result.traces[].spans[] | select(.endpointName == "Worker/ReviewAttempt")] | length) >= 2
      and ([.data.result.traces[].spans[] | select(.endpointName == "AI/ProviderAttempt")] | length) >= 2
    ' <<<"${helper_traces}" >/dev/null \
      && jq -e '
        ([.data.result.traces[].spans[] | select(.endpointName == "Messaging/OutboxPublishAttempt")] | length) >= 3
        and ([.data.result.traces[].spans[] | select(.endpointName == "Messaging/InboxConsumeAttempt")] | length) >= 2
      ' <<<"${mq_traces}" >/dev/null; then
    break
  fi
  sleep 2
done

if ! jq -e '
    ([.data.result.traces[].spans[] | select(.endpointName == "Worker/ReviewAttempt") | .traceId] | unique | length) >= 2
    and any(.data.result.traces[].spans[];
      .endpointName == "Worker/ReviewAttempt"
      and any(.tags[]; .key == "attempt.kind" and .value == "normal"))
    and any(.data.result.traces[].spans[];
      .endpointName == "Worker/ReviewAttempt"
      and any(.tags[]; .key == "attempt.kind" and .value == "takeover"))
    and any(.data.result.traces[].spans[];
      .endpointName == "AI/ProviderAttempt" and .isError == true
      and any(.tags[]; .key == "outcome" and .value == "upstream_result_unknown")
      and any(.tags[]; .key == "error.kind" and .value == "result_unknown"))
    and all(.data.result.traces[].spans[] | select(
      .endpointName == "Worker/ReviewAttempt" or .endpointName == "AI/ProviderAttempt"
      or .endpointName == "AI/RecoveryAttempt"); (.endTime - .startTime) < 5000)
  ' <<<"${helper_traces}" >/dev/null; then
  echo "OAP 中 attempt 独立 Trace、接管、UNKNOWN 或有界时长不符合契约。" >&2
  echo "${helper_traces}" >&2
  exit 1
fi

if ! jq -e '
    any(.data.result.traces[].spans[];
      .endpointName == "Messaging/OutboxPublishAttempt"
      and any(.tags[]; .key == "outcome" and .value == "success"))
    and any(.data.result.traces[].spans[];
      .endpointName == "Messaging/OutboxPublishAttempt" and .isError == true
      and any(.tags[]; .key == "outcome" and .value == "retry"))
    and any(.data.result.traces[].spans[];
      .endpointName == "Messaging/InboxConsumeAttempt"
      and any(.tags[]; .key == "outcome" and .value == "consumed"))
    and any(.data.result.traces[].spans[];
      .endpointName == "Messaging/InboxConsumeAttempt"
      and any(.tags[]; .key == "outcome" and .value == "duplicate"))
    and any(.data.result.traces[].spans[];
      .component == "rocketMQ-producer" and .type == "Exit")
  ' <<<"${mq_traces}" >/dev/null; then
  echo "OAP 中 Outbox/Inbox/RocketMQ 真实协议边界不完整。" >&2
  echo "${mq_traces}" >&2
  exit 1
fi

for response in "${helper_traces}" "${mq_traces}"; do
  if jq -e '
      any(.data.result.traces[].spans[] | select(
        .endpointName == "Messaging/OutboxPublishAttempt"
        or .endpointName == "Messaging/InboxConsumeAttempt"
        or .endpointName == "Worker/ReviewAttempt"
        or .endpointName == "AI/ProviderAttempt"
        or .endpointName == "AI/RecoveryAttempt").tags[];
        .key != "business.trace_id"
        and (.key | ascii_downcase | test("trace|event|task|attempt.?no|call.?id|payload|prompt|token")))
    ' <<<"${response}" >/dev/null; then
    echo "自定义 Span 泄漏了业务标识或正文标签。" >&2
    exit 1
  fi
done

echo "[通过] Outbox 发布、Inbox 去重、租约接管和 AI UNKNOWN 均形成独立有界 Trace"
echo "TRACE-02 真实 Agent/RocketMQ 协议验收通过。"
