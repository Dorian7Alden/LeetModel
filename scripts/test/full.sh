#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../lib/paths.sh"

"${TEST_SCRIPTS_DIR}/backend.sh"
"${TEST_SCRIPTS_DIR}/frontend.sh"
"${VERIFY_SCRIPTS_DIR}/verify-final-gate.sh"
