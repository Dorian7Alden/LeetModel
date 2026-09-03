#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_DIR="$(cd "${BACKEND_DIR}/.." && pwd)"

"${SCRIPT_DIR}/verify-actuator-contract.sh"
"${SCRIPT_DIR}/verify-logging-contract.sh"
"${SCRIPT_DIR}/verify-metric-contract.sh"
"${SCRIPT_DIR}/verify-audit-contract.sh"
"${SCRIPT_DIR}/verify-safety-contract.sh"
"${SCRIPT_DIR}/drill-fault-protection.sh"

while IFS= read -r script; do
  bash -n "${script}"
done < <(find "${SCRIPT_DIR}" -maxdepth 1 -type f -name '*.sh' -print)

if git -C "${REPO_DIR}" diff --check; then
  echo "最终静态门禁通过；未执行任何远端或固定业务进程操作。"
fi
