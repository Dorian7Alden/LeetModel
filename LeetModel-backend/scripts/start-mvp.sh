#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.mvp-runtime"
SKIP_BUILD=false
SKYWALKING_ENABLED="${LEETMODEL_SKYWALKING_ENABLED:-false}"
SKYWALKING_BACKEND="${LEETMODEL_SKYWALKING_BACKEND:-127.0.0.1:11800}"
SKYWALKING_AGENT_JAR=""
management_curl_args=()

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
)
ports=(8081 8083 8082 8090 8092 8086 8087 8093 8088 8089 8091 8084 8080)

mkdir -p "${RUNTIME_DIR}/logs"

if [[ "${SKYWALKING_ENABLED}" == "true" ]]; then
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

  java_command=(java)
  if [[ "${SKYWALKING_ENABLED}" == "true" ]]; then
    java_command+=(
      "-javaagent:${SKYWALKING_AGENT_JAR}"
      "-Dskywalking.agent.service_name=${service}"
      "-Dskywalking.agent.instance_name=local-${service}"
      "-Dskywalking.collector.backend_service=${SKYWALKING_BACKEND}"
    )
  fi

  nohup "${java_command[@]}" -jar "${jar}" </dev/null >"${log_file}" 2>&1 &
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
  echo "已启动 ${service}（端口 ${port}，PID ${pid}）"
done

echo "MVP 后端已就绪：http://127.0.0.1:8080"
echo "日志目录：${RUNTIME_DIR}/logs"
