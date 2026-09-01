#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${BACKEND_DIR}/docker-compose.observability.yml"

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
echo "Horizon 默认不创建账户；需要登录时通过环境变量注入 Argon2id 用户配置。"
