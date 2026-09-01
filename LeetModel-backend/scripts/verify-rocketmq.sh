#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROCKETMQ_NAMESPACE_VALUE="${ROCKETMQ_NAMESPACE:-lm-dev}"
NAMESERVER_ADDRESS="rocketmq-namesrv:9876"
TOPIC="${ROCKETMQ_NAMESPACE_VALUE}%review-task-v1"
CONSUMER_GROUP="${ROCKETMQ_NAMESPACE_VALUE}%cg-ai-review-task-v1"
PROBE_KEY="mq1-probe-$(date +%s)"

cd "${BACKEND_DIR}"
"${SCRIPT_DIR}/init-rocketmq.sh"

docker compose exec -T rocketmq-broker ./mqadmin topicStatus \
  -n "${NAMESERVER_ADDRESS}" -t "${TOPIC}" >/dev/null

consumer_config="$(docker compose exec -T rocketmq-broker ./mqadmin getConsumerConfig \
  -n "${NAMESERVER_ADDRESS}" -g "${CONSUMER_GROUP}")"
if ! grep -Eq 'retryMaxTimes[[:space:]]*=[[:space:]]*5' <<<"${consumer_config}"; then
  echo "消费组最大重试次数不是 5。" >&2
  exit 1
fi

send_result="$(docker compose exec -T rocketmq-broker ./mqadmin sendMessage \
  -n "${NAMESERVER_ADDRESS}" -t "${TOPIC}" -c MQ1_PROBE -k "${PROBE_KEY}" \
  -p '{"probe":"rocketmq-volume"}')"
if ! grep -q 'SEND_OK' <<<"${send_result}"; then
  echo "RocketMQ 探针消息发送失败。" >&2
  exit 1
fi

if [[ "${ROCKETMQ_VERIFY_RESTART:-false}" == "true" ]]; then
  docker compose restart rocketmq-broker
  docker compose up -d --wait rocketmq-broker
fi

for _ in {1..10}; do
  if docker compose exec -T rocketmq-broker ./mqadmin queryMsgByKey \
      -n "${NAMESERVER_ADDRESS}" -t "${TOPIC}" -k "${PROBE_KEY}" 2>/dev/null \
      | grep -q "${PROBE_KEY}"; then
    echo "RocketMQ 验证通过：topic=${TOPIC}，consumerGroup=${CONSUMER_GROUP}，probe=${PROBE_KEY}"
    exit 0
  fi
  sleep 1
done

echo "无法按 Key 查询到 RocketMQ 探针消息。" >&2
exit 1
