#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORTER="${ROOT_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/SkyWalkingLogReporterAppender.java"
METRICS="${ROOT_DIR}/common/common-core/src/main/java/com/leetmodel/common/core/logging/SkyWalkingLogReporterMetrics.java"
PRODUCER="${ROOT_DIR}/common/common-messaging/src/main/java/com/leetmodel/common/messaging/internal/OperationAuditGovernanceProducer.java"
MESSAGING="${ROOT_DIR}/common/common-messaging/src/main/java/com/leetmodel/common/messaging/internal/MessagingOperationsService.java"
LOGBACK="${ROOT_DIR}/common/common-core/src/main/resources/leetmodel-logback-spring.xml"

for marker in 'LinkedBlockingDeque' 'queue.offerLast' 'droppedQueueLow' 'droppedQueueHigh' 'requestTimeoutMillis'; do
  grep -Fq "${marker}" "${REPORTER}" || { echo "日志 Reporter 缺少有界/降级契约: ${marker}" >&2; exit 1; }
done
for marker in 'QUEUE_DEPTH_METRIC' 'DROPPED_QUEUE_HIGH' 'CONNECTED_METRIC'; do
  grep -Fq "${marker}" "${METRICS}" || { echo "日志 Reporter 指标缺少: ${marker}" >&2; exit 1; }
done
grep -Fq '<appender name="LOCAL_ROLLING"' "${LOGBACK}"
grep -Fq 'public void assertReady(String operationCode)' "${PRODUCER}"
grep -Fq 'OutboxStatus.BLOCKED' "${PRODUCER}"
for marker in 'audit.assertReady("OUTBOX.REPLAY")' 'audit.assertReady("CONSUMER.PAUSE")' 'audit.assertReady("CONSUMER.RESUME")'; do
  grep -Fq "${marker}" "${MESSAGING}" || { echo "消息治理缺少 fail-closed 门禁: ${marker}" >&2; exit 1; }
done
echo "遥测与审计故障保护静态契约验证通过。"
