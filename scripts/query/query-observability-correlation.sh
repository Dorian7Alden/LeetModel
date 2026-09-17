#!/usr/bin/env bash
set -euo pipefail

ALERT_FILE=""
FACT_FILE=""
OAP_URL="${LEETMODEL_OAP_GRAPHQL_URL:-http://127.0.0.1:12800/graphql}"
ALLOW_GAPS=false
SIMULATE_TRACE_SAMPLED=false

usage() {
  cat >&2 <<'USAGE'
用法: query-observability-correlation.sh --alert-file FILE --fact-file FILE
       [--oap-url URL] [--allow-gaps] [--simulate-trace-sampled]

只读关联 Prometheus 告警快照、SkyWalking Trace、中央结构化日志和脱敏业务事实。
输出固定 schema JSON；不会输出日志正文、消息/Payload、Prompt、回答、Token 或原始异常。
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --alert-file) ALERT_FILE="${2:-}"; shift 2 ;;
    --fact-file) FACT_FILE="${2:-}"; shift 2 ;;
    --oap-url) OAP_URL="${2:-}"; shift 2 ;;
    --allow-gaps) ALLOW_GAPS=true; shift ;;
    --simulate-trace-sampled) SIMULATE_TRACE_SAMPLED=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 2 ;;
  esac
done

for command in curl jq python3; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少联合定位命令：${command}" >&2
    exit 2
  fi
done
if [[ ! -s "${ALERT_FILE}" || ! -s "${FACT_FILE}" ]]; then
  usage
  exit 2
fi

python3 - "${ALERT_FILE}" "${FACT_FILE}" <<'PY'
import json
import pathlib
import re
import sys

alert = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
snapshot = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
if snapshot.get("schemaVersion") != "leetmodel.correlation.fact.v1":
    raise SystemExit("业务事实快照 schemaVersion 不受支持")
required = {"scenario", "service", "businessTraceId", "traceOperation", "logEventCode", "fact"}
if required - snapshot.keys():
    raise SystemExit(f"业务事实快照缺少字段：{sorted(required-snapshot.keys())}")
identifier = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:%-]{0,159}$")
operation = re.compile(r"^[A-Za-z][A-Za-z0-9._-]{0,63}/[A-Za-z][A-Za-z0-9._-]{0,63}$")
for key in ("service", "businessTraceId", "logEventCode"):
    if not isinstance(snapshot[key], str) or not identifier.fullmatch(snapshot[key]):
        raise SystemExit(f"业务事实字段不安全：{key}")
if not isinstance(snapshot["traceOperation"], str) or not operation.fullmatch(snapshot["traceOperation"]):
    raise SystemExit("业务事实字段不安全：traceOperation")

forbidden = re.compile(
    r"(^|_)(payload|prompt|answer|paper|content|token|credential|secret|password|rawexception)(_|$)",
    re.IGNORECASE,
)
def inspect(value, path="fact"):
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = re.sub(r"(?<!^)(?=[A-Z])", "_", str(key)).lower()
            if forbidden.search(normalized):
                raise SystemExit(f"业务事实快照包含禁止字段：{path}.{key}")
            inspect(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            inspect(child, f"{path}[{index}]")
    elif isinstance(value, str) and len(value) > 200:
        raise SystemExit(f"业务事实快照字符串超长：{path}")
inspect(snapshot["fact"])

labels = alert.get("labels", {})
annotations = alert.get("annotations", {})
if labels.get("service") != snapshot["service"]:
    raise SystemExit("告警 service 与业务事实 service 不一致")
if annotations.get("correlation_contract") != "leetmodel.correlation.v1":
    raise SystemExit("告警缺少 leetmodel.correlation.v1 契约")
if annotations.get("trace_operation") != snapshot["traceOperation"]:
    raise SystemExit("告警 trace_operation 与事实场景不一致")
if annotations.get("log_event_code") != snapshot["logEventCode"]:
    raise SystemExit("告警 log_event_code 与事实场景不一致")
for key in labels:
    normalized = re.sub(r"[^a-z0-9]", "", key.lower())
    if normalized in {
        "traceid", "swtraceid", "eventid", "taskid", "domaintaskid",
        "attemptid", "attemptno", "aicallid", "callid", "operationid",
    }:
        raise SystemExit(f"告警包含禁止的高基数标签：{key}")
PY

service="$(jq -r '.service' "${FACT_FILE}")"
business_trace="$(jq -r '.businessTraceId' "${FACT_FILE}")"
operation="$(jq -r '.traceOperation' "${FACT_FILE}")"
event_code="$(jq -r '.logEventCode' "${FACT_FILE}")"
scenario="$(jq -r '.scenario' "${FACT_FILE}")"

duration_start="$(date -u -d '30 minutes ago' '+%Y-%m-%d %H%M')"
duration_end="$(date -u -d '10 minutes' '+%Y-%m-%d %H%M')"

query_oap() {
  curl --fail --silent --show-error --max-time 8 \
    --header 'Content-Type: application/json' --data-binary "$1" "${OAP_URL}"
}

trace_status="sampled_or_not_found"
trace_id=""
trace_outcome=""
trace_error_kind=""
oap_available=true

if [[ "${SIMULATE_TRACE_SAMPLED}" != "true" ]]; then
  services_payload="$(jq -nc --arg start "${duration_start}" --arg end "${duration_end}" \
    '{query:"query($duration: Duration!) { services: getAllServices(duration: $duration) { id name } }",variables:{duration:{start:$start,end:$end,step:"MINUTE"}}}')"
  if services_response="$(query_oap "${services_payload}" 2>/dev/null)" \
      && ! jq -e '.errors != null' <<<"${services_response}" >/dev/null; then
    service_id="$(jq -r --arg service "${service}" \
      '.data.services[]? | select(.name == $service or (.name | startswith($service + "|"))) | .id' \
      <<<"${services_response}" | head -n 1)"
    if [[ -n "${service_id}" ]]; then
      traces_payload="$(jq -nc --arg id "${service_id}" --arg start "${duration_start}" --arg end "${duration_end}" \
        '{query:"query($condition: TraceQueryCondition!) { result: queryTraces(condition: $condition) { traces { spans { traceId endpointName isError tags { key value } } } } }",variables:{condition:{queryDuration:{start:$start,end:$end,step:"MINUTE"},serviceId:$id,traceState:"ALL",queryOrder:"BY_START_TIME",paging:{pageNum:1,pageSize:200}}}}')"
      if traces_response="$(query_oap "${traces_payload}" 2>/dev/null)" \
          && ! jq -e '.errors != null' <<<"${traces_response}" >/dev/null; then
        span="$(jq -c --arg operation "${operation}" --arg business "${business_trace}" '
          [.data.result.traces[].spans[]? | select(
            .endpointName == $operation
            and any(.tags[]?; .key == "business.trace_id" and .value == $business)
          )] | first // empty' <<<"${traces_response}")"
        if [[ -n "${span}" ]]; then
          trace_status="available"
          trace_id="$(jq -r '.traceId' <<<"${span}")"
          trace_outcome="$(jq -r '[.tags[]? | select(.key == "outcome") | .value] | first // ""' <<<"${span}")"
          trace_error_kind="$(jq -r '[.tags[]? | select(.key == "error.kind") | .value] | first // ""' <<<"${span}")"
        fi
      else
        oap_available=false
        trace_status="unavailable"
      fi
    fi
  else
    oap_available=false
    trace_status="unavailable"
  fi
fi

log_status="not_found"
log_summary='{}'
if [[ "${oap_available}" == "true" ]]; then
  logs_payload="$(jq -nc --arg service "${service}" --arg business "${business_trace}" \
    --arg start "${duration_start}" --arg end "${duration_end}" \
    '{query:"query($condition: LogQueryConditionByName!) { result: queryLogsByName(condition: $condition) { errorReason logs { traceId content } } }",variables:{condition:{service:{serviceName:$service,layer:"GENERAL"},queryDuration:{start:$start,end:$end,step:"MINUTE"},tags:[{key:"business_trace_id",value:$business}],paging:{pageNum:1,pageSize:100},queryOrder:"DES"}}}')"
  if logs_response="$(query_oap "${logs_payload}" 2>/dev/null)" \
      && ! jq -e '.errors != null' <<<"${logs_response}" >/dev/null; then
    log_summary="$(jq -c --arg event "${event_code}" --arg business "${business_trace}" '
      [.data.result.logs[]?.content | fromjson? | select(
        .eventCode == $event and .traceId == $business
      ) | {
        eventCode, traceId, swTraceId, eventId, domainTaskId, attemptNo, aiCallId,
        taskState, messageTopic, retryCount, errorCode, outcome
      } | with_entries(select(.value != null))] | first // {}' <<<"${logs_response}")"
    if [[ "${log_summary}" != '{}' ]]; then log_status="available"; fi
  else
    log_status="unavailable"
  fi
else
  log_status="unavailable"
fi

fact_summary="$(jq -c '.fact' "${FACT_FILE}")"
join="partial"
identifiers_match=false
if [[ "${log_status}" == "available" ]]; then
  if [[ "${scenario}" == "outbox_backlog" ]]; then
    if jq -e --argjson log "${log_summary}" '
        .fact.eventId == $log.eventId and .businessTraceId == $log.traceId
      ' "${FACT_FILE}" >/dev/null; then identifiers_match=true; fi
  elif [[ "${scenario}" == "ai_unknown" ]]; then
    if jq -e --argjson log "${log_summary}" '
        .fact.domainTaskId == $log.domainTaskId
        and .fact.attemptNo == $log.attemptNo
        and .fact.aiCallId == $log.aiCallId
        and .businessTraceId == $log.traceId
      ' "${FACT_FILE}" >/dev/null; then identifiers_match=true; fi
  fi
fi
if [[ "${identifiers_match}" == "true" && "${trace_status}" == "available" ]]; then
  join="complete"
elif [[ "${identifiers_match}" == "true" \
    && "${trace_status}" == "sampled_or_not_found" ]]; then
  join="fallback_complete"
fi

jq -n \
  --arg scenario "${scenario}" \
  --arg join "${join}" \
  --arg service "${service}" \
  --arg alert_status "$(jq -r '.status // "unknown"' "${ALERT_FILE}")" \
  --arg alert_name "$(jq -r '.labels.alertname // "unknown"' "${ALERT_FILE}")" \
  --arg component "$(jq -r '.labels.component // "unknown"' "${ALERT_FILE}")" \
  --arg trace_status "${trace_status}" \
  --arg trace_id "${trace_id}" \
  --arg operation "${operation}" \
  --arg trace_outcome "${trace_outcome}" \
  --arg trace_error "${trace_error_kind}" \
  --arg log_status "${log_status}" \
  --arg fact_query "$(jq -r '.annotations.fact_query // ""' "${ALERT_FILE}")" \
  --argjson log "${log_summary}" \
  --argjson fact "${fact_summary}" '
  {
    schemaVersion: "leetmodel.correlation.result.v1",
    scenario: $scenario,
    joinStatus: $join,
    service: $service,
    sources: {
      alert: {status: "available", alertName: $alert_name, alertStatus: $alert_status, component: $component},
      trace: ({status: $trace_status, operation: $operation}
        + (if $trace_id == "" then {} else {swTraceId: $trace_id} end)
        + (if $trace_outcome == "" then {} else {outcome: $trace_outcome} end)
        + (if $trace_error == "" then {} else {errorKind: $trace_error} end)),
      log: ({status: $log_status} + (if $log_status == "available" then $log else {} end)),
      fact: {status: "available", query: $fact_query, value: $fact}
    },
    gaps: [
      (if $trace_status == "available" then empty else {source:"trace",status:$trace_status,fallback:"business_trace_id -> central_log -> persisted_fact"} end),
      (if $log_status == "available" then empty else {source:"central_log",status:$log_status,fallback:"business_trace_id -> persisted_fact"} end)
    ]
  }'

if [[ "${join}" != "complete" && "${join}" != "fallback_complete" \
    && "${ALLOW_GAPS}" != "true" ]]; then
  exit 3
fi
