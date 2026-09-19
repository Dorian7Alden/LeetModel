package com.leetmodel.submission.messaging;

import com.leetmodel.common.api.dto.FileBindingChangedPayload;
import com.leetmodel.common.api.messaging.FileBindingMessageContract;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 正式论文引用事件生产者。
 *
 * <p>提交版本与论文文件的归属关系是提交领域事实；本组件在提交服务本地事务内写入绑定 Outbox，
 * 由 file-service 幂等消费维护引用投影与物理清理判断。</p>
 */
@Component
public class SubmissionPaperEventProducer {

    /** 引用事实所有者服务名。 */
    public static final String OWNER_SERVICE = "submission-service";
    /** file-service 业务用途编码。 */
    public static final String PURPOSE_CODE = "SUBMISSION_PAPER";
    /** 业务资源类型。 */
    public static final String RESOURCE_TYPE = "SUBMISSION_PAPER";

    private static final String AGGREGATE_TYPE = "submission";
    private static final long BOUND_EVENT_VERSION = 1L;

    private final MessageOutbox outbox;
    private final MessageEnvelopeFactory envelopeFactory;

    public SubmissionPaperEventProducer(MessageOutbox outbox, MessageEnvelopeFactory envelopeFactory) {
        this.outbox = outbox;
        this.envelopeFactory = envelopeFactory;
    }

    /**
     * 发布正式论文绑定事件。
     *
     * @param submissionId 提交版本标识
     * @param fileId 文件资产标识
     */
    public void bound(Long submissionId, Long fileId) {
        if (submissionId == null || fileId == null) {
            return;
        }
        FileBindingChangedPayload payload = new FileBindingChangedPayload(
                fileId, OWNER_SERVICE, RESOURCE_TYPE, submissionId.toString(), BOUND_EVENT_VERSION);
        try {
            outbox.enqueue(
                    FileBindingMessageContract.TOPIC,
                    FileBindingMessageContract.BOUND_EVENT_TYPE,
                    envelopeFactory.create(
                            FileBindingMessageContract.BOUND_EVENT_TYPE,
                            AGGREGATE_TYPE,
                            submissionId.toString(),
                            FileBindingMessageContract.idempotencyKey(
                                    FileBindingMessageContract.BOUND_EVENT_TYPE,
                                    fileId, submissionId.toString(), BOUND_EVENT_VERSION),
                            currentTraceId(),
                            payload));
        } catch (DuplicateKeyException ignored) {
            // 同一提交版本与论文文件只对应一个引用事实；重复写入用于补偿历史缺失 Outbox。
        }
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }
}
