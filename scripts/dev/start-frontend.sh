#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/paths.sh"

for command_name in node npm; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "缺少前端启动命令：${command_name}" >&2
    exit 1
  fi
done

cd "${FRONTEND_DIR}"
if [[ ! -d node_modules ]]; then
  npm ci
fi
exec npm run dev
