#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/paths.sh"

"${VERIFY_SCRIPTS_DIR}/verify-actuator-contract.sh"
"${VERIFY_SCRIPTS_DIR}/verify-logging-contract.sh"
"${VERIFY_SCRIPTS_DIR}/verify-metric-contract.sh"
"${VERIFY_SCRIPTS_DIR}/verify-audit-contract.sh"
"${VERIFY_SCRIPTS_DIR}/verify-safety-contract.sh"
"${DRILL_SCRIPTS_DIR}/drill-fault-protection.sh"

while IFS= read -r script; do
  bash -n "${script}"
done < <(find "${SCRIPTS_DIR}" -type f -name '*.sh' -print)

if git -C "${REPO_ROOT}" diff --check; then
  echo "最终静态门禁通过；未执行任何远端或固定业务进程操作。"
fi
