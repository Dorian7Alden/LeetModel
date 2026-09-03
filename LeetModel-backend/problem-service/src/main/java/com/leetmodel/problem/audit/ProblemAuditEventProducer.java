package com.leetmodel.problem.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.common.api.audit.OperationAuditCatalog;
import com.leetmodel.common.api.audit.OperationAuditContract;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
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

/** problem-service 领域审计生产者；只保存目录允许的摘要字段。 */
@Component
public class ProblemAuditEventProducer {
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;
    @Value("${spring.application.name:problem-service}") private String sourceService;
    @Value("${APP_VERSION:dev}") private String serviceVersion;

    public ProblemAuditEventProducer(MessageOutbox outbox, OperationAuditMessageCodec codec) {
        this.outbox = outbox; this.codec = codec;
    }

    public void problemCreated(Long id) { emit("PROBLEM.CREATE", "PROBLEM", id, Map.of("contentVersion", "CREATED")); }
    public void problemUpdated(Long id) { emit("PROBLEM.UPDATE", "PROBLEM", id, Map.of("contentVersion", "UPDATED")); }
    public void problemDeleted(Long id) { emit("PROBLEM.DELETE", "PROBLEM", id, Map.of("contentVersion", "DELETED")); }
    public void attachmentDeleted(Long id) { emit("PROBLEM.ATTACHMENT_DELETE", "ATTACHMENT", id, Map.of("attachmentKind", "OBJECT", "attachmentVersion", "DELETED")); }
    public void contestUpdated(Long id) { emit("CONTEST.UPDATE", "CONTEST", id, Map.of("scheduleVersion", "UPDATED")); }

    private void emit(String code, String targetType, Long targetId, Map<String, String> after) {
        String eventId = UUID.randomUUID().toString();
        String trace = TraceIdUtil.getTraceId();
        if (trace == null || trace.isBlank()) trace = CorrelationContext.newId();
        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(code);
        String actor = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "admin-unknown";
        OperationAuditPayloadV1 payload = new OperationAuditPayloadV1(
                1, eventId, CorrelationContext.ensureOperationId(), "COMPLETED", Instant.now(),
                sourceService, serviceVersion, spec.category(), code, spec.riskLevel(), "SUCCEEDED",
                "ADMIN_REQUEST", null, "ADMIN", actor, List.of(), targetType, String.valueOf(targetId),
                null, Map.of(), after, trace, null, null, null, null, null, null);
        OperationAuditContract.validate(payload);
        outbox.enqueue(OperationAuditResources.TOPIC, OperationAuditResources.TAG, codec.envelope(payload));
    }
}
