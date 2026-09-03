#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.mvp-runtime"
SKIP_BUILD=false
SKYWALKING_ENABLED="${LEETMODEL_SKYWALKING_ENABLED:-false}"
SKYWALKING_BACKEND="${LEETMODEL_SKYWALKING_BACKEND:-127.0.0.1:11800}"
SKYWALKING_SAMPLE="${LEETMODEL_SKYWALKING_SAMPLE:-100}"
SKYWALKING_LOG_ENABLED="${LEETMODEL_SKYWALKING_LOG_ENABLED:-${SKYWALKING_ENABLED}}"
SKYWALKING_LOG_ENDPOINT="${LEETMODEL_SKYWALKING_LOG_ENDPOINT:-http://127.0.0.1:12800/v3/logs}"
TELEMETRY_ENVIRONMENT="${LEETMODEL_ENVIRONMENT:-${SPRING_PROFILES_ACTIVE:-dev}}"
SERVICE_VERSION_VALUE="${SERVICE_VERSION:-0.0.1-SNAPSHOT}"
SKYWALKING_AGENT_JAR=""
management_curl_args=()
OBSERVABILITY_TOKEN_FILE="${LEETMODEL_MANAGEMENT_TOKEN_FILE:-${BACKEND_DIR}/.observability-runtime/management-token}"
AUDIT_DB_URL_VALUE="${AUDIT_DB_URL:-jdbc:mysql://localhost:3306/lm_audit?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai}"
AUDIT_DB_APP_USERNAME_VALUE="${AUDIT_DB_APP_USERNAME:-lm_audit_app}"
AUDIT_DB_APP_PASSWORD_VALUE="${AUDIT_DB_APP_PASSWORD:-lm-audit-app-local-only-change-me}"
AUDIT_DB_MIGRATOR_USERNAME_VALUE="${AUDIT_DB_MIGRATOR_USERNAME:-lm_audit_migrator}"
AUDIT_DB_MIGRATOR_PASSWORD_VALUE="${AUDIT_DB_MIGRATOR_PASSWORD:-lm-audit-migrator-local-only-change-me}"

if [[ -z "${MANAGEMENT_TOKEN:-}" && -s "${OBSERVABILITY_TOKEN_FILE}" ]]; then
  MANAGEMENT_TOKEN="$(<"${OBSERVABILITY_TOKEN_FILE}")"
  export MANAGEMENT_TOKEN
fi

if [[ -n "${MANAGEMENT_TOKEN:-}" ]]; then
  management_curl_args=(-H "X-LeetModel-Management-Token: ${MANAGEMENT_TOKEN}")
fi

if [[ "${1:-}" == "--skip-build" ]]; then
  SKIP_BUILD=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--skip-build]" >&2
  exit 2
fi

services=(
  user-service problem-service team-service ai-gateway-service
  submission-service ai-review-service ranking-service knowledge-retrieval-service
  ai-suggestion-service ai-assistant-service ai-evaluation-service admin-service gateway-service
  audit-service
)
ports=(8081 8083 8082 8090 8092 8086 8087 8093 8088 8089 8091 8084 8080 8094)

mkdir -p "${RUNTIME_DIR}/logs"

if [[ "${SKYWALKING_ENABLED}" == "true" ]]; then
  if [[ ! "${SKYWALKING_SAMPLE}" =~ ^-?[0-9]+$ ]]; then
    echo "LEETMODEL_SKYWALKING_SAMPLE 必须是整数。" >&2
    exit 1
  fi
  for resource_value in "${TELEMETRY_ENVIRONMENT}" "${SERVICE_VERSION_VALUE}"; do
    if [[ ! "${resource_value}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$ ]]; then
      echo "SkyWalking 环境或版本资源字段不合法。" >&2
      exit 1
    fi
  done
  SKYWALKING_AGENT_JAR="$("${SCRIPT_DIR}/prepare-skywalking-agent.sh")"
  if [[ ! -f "${SKYWALKING_AGENT_JAR}" ]]; then
    echo "SkyWalking Agent 准备失败。" >&2
    exit 1
  fi
fi

cd "${BACKEND_DIR}"
docker compose up -d --wait \
  mysql redis cache-redis minio nacos elasticsearch rocketmq-namesrv rocketmq-broker
"${SCRIPT_DIR}/init-rocketmq.sh"
MYSQL_ADMIN_CONTAINER=leetmodel-mysql \
MYSQL_ADMIN_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}" \
AUDIT_DB_MIGRATOR_USERNAME="${AUDIT_DB_MIGRATOR_USERNAME_VALUE}" \
AUDIT_DB_MIGRATOR_PASSWORD="${AUDIT_DB_MIGRATOR_PASSWORD_VALUE}" \
AUDIT_DB_APP_USERNAME="${AUDIT_DB_APP_USERNAME_VALUE}" \
AUDIT_DB_APP_PASSWORD="${AUDIT_DB_APP_PASSWORD_VALUE}" \
  "${SCRIPT_DIR}/init-audit-database.sh" bootstrap
echo "Docker 基础设施已就绪（MySQL、安全 Redis、业务缓存 Redis、MinIO、Nacos、Elasticsearch、RocketMQ）"

if [[ "${SKIP_BUILD}" == false ]]; then
  mvn -DskipTests package
fi

# 在启动任何业务服务前完成全量预检，避免运行到一半才因端口冲突留下半套进程。
for index in "${!services[@]}"; do
  service="${services[$index]}"
  port="${ports[$index]}"
  jar="${BACKEND_DIR}/${service}/target/${service}-0.0.1-SNAPSHOT.jar"
  pid_file="${RUNTIME_DIR}/${service}.pid"

  if [[ ! -f "${jar}" ]]; then
    echo "缺少 ${service} 可执行包，请先去掉 --skip-build 重新运行。" >&2
    exit 1
  fi

  if [[ -f "${pid_file}" ]]; then
    old_pid="$(<"${pid_file}")"
    if [[ "${old_pid}" =~ ^[0-9]+$ ]] && kill -0 "${old_pid}" 2>/dev/null; then
      command_line=""
      if [[ -r "/proc/${old_pid}/cmdline" ]]; then
        command_line="$(tr '\0' ' ' <"/proc/${old_pid}/cmdline")"
      fi
      if [[ "${command_line}" == *"/${service}-0.0.1-SNAPSHOT.jar"* ]]; then
        echo "${service} 已在运行（PID ${old_pid}）。" >&2
        exit 1
      fi
    fi
    rm -f "${pid_file}"
  fi

  if ss -ltn "sport = :${port}" | tail -n +2 | grep -q .; then
    echo "端口 ${port} 已被占用，未启动任何业务服务。" >&2
    exit 1
  fi
done

for index in "${!services[@]}"; do
  service="${services[$index]}"
  port="${ports[$index]}"
  jar="${BACKEND_DIR}/${service}/target/${service}-0.0.1-SNAPSHOT.jar"
  pid_file="${RUNTIME_DIR}/${service}.pid"
  log_file="${RUNTIME_DIR}/logs/${service}.log"
  structured_log_dir="${RUNTIME_DIR}/logs"
  service_instance="${LEETMODEL_SERVICE_INSTANCE_PREFIX:-local}-${service}"
  if [[ ! "${service_instance}" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$ ]]; then
    echo "SkyWalking 实例名不合法或超过 50 字符：${service_instance}" >&2
    "${SCRIPT_DIR}/stop-mvp.sh"
    exit 1
  fi
  instance_properties_json="{\"environment\":\"${TELEMETRY_ENVIRONMENT}\",\"serviceVersion\":\"${SERVICE_VERSION_VALUE}\",\"instance\":\"${service_instance}\"}"

  service_env=(
    "LEETMODEL_LOG_DIR=${structured_log_dir}"
    "LEETMODEL_SKYWALKING_LOG_ENABLED=${SKYWALKING_LOG_ENABLED}"
    "LEETMODEL_SKYWALKING_LOG_ENDPOINT=${SKYWALKING_LOG_ENDPOINT}"
    "SERVICE_INSTANCE=${service_instance}"
    "SERVICE_VERSION=${SERVICE_VERSION_VALUE}"
  )
  if [[ "${service}" == "audit-service" ]]; then
    service_env+=(
      "AUDIT_DB_URL=${AUDIT_DB_URL_VALUE}"
      "AUDIT_DB_APP_USERNAME=${AUDIT_DB_APP_USERNAME_VALUE}"
      "AUDIT_DB_APP_PASSWORD=${AUDIT_DB_APP_PASSWORD_VALUE}"
      "AUDIT_DB_MIGRATOR_USERNAME=${AUDIT_DB_MIGRATOR_USERNAME_VALUE}"
      "AUDIT_DB_MIGRATOR_PASSWORD=${AUDIT_DB_MIGRATOR_PASSWORD_VALUE}"
    )
  fi

  java_command=(java)
  if [[ "${SKYWALKING_ENABLED}" == "true" ]]; then
    java_command+=(
      "-javaagent:${SKYWALKING_AGENT_JAR}"
      "-Dskywalking.agent.service_name=${service}"
      "-Dskywalking.agent.namespace=${TELEMETRY_ENVIRONMENT}"
      "-Dskywalking.agent.instance_name=${service_instance}"
      "-Dskywalking.agent.instance_properties_json=${instance_properties_json}"
      "-Dskywalking.agent.sample_n_per_3_secs=${SKYWALKING_SAMPLE}"
      "-Dskywalking.correlation.auto_tag_keys=business.trace_id"
      "-Dskywalking.plugin.exclude_plugins=feign-default-http-9.x,feign-pathvar-9.x"
      "-Dskywalking.plugin.jdbc.trace_sql_parameters=false"
      "-Dskywalking.collector.backend_service=${SKYWALKING_BACKEND}"
    )
  fi

  nohup env "${service_env[@]}" \
    "${java_command[@]}" -jar "${jar}" </dev/null >"${log_file}" 2>&1 &
  pid=$!
  printf '%s\n' "${pid}" >"${pid_file}"

  ready=false
  for _ in {1..90}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      echo "${service} 启动失败，末尾日志如下：" >&2
      tail -n 40 "${log_file}" >&2
      "${SCRIPT_DIR}/stop-mvp.sh"
      exit 1
    fi
    if curl -fsS "http://127.0.0.1:${port}/actuator/health/readiness" >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 1
  done

  if [[ "${ready}" == false ]]; then
    echo "${service} 在 90 秒内未就绪。" >&2
    tail -n 40 "${log_file}" >&2
    "${SCRIPT_DIR}/stop-mvp.sh"
    exit 1
  fi
  if ! curl -fsS "${management_curl_args[@]}" \
      "http://127.0.0.1:${port}/actuator/prometheus" \
      | grep '^jvm_info' >/dev/null; then
    echo "${service} Prometheus 端点不可抓取。" >&2
    "${SCRIPT_DIR}/stop-mvp.sh"
    exit 1
  fi
  if [[ "${service}" == "audit-service" ]]; then
    MYSQL_ADMIN_CONTAINER=leetmodel-mysql \
    MYSQL_ADMIN_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}" \
    AUDIT_DB_MIGRATOR_USERNAME="${AUDIT_DB_MIGRATOR_USERNAME_VALUE}" \
    AUDIT_DB_MIGRATOR_PASSWORD="${AUDIT_DB_MIGRATOR_PASSWORD_VALUE}" \
    AUDIT_DB_APP_USERNAME="${AUDIT_DB_APP_USERNAME_VALUE}" \
    AUDIT_DB_APP_PASSWORD="${AUDIT_DB_APP_PASSWORD_VALUE}" \
      "${SCRIPT_DIR}/init-audit-database.sh" finalize
  fi
  echo "已启动 ${service}（端口 ${port}，PID ${pid}）"
done

echo "MVP 后端已就绪：http://127.0.0.1:8080"
echo "日志目录：${RUNTIME_DIR}/logs"
