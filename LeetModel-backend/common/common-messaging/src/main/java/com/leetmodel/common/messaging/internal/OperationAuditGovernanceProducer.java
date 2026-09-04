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

/**
 * 可靠消息运维治理动作的语义操作审计事件生产者。
 *
 * <p>为消费者挂起/恢复、消息人工补发等高风险治理操作提供 fail-closed 前置门禁与标准审计事件外发。</p>
 */
public final class OperationAuditGovernanceProducer {
    private final String sourceService;
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;
    private final JdbcMessageOutbox jdbcOutbox;

    /**
     * 构造操作审计生产者（无本地 Outbox 门禁检查）。
     *
     * @param sourceService 源微服务名称
     * @param outbox        可靠消息 Outbox 写入端口
     * @param codec         操作审计专用的消息编解码器
     */
    public OperationAuditGovernanceProducer(
            String sourceService,
            MessageOutbox outbox,
            OperationAuditMessageCodec codec
    ) {
        this(sourceService, outbox, codec, null);
    }

    /**
     * 构造操作审计生产者（带本地 Outbox 阻塞门禁检查）。
     *
     * @param sourceService 源微服务名称
     * @param outbox        可靠消息 Outbox 写入端口
     * @param codec         操作审计专用的消息编解码器
     * @param jdbcOutbox    JDBC Outbox 实例，用于门禁检查
     */
    public OperationAuditGovernanceProducer(
            String sourceService,
            MessageOutbox outbox,
            OperationAuditMessageCodec codec,
            JdbcMessageOutbox jdbcOutbox
    ) {
        this.sourceService = sourceService;
        this.outbox = outbox;
        this.codec = codec;
        this.jdbcOutbox = jdbcOutbox;
    }

    /**
     * 对高风险治理命令执行本地审计 Outbox 的 fail-closed 门禁断言。
     *
     * @param operationCode 治理操作标识码
     * @throws IllegalStateException 若当前 Outbox 存在阻塞记录（BLOCKED），拒绝执行高风险命令
     */
    public void assertReady(String operationCode) {
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(operationCode);
        if (!"HIGH".equals(spec.riskLevel()) || jdbcOutbox == null) return;
        if (jdbcOutbox.count(OutboxStatus.BLOCKED) > 0) {
            throw new IllegalStateException("高风险操作审计 Outbox 已阻塞，拒绝继续执行: " + operationCode);
        }
    }

    /**
     * 发送操作审计事件信封至 Outbox。
     *
     * @param operationCode 治理操作标识码
     * @param targetType    操作目标实体类型
     * @param targetId      操作目标实体唯一标识
     * @param after         变更后的审计属性快照
     */
    public void emit(
            String operationCode,
            String targetType,
            String targetId,
            Map<String, String> after
    ) {
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(operationCode);
        if (!spec.sourceServices().contains(sourceService)) return;
        String operationId = CorrelationContext.ensureOperationId();
        if (spec.externalSideEffect()) {
            emitPhase(spec, operationId, targetType, targetId, "PENDING", "PENDING", after);
        }
        emitPhase(spec, operationId, targetType, targetId, "COMPLETED", "SUCCEEDED", after);
    }

    /**
     * 构建并持久化特定阶段（PENDING 或 COMPLETED）的操作审计事件信封。
     *
     * @param spec        操作规范元数据
     * @param operationId 全局治理操作唯一 ID
     * @param targetType  操作目标类型
     * @param targetId    操作目标标识
     * @param phase       操作阶段（PENDING / COMPLETED）
     * @param outcome     操作最终结果
     * @param after       变更后的属性快照
     */
    private void emitPhase(
            OperationAuditCatalog.Spec spec,
            String operationId,
            String targetType,
            String targetId,
            String phase,
            String outcome,
            Map<String, String> after
    ) {
        String trace = TraceIdUtil.getTraceId();
        if (trace == null || trace.isBlank()) trace = CorrelationContext.newId();
        String eventId = UUID.randomUUID().toString();
        OperationAuditPayloadV1 payload = new OperationAuditPayloadV1(
                1,
                eventId,
                operationId,
                phase,
                Instant.now(),
                sourceService,
                "dev",
                spec.category(),
                spec.operationCode(),
                spec.riskLevel(),
                outcome,
                "ADMIN_REQUEST",
                null,
                "ADMIN",
                "admin-command",
                List.of(),
                targetType,
                targetId,
                null,
                Map.of(),
                after,
                trace,
                null,
                null,
                null,
                null,
                null,
                null
        );
        OperationAuditContract.validate(payload);
        outbox.enqueue(OperationAuditResources.TOPIC, OperationAuditResources.TAG, codec.envelope(payload));
    }
}
