#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${BACKEND_DIR}/.mvp-runtime"

services=(
  gateway-service admin-service ai-evaluation-service ai-assistant-service
  ai-suggestion-service knowledge-retrieval-service ranking-service ai-review-service submission-service
  ai-gateway-service team-service problem-service user-service
)

for service in "${services[@]}"; do
  pid_file="${RUNTIME_DIR}/${service}.pid"
  [[ -f "${pid_file}" ]] || continue
  pid="$(<"${pid_file}")"
  command_line=""
  if [[ "${pid}" =~ ^[0-9]+$ ]] && [[ -r "/proc/${pid}/cmdline" ]]; then
    command_line="$(tr '\0' ' ' <"/proc/${pid}/cmdline")"
  fi

  if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null && [[ "${command_line}" == *"/${service}-0.0.1-SNAPSHOT.jar"* ]]; then
    kill "${pid}"
    echo "正在停止 ${service}（PID ${pid}）"
  fi
  rm -f "${pid_file}"
done

for _ in {1..20}; do
  remaining=false
  for service in "${services[@]}"; do
    if pgrep -f "/${service}-0.0.1-SNAPSHOT.jar" >/dev/null 2>&1; then
      remaining=true
      break
    fi
  done
  [[ "${remaining}" == false ]] && break
  sleep 1
done

echo "MVP 后端服务已停止；Docker 基础设施保持运行。"
