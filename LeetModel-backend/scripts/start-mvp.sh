#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.mvp-runtime"
NACOS_DIR="${NACOS_HOME:-${HOME}/repo/nacos}"
SKIP_BUILD=false

if [[ "${1:-}" == "--skip-build" ]]; then
  SKIP_BUILD=true
elif [[ $# -gt 0 ]]; then
  echo "用法: $0 [--skip-build]" >&2
  exit 2
fi

services=(
  user-service problem-service team-service ai-gateway-service
  submission-service ai-review-service ranking-service ai-suggestion-service
  ai-assistant-service ai-evaluation-service admin-service gateway-service
)
ports=(8081 8083 8082 8090 8085 8086 8087 8088 8089 8091 8084 8080)

mkdir -p "${RUNTIME_DIR}/logs"

cd "${BACKEND_DIR}"
docker compose up -d --wait mysql redis minio

if ! curl -fsS "http://127.0.0.1:8848/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
  if [[ ! -x "${NACOS_DIR}/bin/startup.sh" ]]; then
    echo "Nacos 未运行，且未找到 ${NACOS_DIR}/bin/startup.sh。可通过 NACOS_HOME 指定目录。" >&2
    exit 1
  fi
  "${NACOS_DIR}/bin/startup.sh" -m standalone
  nacos_ready=false
  for _ in {1..60}; do
    if curl -fsS "http://127.0.0.1:8848/nacos/v1/console/health/readiness" >/dev/null 2>&1; then
      nacos_ready=true
      break
    fi
    sleep 1
  done
  if [[ "${nacos_ready}" == false ]]; then
    echo "Nacos 在 60 秒内未就绪，请检查 ${NACOS_DIR}/logs/start.out。" >&2
    exit 1
  fi
fi
echo "Nacos 已就绪（${NACOS_DIR}）"

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
    command_line="$(tr '\0' ' ' <"/proc/${old_pid}/cmdline" 2>/dev/null || true)"
    if kill -0 "${old_pid}" 2>/dev/null && [[ "${command_line}" == *"/${service}-0.0.1-SNAPSHOT.jar"* ]]; then
      echo "${service} 已在运行（PID ${old_pid}）。" >&2
      exit 1
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

  nohup java -jar "${jar}" </dev/null >"${log_file}" 2>&1 &
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
    if curl -fsS "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
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
  echo "已启动 ${service}（端口 ${port}，PID ${pid}）"
done

echo "MVP 后端已就绪：http://127.0.0.1:8080"
echo "日志目录：${RUNTIME_DIR}/logs"
