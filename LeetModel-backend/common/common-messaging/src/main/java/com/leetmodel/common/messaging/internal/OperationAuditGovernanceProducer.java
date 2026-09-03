package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.audit.OperationAuditCatalog;
import com.leetmodel.common.api.audit.OperationAuditContract;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.OperationAuditMessageCodec;
import com.leetmodel.common.messaging.OperationAuditResources;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 可靠消息运维命令的语义审计生产者。 */
public final class OperationAuditGovernanceProducer {
    private final String sourceService;
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;
    private final JdbcMessageOutbox jdbcOutbox;

    public OperationAuditGovernanceProducer(String sourceService, MessageOutbox outbox,
                                            OperationAuditMessageCodec codec) {
        this(sourceService, outbox, codec, null);
    }

    public OperationAuditGovernanceProducer(String sourceService, MessageOutbox outbox,
                                            OperationAuditMessageCodec codec,
                                            JdbcMessageOutbox jdbcOutbox) {
        this.sourceService = sourceService;
        this.outbox = outbox;
        this.codec = codec;
        this.jdbcOutbox = jdbcOutbox;
    }

    /** 高风险命令的本地审计 Outbox fail-closed 门禁。 */
    public void assertReady(String operationCode) {
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(operationCode);
        if (!"HIGH".equals(spec.riskLevel()) || jdbcOutbox == null) return;
        if (jdbcOutbox.count(OutboxStatus.BLOCKED) > 0) {
            throw new IllegalStateException("高风险操作审计 Outbox 已阻塞，拒绝继续执行: " + operationCode);
        }
    }

    public void emit(String operationCode, String targetType, String targetId,
                     Map<String, String> after) {
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(operationCode);
        if (!spec.sourceServices().contains(sourceService)) return;
        String operationId = CorrelationContext.ensureOperationId();
        if (spec.externalSideEffect()) {
            emitPhase(spec, operationId, targetType, targetId, "PENDING", "PENDING", after);
        }
        emitPhase(spec, operationId, targetType, targetId, "COMPLETED", "SUCCEEDED", after);
    }

    private void emitPhase(OperationAuditCatalog.Spec spec, String operationId,
                           String targetType, String targetId, String phase,
                           String outcome, Map<String, String> after) {
        String trace = TraceIdUtil.getTraceId();
        if (trace == null || trace.isBlank()) trace = CorrelationContext.newId();
        String eventId = UUID.randomUUID().toString();
        OperationAuditPayloadV1 payload = new OperationAuditPayloadV1(
                1, eventId, operationId, phase, Instant.now(),
                sourceService, "dev", spec.category(), spec.operationCode(), spec.riskLevel(), outcome,
                "ADMIN_REQUEST", null, "ADMIN", "admin-command", List.of(), targetType, targetId,
                null, Map.of(), after, trace, null, null, null, null, null, null);
        OperationAuditContract.validate(payload);
        outbox.enqueue(OperationAuditResources.TOPIC, OperationAuditResources.TAG, codec.envelope(payload));
    }
}
