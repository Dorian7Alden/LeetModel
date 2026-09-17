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

/**
 * 题目服务操作审计事件生产者。
 *
 * <p>捕获题目增删改、附件删除与赛事基础信息更新动作，原子写入操作审计 Outbox。</p>
 */
@Component
public class ProblemAuditEventProducer {
    private final MessageOutbox outbox;
    private final OperationAuditMessageCodec codec;
    @Value("${spring.application.name:problem-service}") private String sourceService;
    @Value("${APP_VERSION:dev}") private String serviceVersion;

    public ProblemAuditEventProducer(MessageOutbox outbox, OperationAuditMessageCodec codec) {
        this.outbox = outbox; this.codec = codec;
    }

    /**
     * 发布题目创建的操作审计事件。
     *
     * @param id 新增题目唯一 ID
     */
    public void problemCreated(Long id) { emit("PROBLEM.CREATE", "PROBLEM", id, Map.of("contentVersion", "CREATED")); }

    /**
     * 发布题目更新的操作审计事件。
     *
     * @param id 目标题目唯一 ID
     */
    public void problemUpdated(Long id) { emit("PROBLEM.UPDATE", "PROBLEM", id, Map.of("contentVersion", "UPDATED")); }

    /**
     * 发布题目删除的操作审计事件。
     *
     * @param id 被删除题目唯一 ID
     */
    public void problemDeleted(Long id) { emit("PROBLEM.DELETE", "PROBLEM", id, Map.of("contentVersion", "DELETED")); }

    /**
     * 发布附件删除的操作审计事件。
     *
     * @param id 被删除附件唯一 ID
     */
    public void attachmentDeleted(Long id) { emit("PROBLEM.ATTACHMENT_DELETE", "ATTACHMENT", id, Map.of("attachmentKind", "OBJECT", "attachmentVersion", "DELETED")); }

    /**
     * 发布赛事信息更新的操作审计事件。
     *
     * @param id 被修改赛事唯一 ID
     */
    public void contestUpdated(Long id) { emit("CONTEST.UPDATE", "CONTEST", id, Map.of("scheduleVersion", "UPDATED")); }

    /**
     * 构建标准操作审计载荷并投递至本地事务 Outbox。
     *
     * @param code       审计操作标识码
     * @param targetType 操作目标实体类型
     * @param targetId   操作目标实体 ID
     * @param after      变更后属性快照 Map
     */
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
