#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIVE=false
if [[ "${1:-}" == "--live" ]]; then LIVE=true; fi

declare -a SCENARIOS=(
  "telemetry_oap_outage|verify-skywalking-logging.sh|OAP 中断、Reporter 有界队列与本地日志兜底"
  "prometheus_alerting_outage|drill-alerting.sh|Prometheus/Alertmanager firing-resolved 与数据空洞"
  "reliable_message_failure|drill-messaging-failures.sh|Outbox 重试、Inbox 重复、DLQ 与受控恢复"
  "correlation_fallback|drill-observability-correlation.sh|告警到 Trace/日志/业务事实的回退关联"
)

echo "故障演练矩阵（LIVE=${LIVE}）"
for entry in "${SCENARIOS[@]}"; do
  IFS='|' read -r scenario script expected <<<"${entry}"
  path="${SCRIPT_DIR}/${script}"
  [[ -x "${path}" ]] || { echo "缺少可执行演练脚本: ${script}" >&2; exit 1; }
  printf '%-28s %-36s %s\n' "${scenario}" "${script}" "${expected}"
done

if [[ "${LIVE}" != true ]]; then
  echo "仅输出演练计划；显式传入 --live 才会调用隔离演练脚本。"
  exit 0
fi

for entry in "${SCENARIOS[@]}"; do
  IFS='|' read -r scenario script _ <<<"${entry}"
  echo "开始场景: ${scenario}"
  "${SCRIPT_DIR}/${script}"
  echo "场景通过: ${scenario}"
done
echo "故障演练矩阵完成；详见 docs/runbooks/observability/fault-drills.md。"
