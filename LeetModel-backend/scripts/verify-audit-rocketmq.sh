#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="$(date +%Y%m%d%H%M%S)-$$"
RUNTIME_DIR="${BACKEND_DIR}/.observability-runtime/audit-rocketmq/${RUN_ID}"
NETWORK="leetmodel-audit-mq-${RUN_ID}"
NAMESRV_CONTAINER="leetmodel-audit-namesrv-${RUN_ID}"
BROKER_CONTAINER="leetmodel-audit-broker-${RUN_ID}"
NAMESRV_PORT="${AUDIT_ROCKETMQ_NAMESERVER_PORT:-29876}"
BROKER_PORT="${AUDIT_ROCKETMQ_BROKER_PORT:-30911}"
CLUSTER="LeetModelAuditContractCluster"
NAMESPACE="lm-audit-contract"
TOPIC="${NAMESPACE}%leetmodel-operation-audit-v1"
GROUP="${NAMESPACE}%cg-audit-archive-v1"
DLQ_TOPIC="%DLQ%${GROUP}"
DLQ_KEY="audit-dlq-${RUN_ID}"
ADMIN_USER="audit-contract-admin"
PRODUCER_USER="audit-contract-producer"
ARCHIVE_USER="audit-contract-archive"
ADMIN_SECRET="Adm1!$(openssl rand -hex 18)"
PRODUCER_SECRET="Pub1!$(openssl rand -hex 18)"
ARCHIVE_SECRET="Sub1!$(openssl rand -hex 18)"
MAVEN_TEST_SELECTOR="${AUDIT_MAVEN_TEST_SELECTOR:-OperationAuditContractTest,OperationAuditMessageCodecTest,OperationAuditRocketMqIntegrationTest}"

cleanup() {
  local status=$?
  if (( status != 0 )); then
    docker logs "${NAMESRV_CONTAINER}" >"${RUNTIME_DIR}/namesrv.log" 2>&1 || true
    docker logs "${BROKER_CONTAINER}" >"${RUNTIME_DIR}/broker.log" 2>&1 || true
    echo "隔离 RocketMQ 验证失败，诊断日志：${RUNTIME_DIR}" >&2
  fi
  docker rm -f "${BROKER_CONTAINER}" "${NAMESRV_CONTAINER}" >/dev/null 2>&1 || true
  docker network rm "${NETWORK}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p "${RUNTIME_DIR}"
chmod 700 "${RUNTIME_DIR}"

printf 'listenPort=%s\n' "${NAMESRV_PORT}" > "${RUNTIME_DIR}/namesrv.conf"
{
  printf '%s\n' \
    "brokerClusterName=${CLUSTER}" \
    'brokerName=audit-contract-broker' \
    'brokerId=0' \
    'brokerIP1=127.0.0.1' \
    "listenPort=${BROKER_PORT}" \
    "namesrvAddr=audit-namesrv:${NAMESRV_PORT}" \
    'brokerRole=ASYNC_MASTER' \
    'flushDiskType=ASYNC_FLUSH' \
    'fileReservedTime=1' \
    'autoCreateTopicEnable=false' \
    'autoCreateSubscriptionGroup=false' \
    'authenticationEnabled=true' \
    'authenticationMetadataProvider=org.apache.rocketmq.auth.authentication.provider.LocalAuthenticationMetadataProvider' \
    'authorizationEnabled=true' \
    'authorizationMetadataProvider=org.apache.rocketmq.auth.authorization.provider.LocalAuthorizationMetadataProvider' \
    "initAuthenticationUser={\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_SECRET}\"}" \
    "innerClientAuthenticationCredentials={\"accessKey\":\"${ADMIN_USER}\",\"secretKey\":\"${ADMIN_SECRET}\"}"
} > "${RUNTIME_DIR}/broker.conf"
{
  printf 'accessKey: %s\nsecretKey: %s\n' "${ADMIN_USER}" "${ADMIN_SECRET}"
} > "${RUNTIME_DIR}/tools.yml"
chmod 644 "${RUNTIME_DIR}/namesrv.conf" "${RUNTIME_DIR}/broker.conf" "${RUNTIME_DIR}/tools.yml"

docker network create "${NETWORK}" >/dev/null
docker run -d --name "${NAMESRV_CONTAINER}" --network "${NETWORK}" \
  --network-alias audit-namesrv \
  -p "127.0.0.1:${NAMESRV_PORT}:${NAMESRV_PORT}" \
  -e JAVA_OPT_EXT='-Xms128m -Xmx256m -Xmn64m' \
  -v "${RUNTIME_DIR}/namesrv.conf:/home/rocketmq/rocketmq-5.5.0/conf/audit-contract-namesrv.conf:ro" \
  apache/rocketmq:5.5.0 /bin/bash -lc \
  'exec ./mqnamesrv -c /home/rocketmq/rocketmq-5.5.0/conf/audit-contract-namesrv.conf' >/dev/null
docker run -d --name "${BROKER_CONTAINER}" --network "${NETWORK}" \
  --network-alias audit-broker \
  -p "127.0.0.1:$((BROKER_PORT - 2)):$((BROKER_PORT - 2))" \
  -p "127.0.0.1:${BROKER_PORT}:${BROKER_PORT}" \
  -p "127.0.0.1:$((BROKER_PORT + 1)):$((BROKER_PORT + 1))" \
  -e JAVA_OPT_EXT='-Xms256m -Xmx512m -Xmn128m' \
  -v "${RUNTIME_DIR}/broker.conf:/home/rocketmq/rocketmq-5.5.0/conf/audit-contract-broker.conf:ro" \
  -v "${RUNTIME_DIR}/tools.yml:/home/rocketmq/rocketmq-5.5.0/conf/tools.yml:ro" \
  apache/rocketmq:5.5.0 /bin/bash -lc \
  'exec ./mqbroker -c /home/rocketmq/rocketmq-5.5.0/conf/audit-contract-broker.conf' >/dev/null

for _ in {1..40}; do
  if docker exec "${BROKER_CONTAINER}" ./mqadmin clusterList \
      -n "audit-namesrv:${NAMESRV_PORT}" 2>/dev/null | grep -q "${CLUSTER}"; then
    break
  fi
  sleep 1
done
docker exec "${BROKER_CONTAINER}" ./mqadmin clusterList \
  -n "audit-namesrv:${NAMESRV_PORT}" | grep -q "${CLUSTER}"

docker exec "${BROKER_CONTAINER}" ./mqadmin updateTopic \
  -n "audit-namesrv:${NAMESRV_PORT}" -c "${CLUSTER}" -t "${TOPIC}" \
  -r 4 -w 4 -p 6 -a '+message.type=NORMAL' >/dev/null
docker exec "${BROKER_CONTAINER}" ./mqadmin updateSubGroup \
  -n "audit-namesrv:${NAMESRV_PORT}" -c "${CLUSTER}" -g "${GROUP}" \
  -r 1 -p '{"type":"CUSTOMIZED","customizedRetryPolicy":{"next":[1000]}}' >/dev/null

ROCKETMQ_ADMIN_CONTAINER="${BROKER_CONTAINER}" \
ROCKETMQ_NAMESERVER_ADDRESS="audit-namesrv:${NAMESRV_PORT}" \
ROCKETMQ_CLUSTER="${CLUSTER}" \
ROCKETMQ_NAMESPACE="${NAMESPACE}" \
AUDIT_MQ_PRODUCER_USERNAME="${PRODUCER_USER}" \
AUDIT_MQ_PRODUCER_PASSWORD="${PRODUCER_SECRET}" \
AUDIT_MQ_ARCHIVE_USERNAME="${ARCHIVE_USER}" \
AUDIT_MQ_ARCHIVE_PASSWORD="${ARCHIVE_SECRET}" \
  "${SCRIPT_DIR}/init-audit-rocketmq-acl.sh"

RUN_AUDIT_ROCKETMQ_INTEGRATION=true \
AUDIT_ROCKETMQ_NAMESERVER="127.0.0.1:${NAMESRV_PORT}" \
AUDIT_ROCKETMQ_TOPIC="${TOPIC}" \
AUDIT_ROCKETMQ_GROUP="${GROUP}" \
AUDIT_ROCKETMQ_DLQ_KEY="${DLQ_KEY}" \
AUDIT_ROCKETMQ_PRODUCER_USER="${PRODUCER_USER}" \
AUDIT_ROCKETMQ_PRODUCER_SECRET="${PRODUCER_SECRET}" \
AUDIT_ROCKETMQ_ARCHIVE_USER="${ARCHIVE_USER}" \
AUDIT_ROCKETMQ_ARCHIVE_SECRET="${ARCHIVE_SECRET}" \
  mvn -f "${BACKEND_DIR}/pom.xml" -pl common/common-messaging -am \
    -Dtest="${MAVEN_TEST_SELECTOR}" \
    -Dsurefire.failIfNoSpecifiedTests=false test

for _ in {1..20}; do
  if docker exec "${BROKER_CONTAINER}" ./mqadmin queryMsgByKey \
      -n "audit-namesrv:${NAMESRV_PORT}" -t "${DLQ_TOPIC}" -k "${DLQ_KEY}" 2>/dev/null \
      | grep -q "${DLQ_KEY}"; then
    dlq_found=true
    break
  fi
  sleep 1
done
if [[ "${dlq_found:-false}" != "true" ]]; then
  echo "重试耗尽后的审计消息未进入专用消费组 DLQ。" >&2
  exit 1
fi

docker exec "${BROKER_CONTAINER}" ./mqadmin updateSubGroup \
  -n "audit-namesrv:${NAMESRV_PORT}" -c "${CLUSTER}" -g "${GROUP}" \
  -r 5 -p '{"type":"CUSTOMIZED","customizedRetryPolicy":{"next":[1000,5000,30000,120000,600000]}}' \
  >/dev/null
consumer_config="$(docker exec "${BROKER_CONTAINER}" ./mqadmin getConsumerConfig \
  -n "audit-namesrv:${NAMESRV_PORT}" -g "${GROUP}")"
grep -Eq 'retryMaxTimes[[:space:]]*=[[:space:]]*5' <<<"${consumer_config}"
grep -Fq '1000, 5000, 30000, 120000, 600000' <<<"${consumer_config}"

producer_acl="$(docker exec "${BROKER_CONTAINER}" ./mqadmin getAcl \
  -n "audit-namesrv:${NAMESRV_PORT}" -c "${CLUSTER}" -s "User:${PRODUCER_USER}")"
archive_acl="$(docker exec "${BROKER_CONTAINER}" ./mqadmin getAcl \
  -n "audit-namesrv:${NAMESRV_PORT}" -c "${CLUSTER}" -s "User:${ARCHIVE_USER}")"
grep -Fq "Topic:${TOPIC}" <<<"${producer_acl}"
grep -Eq '(^|[^[:alnum:]_])Pub([^[:alnum:]_]|$)' <<<"${producer_acl}"
if grep -Eq '(^|[^[:alnum:]_])Sub([^[:alnum:]_]|$)' <<<"${producer_acl}"; then
  echo "审计生产者 ACL 不得包含 Sub。" >&2
  exit 1
fi
grep -Fq "Topic:${TOPIC}" <<<"${archive_acl}"
grep -Fq "Group:${GROUP}" <<<"${archive_acl}"
grep -Eq '(^|[^[:alnum:]_])Sub([^[:alnum:]_]|$)' <<<"${archive_acl}"
if grep -Eq '(^|[^[:alnum:]_])Pub([^[:alnum:]_]|$)' <<<"${archive_acl}"; then
  echo "审计归档消费者 ACL 不得包含 Pub。" >&2
  exit 1
fi

echo "操作审计 RocketMQ 验证通过：严格消息、最小 ACL、固定重试与 DLQ 均已验证（凭据未输出）。"
