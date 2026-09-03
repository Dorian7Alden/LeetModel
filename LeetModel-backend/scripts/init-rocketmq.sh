#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROCKETMQ_NAMESPACE_VALUE="${ROCKETMQ_NAMESPACE:-lm-dev}"
NAMESERVER_ADDRESS="rocketmq-namesrv:9876"
CLUSTER_NAME="LeetModelLocalCluster"

if [[ ! "${ROCKETMQ_NAMESPACE_VALUE}" =~ ^[a-zA-Z0-9_-]{1,80}$ ]]; then
  echo "ROCKETMQ_NAMESPACE 只能包含字母、数字、下划线和连字符，且长度不超过 80。" >&2
  exit 2
fi

topics=(
  review-task-v1 suggestion-task-v1 evaluation-task-v1
  submission-event-v1 review-event-v1
  leetmodel-operation-audit-v1
)

consumer_groups=(
  cg-ai-review-task-v1 cg-ai-suggestion-task-v1 cg-ai-evaluation-task-v1
  cg-ranking-submission-v1 cg-ranking-review-v1
  cg-audit-archive-v1
)

physical_name() {
  local logical_name="$1"
  printf '%s%%%s' "${ROCKETMQ_NAMESPACE_VALUE}" "${logical_name}"
}

cd "${BACKEND_DIR}"

if ! docker compose exec -T rocketmq-broker ./mqadmin clusterList \
  -n "${NAMESERVER_ADDRESS}" | grep -q "${CLUSTER_NAME}"; then
  echo "RocketMQ Broker 尚未向 NameServer 注册。" >&2
  exit 1
fi

for topic in "${topics[@]}"; do
  docker compose exec -T rocketmq-broker ./mqadmin updateTopic \
    -n "${NAMESERVER_ADDRESS}" \
    -c "${CLUSTER_NAME}" \
    -t "$(physical_name "${topic}")" \
    -r 4 \
    -w 4 \
    -p 6 \
    -a '+message.type=NORMAL' >/dev/null
done

for consumer_group in "${consumer_groups[@]}"; do
  docker compose exec -T rocketmq-broker ./mqadmin updateSubGroup \
    -n "${NAMESERVER_ADDRESS}" \
    -c "${CLUSTER_NAME}" \
    -g "$(physical_name "${consumer_group}")" \
    -r 5 \
    -p '{"type":"CUSTOMIZED","customizedRetryPolicy":{"next":[1000,5000,30000,120000,600000]}}' \
    >/dev/null
done

echo "RocketMQ 资源已就绪：namespace=${ROCKETMQ_NAMESPACE_VALUE}，topics=${#topics[@]}，consumerGroups=${#consumer_groups[@]}"
