#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${BACKEND_DIR}/.." && pwd)"
CATALOG_FILE="${BACKEND_DIR}/common/common-api/src/main/java/com/leetmodel/common/api/audit/OperationAuditCatalog.java"
PAYLOAD_FILE="${BACKEND_DIR}/common/common-api/src/main/java/com/leetmodel/common/api/audit/OperationAuditPayloadV1.java"
CODEC_FILE="${BACKEND_DIR}/common/common-messaging/src/main/java/com/leetmodel/common/messaging/OperationAuditMessageCodec.java"
INIT_FILE="${SCRIPT_DIR}/init-rocketmq.sh"
ACL_FILE="${SCRIPT_DIR}/init-audit-rocketmq-acl.sh"
ACL_CONFIG_FILE="${BACKEND_DIR}/docker/rocketmq/broker-acl.conf.example"
AUDIT_SERVICE_DIR="${BACKEND_DIR}/audit-service"

required_tokens=(
  'AUTH.LOGIN_SUCCESS' 'USER.ROLE_CHANGE' 'PROBLEM.DELETE' 'SUBMISSION.FINALIZE'
  'AI_QUEUE.CANCEL' 'EVALUATION.RETRY' 'ASSISTANT_CONFIG.ROLLBACK'
  'CONSUMER.PAUSE' 'OUTBOX.REPLAY' 'DLQ.REPLAY' 'RANKING.REBUILD'
  'AUDIT.SEARCH_EXPORT'
)
for token in "${required_tokens[@]}"; do
  grep -Fq "${token}" "${CATALOG_FILE}" || {
    echo "P0 操作目录缺少 ${token}。" >&2
    exit 1
  }
done

for field in auditEventId operationId phase outcome actorType actorId targetType targetId \
    beforeSummary afterSummary traceId swTraceId clientIpHash userAgentHash; do
  grep -Fq "${field}" "${PAYLOAD_FILE}" || {
    echo "审计载荷缺少 ${field}。" >&2
    exit 1
  }
done

grep -Fq 'OPERATION_AUDIT_RECORDED' "${CODEC_FILE}"
grep -Fq 'payload.auditEventId(), envelope.eventId()' "${CODEC_FILE}"
grep -Fq 'FAIL_ON_UNKNOWN_PROPERTIES' "${CODEC_FILE}"
grep -Fq 'leetmodel-operation-audit-v1' "${INIT_FILE}"
grep -Fq 'cg-audit-archive-v1' "${INIT_FILE}"
grep -Fq 'Topic:${TOPIC}' "${ACL_FILE}"
grep -Fq 'Group:${GROUP}' "${ACL_FILE}"
grep -Fq 'authenticationEnabled=true' "${ACL_CONFIG_FILE}"
grep -Fq 'authorizationEnabled=true' "${ACL_CONFIG_FILE}"
grep -Fq 'LocalAuthenticationMetadataProvider' "${ACL_CONFIG_FILE}"
grep -Fq 'LocalAuthorizationMetadataProvider' "${ACL_CONFIG_FILE}"

for required_file in \
    "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/messaging/OperationAuditConsumer.java" \
    "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/service/AuditArchiveService.java" \
    "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/monitor/AuditIntegrityMonitor.java" \
    "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/repository/AuditQueryRepository.java" \
    "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/config/AuditInternalAccessFilter.java" \
    "${AUDIT_SERVICE_DIR}/src/main/resources/db/migration/V1__create_audit_archive.sql"; do
  [[ -s "${required_file}" ]] || { echo "AUD-03 审计管道文件缺失：${required_file}" >&2; exit 1; }
done
grep -Fq 'maxReconsumeTimes = OperationAuditResources.MAX_RECONSUME_TIMES' \
  "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/messaging/OperationAuditConsumer.java"
grep -Fq "status='PROCESSING'" \
  "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/monitor/AuditIntegrityMonitor.java"
grep -Fq "phase='REQUESTED' AND outcome='PENDING'" \
  "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/monitor/AuditIntegrityMonitor.java"
grep -Fq 'audit.consumer.dlq' \
  "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/metrics/AuditMetrics.java"
grep -Fq 'MAX_LIMIT = 100' "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/repository/AuditQueryRepository.java"
grep -Fq 'MessageDigest.isEqual' "${AUDIT_SERVICE_DIR}/src/main/java/com/leetmodel/audit/config/AuditInternalAccessFilter.java"
grep -Fq '@SaCheckRole("admin")' \
  "${ROOT_DIR}/LeetModel-backend/admin-service/src/main/java/com/leetmodel/admin/controller/AdminAuditController.java"

if rg -n -i '(passwordValue|accessToken|promptText|answerText|paperContent|messagePayload)' \
    "${BACKEND_DIR}/common/common-api/src/main/java/com/leetmodel/common/api/audit" \
    "${BACKEND_DIR}/common/common-messaging/src/main/java/com/leetmodel/common/messaging/OperationAuditMessageCodec.java" \
    "${BACKEND_DIR}/common/common-messaging/src/main/java/com/leetmodel/common/messaging/OperationAuditResources.java" \
    | grep -v 'OperationAuditContract.java'; then
  echo "操作审计生产契约出现禁止的正文/凭据字段。" >&2
  exit 1
fi

echo "操作审计静态契约验证通过。"
