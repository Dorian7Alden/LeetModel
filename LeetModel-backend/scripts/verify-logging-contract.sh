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

services=(
  user-service problem-service team-service submission-service ai-review-service
  ranking-service ai-suggestion-service ai-assistant-service knowledge-retrieval-service
  ai-evaluation-service gateway-service admin-service ai-gateway-service
  audit-service
)

shared_layout="${BACKEND_DIR}/common/common-core/src/main/resources/leetmodel-logback-spring.xml"
shared_config="${BACKEND_DIR}/common/common-core/src/main/resources/leetmodel-logging.yml"
layout_source="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/LeetModelJsonLayout.java"
field_names="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/LogFieldNames.java"
event_codes="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/LogEventCodes.java"
sanitizer="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/LogSanitizer.java"
failure_limiter="${BACKEND_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/FailureLogLimiter.java"

for required in "${shared_layout}" "${shared_config}" "${layout_source}" "${field_names}" \
    "${event_codes}" "${sanitizer}" "${failure_limiter}"; do
  if [[ ! -f "${required}" ]]; then
    echo "缺少统一日志契约文件：${required}" >&2
    exit 1
  fi
done

for service in "${services[@]}"; do
  application="${BACKEND_DIR}/${service}/src/main/resources/application.yml"
  if [[ "$(rg -c 'classpath:leetmodel-logging\.yml' "${application}")" != "1" ]]; then
    echo "${service} 必须且只能导入一次公共日志配置。" >&2
    exit 1
  fi
  if find "${BACKEND_DIR}/${service}/src/main/resources" -maxdepth 1 \
      -type f -name 'logback*.xml' | grep -q .; then
    echo "${service} 仍存在私有 Logback 配置。" >&2
    exit 1
  fi
done

required_layout_markers=(
  'leetmodel.log.v1' 'schemaVersion' 'eventCode' 'serviceVersion'
  'routeTemplate' 'domainTaskId' 'attemptNo' 'aiCallId' 'suppressedCount' 'stackTrace'
)
for marker in "${required_layout_markers[@]}"; do
  if ! rg -q "${marker}" "${layout_source}" "${field_names}"; then
    echo "JSON schema 缺少字段：${marker}" >&2
    exit 1
  fi
done

required_event_codes=(
  HTTP_REQUEST_COMPLETED HTTP_REQUEST_FAILED
  OUTBOX_PUBLISH_BLOCKED OUTBOX_PUBLISH_RETRY
  INBOX_MESSAGE_CONSUMED INBOX_MESSAGE_DUPLICATE INBOX_MESSAGE_FAILED
  DOMAIN_TASK_CLAIMED DOMAIN_TASK_COMPLETED DOMAIN_TASK_FAILED
  AI_CALL_COMPLETED AI_CALL_FAILED AI_CALL_RESULT_UNKNOWN
  DEPENDENCY_CALL_FAILED DEPENDENCY_CALL_RECOVERED REQUEST_REJECTED SYSTEM_FAILURE
)
for event_code in "${required_event_codes[@]}"; do
  if ! rg -q "${event_code}" "${event_codes}"; then
    echo "缺少稳定事件编码：${event_code}" >&2
    exit 1
  fi
done

if ! rg -q '<appender name="CONSOLE"' "${shared_layout}" \
    || ! rg -q '<appender name="LOCAL_ROLLING"' "${shared_layout}" \
    || ! rg -q 'SizeAndTimeBasedRollingPolicy' "${shared_layout}"; then
  echo "统一 Logback 配置必须同时提供 stdout 与有界本地轮转。" >&2
  exit 1
fi

if ! rg -q 'logging\.config: classpath:leetmodel-logback-spring\.xml|config: classpath:leetmodel-logback-spring\.xml' \
    "${shared_config}"; then
  echo "公共 YAML 未指向统一 Logback 配置。" >&2
  exit 1
fi

if rg -n 'StdOutImpl|org\.apache\.ibatis\.logging\.stdout' "${BACKEND_DIR}" \
    --glob 'application*.yml' --glob '!**/target/**' >/dev/null; then
  echo "检测到会输出 SQL 参数的 MyBatis stdout logger。" >&2
  exit 1
fi

if rg -n '(^|[[:space:]])(root|com\.leetmodel|org\.apache\.ibatis|com\.baomidou\.mybatisplus):[[:space:]]*DEBUG' \
    "${BACKEND_DIR}" --glob 'application*.yml' --glob '!**/target/**' >/dev/null; then
  echo "检测到全局、业务或 SQL logger 的 DEBUG 默认级别。" >&2
  exit 1
fi

python3 - "${BACKEND_DIR}" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
forbidden = re.compile(
    r"getMessage\s*\(|getUploadToken\s*\(|\bsafeReason\s*\(|"
    r"\b(?:objectName|objectKey|targetObjectName|avatarPath|originalFilename|"
    r"prompt|answer|payload|ragContext|knowledgeChunk|embedding)\b"
)
start = re.compile(r"\blog\.(?:trace|debug|info|warn|error)\s*\(|\blog\.at(?:Trace|Debug|Info|Warn|Error)\s*\(")
violations = []

for path in root.rglob("src/main/java/**/*.java"):
    text = path.read_text(encoding="utf-8")
    for match in start.finditer(text):
        index = match.start()
        end = text.find(";", match.end())
        if end < 0:
            end = min(len(text), match.end() + 1000)
        statement = text[index:end + 1]
        found = forbidden.search(statement)
        if found:
            line = text.count("\n", 0, index) + 1
            violations.append(f"{path.relative_to(root)}:{line}: {found.group(0)}")

if violations:
    raise SystemExit("日志调用仍携带自由异常文本或敏感正文/路径：\n" + "\n".join(violations))
PY

for marker in 'LogSanitizer.message' 'LogSanitizer.field' 'LogSanitizer.identifier' \
    'MAX_MESSAGE_LENGTH' 'MAX_FIELD_LENGTH'; do
  if ! rg -q "${marker}" "${layout_source}" "${sanitizer}"; then
    echo "日志最终边界缺少安全策略：${marker}" >&2
    exit 1
  fi
done

if ! rg -q 'leetmodel\.logging\.suppressed' "${failure_limiter}" \
    || ! rg -q 'summary-interval:' "${shared_config}"; then
  echo "缺少重复故障限频窗口或抑制计数指标。" >&2
  exit 1
fi

echo "[通过] ${#services[@]} 个服务统一导入 JSON、安全清洗、调用点负面门禁与限频基线"

if [[ "${RUNTIME}" != "true" ]]; then
  exit 0
fi

runtime_dir="${BACKEND_DIR}/.observability-runtime/logging-contract"
stdout_log="${runtime_dir}/stdout.log"
rolling_log="${runtime_dir}/knowledge-retrieval-service.json.log"
runtime_port="${LOGGING_CONTRACT_PORT:-18095}"
runtime_pid=""

cleanup() {
  if [[ -n "${runtime_pid}" ]] && kill -0 "${runtime_pid}" 2>/dev/null; then
    kill "${runtime_pid}" 2>/dev/null || true
    for _ in {1..20}; do
      if ! kill -0 "${runtime_pid}" 2>/dev/null; then
        return
      fi
      sleep 1
    done
    kill -9 "${runtime_pid}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

if ss -ltn "sport = :${runtime_port}" | tail -n +2 | grep -q .; then
  echo "日志契约端口 ${runtime_port} 已被占用。" >&2
  exit 1
fi

mkdir -p "${runtime_dir}"
: >"${stdout_log}"
: >"${rolling_log}"

cd "${BACKEND_DIR}"
if [[ "${LOGGING_SKIP_BUILD:-false}" != "true" ]]; then
  mvn -pl knowledge-retrieval-service -am -DskipTests package >/dev/null
fi
artifact="${BACKEND_DIR}/knowledge-retrieval-service/target/knowledge-retrieval-service-0.0.1-SNAPSHOT.jar"
if [[ ! -f "${artifact}" ]]; then
  echo "缺少 knowledge-retrieval-service 可执行包。" >&2
  exit 1
fi

env \
  "LEETMODEL_LOG_DIR=${runtime_dir}" \
  SERVICE_INSTANCE=logging-contract \
  SERVICE_VERSION=0.0.1-contract \
  java -jar "${artifact}" \
  "--server.port=${runtime_port}" \
  --spring.profiles.active=test \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  >"${stdout_log}" 2>&1 &
runtime_pid=$!

ready=false
for _ in {1..90}; do
  if ! kill -0 "${runtime_pid}" 2>/dev/null; then
    echo "日志契约临时服务提前退出。" >&2
    tail -n 40 "${stdout_log}" >&2
    exit 1
  fi
  if curl -fsS "http://127.0.0.1:${runtime_port}/actuator/health/readiness" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "${ready}" != "true" ]]; then
  echo "日志契约临时服务未在 90 秒内就绪。" >&2
  exit 1
fi

curl --silent --show-error \
  --header 'Content-Type: application/json' \
  --header 'X-Trace-Id: logging-contract-trace' \
  --data '{}' \
  "http://127.0.0.1:${runtime_port}/internal/knowledge-retrieval/runs" >/dev/null

runtime_secret="runtime-log-secret-9f4a2"
curl --silent --show-error \
  --header 'Content-Type: application/json' \
  --header "X-Trace-Id: password=${runtime_secret}" \
  --data '{"query":"prompt=runtime-log-secret-9f4a2\\r\\nFORGED_RECORD","topK":1,"tokenBudget":128}' \
  "http://127.0.0.1:${runtime_port}/internal/knowledge-retrieval/runs" >/dev/null || true

access_event=false
for _ in {1..20}; do
  if rg -q '"eventCode":"HTTP_REQUEST_COMPLETED".*"routeTemplate":"/internal/knowledge-retrieval/runs"' \
      "${stdout_log}"; then
    access_event=true
    break
  fi
  sleep 1
done
if [[ "${access_event}" != "true" ]]; then
  echo "未观察到带路由模板的 HTTP 结构化事件。" >&2
  tail -n 40 "${stdout_log}" >&2
  exit 1
fi

python3 - "${stdout_log}" "${rolling_log}" <<'PY'
import datetime
import json
import pathlib
import sys

required = {
    "schemaVersion", "timestamp", "level", "eventCode", "message", "service",
    "environment", "serviceVersion", "instance", "logger", "thread", "traceId",
    "swTraceId", "swSpanId", "requestId", "operationId", "httpMethod",
    "routeTemplate", "statusCode", "durationMs", "errorCode", "businessType",
    "businessId", "domainTaskId", "attemptNo", "eventId", "aiCallId",
    "messageTopic", "consumerGroup", "retryCount", "suppressedCount", "taskState", "claimType",
    "aiPriority", "aiCallType", "outcome", "exceptionType", "failureCategory",
    "stackTrace",
}

for filename in sys.argv[1:]:
    path = pathlib.Path(filename)
    lines = [line for line in path.read_text(encoding="utf-8").splitlines() if line]
    if not lines:
        raise SystemExit(f"empty log file: {path}")
    for number, line in enumerate(lines, 1):
        value = json.loads(line)
        missing = required.difference(value)
        if missing:
            raise SystemExit(f"{path}:{number} missing fields: {sorted(missing)}")
        if value["schemaVersion"] != "leetmodel.log.v1":
            raise SystemExit(f"{path}:{number} has wrong schema version")
        if value["service"] != "knowledge-retrieval-service":
            raise SystemExit(f"{path}:{number} has wrong service")
        if value["environment"] != "test" or value["instance"] != "logging-contract":
            raise SystemExit(f"{path}:{number} has wrong resource identity")
        datetime.datetime.fromisoformat(value["timestamp"].replace("Z", "+00:00"))
        def walk(item, key=""):
            if isinstance(item, dict):
                for child_key, child in item.items():
                    yield from walk(child, child_key)
            elif isinstance(item, list):
                for child in item:
                    yield from walk(child, key)
            elif isinstance(item, str):
                yield key, item
        for key, text in walk(value):
            if any(ord(char) < 32 or ord(char) in (0x2028, 0x2029) for char in text):
                raise SystemExit(f"{path}:{number} contains a control character in {key}")
            limit = 1024 if key == "message" else 512 if key == "stackTrace" else 256
            if key in {"traceId", "swTraceId", "swSpanId", "requestId", "operationId",
                       "businessId", "domainTaskId", "eventId", "aiCallId"}:
                limit = 128
            if len(text) > limit:
                raise SystemExit(f"{path}:{number} exceeds {key} length limit")
            if "runtime-log-secret-9f4a2" in text or "FORGED_RECORD" in text:
                raise SystemExit(f"{path}:{number} leaked malicious input in {key}")

events = [json.loads(line) for line in pathlib.Path(sys.argv[1]).read_text().splitlines() if line]
access = [value for value in events if value["eventCode"] == "HTTP_REQUEST_COMPLETED"
          and value["routeTemplate"] == "/internal/knowledge-retrieval/runs"]
if len(access) < 2:
    raise SystemExit(f"expected at least two application access events, found {len(access)}")
trusted = [value for value in access if value["traceId"] == "logging-contract-trace"]
if len(trusted) != 1:
    raise SystemExit("access event did not preserve trusted traceId")
if trusted[0]["httpMethod"] != "POST" or not isinstance(trusted[0]["durationMs"], int):
    raise SystemExit("access event fields have wrong types")
PY

echo "[通过] 真实 Servlet stdout/轮转无敏感输入、控制字符和超长字段，路由与 traceId 契约成立"
