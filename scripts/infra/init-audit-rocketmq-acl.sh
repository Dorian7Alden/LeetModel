#!/usr/bin/env bash
set -euo pipefail

ROCKETMQ_ADMIN_CONTAINER_VALUE="${ROCKETMQ_ADMIN_CONTAINER:-leetmodel-rocketmq-broker}"
ROCKETMQ_NAMESERVER_ADDRESS_VALUE="${ROCKETMQ_NAMESERVER_ADDRESS:-rocketmq-namesrv:9876}"
ROCKETMQ_CLUSTER_VALUE="${ROCKETMQ_CLUSTER:-LeetModelLocalCluster}"
ROCKETMQ_NAMESPACE_VALUE="${ROCKETMQ_NAMESPACE:-lm-dev}"
AUDIT_PRODUCER_USER="${AUDIT_MQ_PRODUCER_USERNAME:?AUDIT_MQ_PRODUCER_USERNAME is required}"
AUDIT_PRODUCER_SECRET="${AUDIT_MQ_PRODUCER_PASSWORD:?AUDIT_MQ_PRODUCER_PASSWORD is required}"
AUDIT_ARCHIVE_USER="${AUDIT_MQ_ARCHIVE_USERNAME:?AUDIT_MQ_ARCHIVE_USERNAME is required}"
AUDIT_ARCHIVE_SECRET="${AUDIT_MQ_ARCHIVE_PASSWORD:?AUDIT_MQ_ARCHIVE_PASSWORD is required}"

if [[ ! "${ROCKETMQ_NAMESPACE_VALUE}" =~ ^[a-zA-Z0-9_-]{1,80}$ ]]; then
  echo "ROCKETMQ_NAMESPACE 格式非法。" >&2
  exit 2
fi
if [[ ! "${AUDIT_PRODUCER_USER}" =~ ^[A-Za-z0-9._-]{3,64}$ ]] \
    || [[ ! "${AUDIT_ARCHIVE_USER}" =~ ^[A-Za-z0-9._-]{3,64}$ ]]; then
  echo "审计 MQ 用户名格式非法。" >&2
  exit 2
fi
if (( ${#AUDIT_PRODUCER_SECRET} < 16 || ${#AUDIT_ARCHIVE_SECRET} < 16 )); then
  echo "审计 MQ 密码必须至少 16 个字符。" >&2
  exit 2
fi

TOPIC="${ROCKETMQ_NAMESPACE_VALUE}%leetmodel-operation-audit-v1"
GROUP="${ROCKETMQ_NAMESPACE_VALUE}%cg-audit-archive-v1"

mqadmin() {
  docker exec "${ROCKETMQ_ADMIN_CONTAINER_VALUE}" ./mqadmin \
    "$@" -n "${ROCKETMQ_NAMESERVER_ADDRESS_VALUE}"
}

upsert_user() {
  local username="$1"
  local password="$2"
  local user_info
  user_info="$(mqadmin getUser -c "${ROCKETMQ_CLUSTER_VALUE}" -u "${username}" 2>/dev/null || true)"
  if grep -Fq "${username}" <<<"${user_info}"; then
    mqadmin updateUser -c "${ROCKETMQ_CLUSTER_VALUE}" -u "${username}" \
      -p "${password}" >/dev/null
    mqadmin updateUser -c "${ROCKETMQ_CLUSTER_VALUE}" -u "${username}" \
      -t Normal >/dev/null
    mqadmin updateUser -c "${ROCKETMQ_CLUSTER_VALUE}" -u "${username}" \
      -s enable >/dev/null
  else
    mqadmin createUser -c "${ROCKETMQ_CLUSTER_VALUE}" -u "${username}" \
      -p "${password}" -t Normal >/dev/null
  fi
}

upsert_acl() {
  local subject="$1"
  local resources="$2"
  local actions="$3"
  if mqadmin getAcl -c "${ROCKETMQ_CLUSTER_VALUE}" -s "${subject}" 2>/dev/null \
      | grep -Fq "${resources%%,*}"; then
    mqadmin updateAcl -c "${ROCKETMQ_CLUSTER_VALUE}" -s "${subject}" \
      -r "${resources}" -a "${actions}" -d Allow >/dev/null
  else
    mqadmin createAcl -c "${ROCKETMQ_CLUSTER_VALUE}" -s "${subject}" \
      -r "${resources}" -a "${actions}" -d Allow >/dev/null
  fi
}

upsert_user "${AUDIT_PRODUCER_USER}" "${AUDIT_PRODUCER_SECRET}"
upsert_user "${AUDIT_ARCHIVE_USER}" "${AUDIT_ARCHIVE_SECRET}"
upsert_acl "User:${AUDIT_PRODUCER_USER}" "Topic:${TOPIC}" "Pub"
upsert_acl "User:${AUDIT_ARCHIVE_USER}" "Topic:${TOPIC},Group:${GROUP}" "Sub"

echo "操作审计 ACL 已就绪：producer=Topic:Pub，archive=Topic+Group:Sub（凭据未输出）"
