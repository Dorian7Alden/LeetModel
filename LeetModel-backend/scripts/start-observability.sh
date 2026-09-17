#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime"
TOKEN_FILE="${LEETMODEL_MANAGEMENT_TOKEN_FILE:-${RUNTIME_DIR}/management-token}"
RUNTIME_TARGETS_DIR="${RUNTIME_DIR}/prometheus-targets"
RUNTIME_RULES_DIR="${RUNTIME_DIR}/prometheus-rules"

mkdir -p "${RUNTIME_TARGETS_DIR}" "${RUNTIME_RULES_DIR}"
mkdir -p "$(dirname "${TOKEN_FILE}")"
chmod 750 "${RUNTIME_DIR}"

if [[ -n "${MANAGEMENT_TOKEN:-}" ]]; then
  printf '%s' "${MANAGEMENT_TOKEN}" >"${TOKEN_FILE}"
elif [[ ! -s "${TOKEN_FILE}" ]]; then
  if command -v openssl >/dev/null 2>&1; then
    generated_token="$(openssl rand -hex 32)"
    printf '%s' "${generated_token}" >"${TOKEN_FILE}"
  else
    token_seed="$(date -u '+%s%N')-$$-${RANDOM}-${RANDOM}"
    printf '%s' "${token_seed}" | sha256sum | awk '{printf "%s", $1}' >"${TOKEN_FILE}"
  fi
fi
chmod 640 "${TOKEN_FILE}"

if [[ ! -e "${RUNTIME_TARGETS_DIR}/runtime.json" ]]; then
  printf '[]\n' >"${RUNTIME_TARGETS_DIR}/runtime.json"
fi
if [[ ! -e "${RUNTIME_RULES_DIR}/runtime.yml" ]]; then
  printf 'groups: []\n' >"${RUNTIME_RULES_DIR}/runtime.yml"
fi

export LEETMODEL_MANAGEMENT_TOKEN_FILE="${TOKEN_FILE}"
export LEETMODEL_MANAGEMENT_TOKEN_GID="${LEETMODEL_MANAGEMENT_TOKEN_GID:-$(id -g)}"

docker compose -f "${COMPOSE_FILE}" up -d --wait

curl --fail --silent --show-error http://127.0.0.1:19090/-/ready >/dev/null
curl --fail --silent --show-error http://127.0.0.1:19093/-/ready >/dev/null
curl --fail --silent --show-error http://127.0.0.1:13000/api/health >/dev/null
curl --fail --silent --show-error http://127.0.0.1:18080/ >/dev/null

echo "可观测性基线已就绪："
echo "  SkyWalking Horizon: http://127.0.0.1:18080"
echo "  SkyWalking OAP API: http://127.0.0.1:12800"
echo "  Prometheus:         http://127.0.0.1:19090"
echo "  Alertmanager:       http://127.0.0.1:19093"
echo "  Grafana:            http://127.0.0.1:13000"
echo "  管理 Token 文件:    ${TOKEN_FILE}"
echo "Horizon 默认不创建账户；需要登录时通过环境变量注入 Argon2id 用户配置。"
