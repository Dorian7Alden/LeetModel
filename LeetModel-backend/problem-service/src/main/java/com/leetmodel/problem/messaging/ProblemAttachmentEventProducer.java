package com.leetmodel.problem.messaging;

import com.leetmodel.common.api.dto.FileBindingChangedPayload;
import com.leetmodel.common.api.messaging.FileBindingMessageContract;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 题目附件引用事件生产者。
 *
 * <p>附件关系是题目领域事实；本组件在题目服务本地事务内写入绑定或解绑 Outbox，
 * 由 file-service 幂等消费维护引用投影与物理清理判断。</p>
 */
@Component
public class ProblemAttachmentEventProducer {

    /** 引用事实所有者服务名。 */
    public static final String OWNER_SERVICE = "problem-service";
    /** file-service 业务用途编码。 */
    public static final String PURPOSE_CODE = "PROBLEM_ATTACHMENT";
    /** 业务资源类型。 */
    public static final String RESOURCE_TYPE = "PROBLEM_ATTACHMENT";

    private static final String AGGREGATE_TYPE = "problem-attachment";
    private static final long BOUND_EVENT_VERSION = 1L;
    private static final long UNBOUND_EVENT_VERSION = 2L;

    private final MessageOutbox outbox;
    private final MessageEnvelopeFactory envelopeFactory;

    public ProblemAttachmentEventProducer(MessageOutbox outbox, MessageEnvelopeFactory envelopeFactory) {
        this.outbox = outbox;
        this.envelopeFactory = envelopeFactory;
    }

    /**
     * 发布附件绑定事件。
     *
     * @param attachmentId 附件记录标识
     * @param fileId 文件资产标识
     */
    public void bound(Long attachmentId, Long fileId) {
        emit(FileBindingMessageContract.BOUND_EVENT_TYPE, attachmentId, fileId, BOUND_EVENT_VERSION);
    }

    /**
     * 发布附件解绑事件。
     *
     * @param attachmentId 附件记录标识
     * @param fileId 文件资产标识
     */
    public void unbound(Long attachmentId, Long fileId) {
        emit(FileBindingMessageContract.UNBOUND_EVENT_TYPE, attachmentId, fileId, UNBOUND_EVENT_VERSION);
    }

    private void emit(String eventType, Long attachmentId, Long fileId, long eventVersion) {
        if (attachmentId == null || fileId == null) {
            return;
        }
        FileBindingChangedPayload payload = new FileBindingChangedPayload(
                fileId, OWNER_SERVICE, RESOURCE_TYPE, attachmentId.toString(), eventVersion);
        try {
            outbox.enqueue(
                    FileBindingMessageContract.TOPIC,
                    eventType,
                    envelopeFactory.create(
                            eventType,
                            AGGREGATE_TYPE,
                            attachmentId.toString(),
                            FileBindingMessageContract.idempotencyKey(
                                    eventType, fileId, attachmentId.toString(), eventVersion),
                            currentTraceId(),
                            payload));
        } catch (DuplicateKeyException ignored) {
            // 同一附件与事件版本只对应一个引用事实；重复写入用于补偿历史缺失 Outbox。
        }
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }
}
