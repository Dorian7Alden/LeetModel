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

/** submission-service 最终提交审计生产者，不复制 PDF 或提交正文。 */
@Component
public class SubmissionAuditEventProducer {
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;
    @Value("${spring.application.name:submission-service}") private String sourceService;
    @Value("${APP_VERSION:dev}") private String serviceVersion;

    public SubmissionAuditEventProducer(MessageOutbox outbox, OperationAuditMessageCodec codec) {
        this.outbox = outbox; this.codec = codec;
    }

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
