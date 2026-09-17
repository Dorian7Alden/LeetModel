#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/paths.sh"

for command_name in java mvn; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "缺少后端测试命令：${command_name}" >&2
    exit 1
  fi
done

cd "${BACKEND_DIR}"
exec mvn test "$@"
