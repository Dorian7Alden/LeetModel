package com.leetmodel.submission.audit;

import com.leetmodel.common.api.audit.OperationAuditCatalog;
import com.leetmodel.common.api.audit.OperationAuditContract;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.common.messaging.OperationAuditMessageCodec;
import com.leetmodel.common.messaging.OperationAuditResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 论文提交服务最终作品锁定审计生产者。
 *
 * <p>在队伍锁定最终提交版本时，将版本号与元数据原子写入操作审计 Outbox，不包含二进制 PDF 内容。</p>
 */
@Component
public class SubmissionAuditEventProducer {
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;
    @Value("${spring.application.name:submission-service}") private String sourceService;
    @Value("${APP_VERSION:dev}") private String serviceVersion;

    /**
     * 构造作品提交审计生产者。
     *
     * @param outbox 本地 Outbox 写入端口
     * @param codec  操作审计消息编解码器
     */
    public SubmissionAuditEventProducer(MessageOutbox outbox, OperationAuditMessageCodec codec) {
        this.outbox = outbox; this.codec = codec;
    }

    /**
     * 发布队伍最终提交锁定的操作审计事件。
     *
     * @param team         队伍实体 DTO
     * @param submissionId 锁定的提交记录 ID
     * @param version      锁定的作品版本号
     */
    public void finalized(TeamDTO team, Long submissionId, Integer version) {
        String eventId = UUID.nameUUIDFromBytes(("submission-finalize:" + submissionId).getBytes()).toString();
        String trace = TraceIdUtil.getTraceId();
        if (trace == null || trace.isBlank()) trace = CorrelationContext.newId();
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require("SUBMISSION.FINALIZE");
        String actorId = team == null || team.getLeaderId() == null ? "unknown-user" : team.getLeaderId().toString();
        OperationAuditPayloadV1 payload = new OperationAuditPayloadV1(
                1, eventId, CorrelationContext.ensureOperationId(), "COMPLETED", Instant.now(), sourceService,
                serviceVersion, spec.category(), spec.operationCode(), spec.riskLevel(), "SUCCEEDED",
                "FINALIZE_SUBMISSION", null, "USER", actorId, List.of(), "SUBMISSION",
                String.valueOf(submissionId), version == null ? null : version.toString(), Map.of(),
                Map.of("submissionVersion", version == null ? "UNKNOWN" : version.toString(), "finalized", "true"),
                trace, null, null, null, null, null, null);
        OperationAuditContract.validate(payload);
        outbox.enqueue(OperationAuditResources.TOPIC, OperationAuditResources.TAG, codec.envelope(payload));
    }
}
