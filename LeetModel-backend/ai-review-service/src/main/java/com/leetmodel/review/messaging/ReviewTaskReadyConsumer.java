package com.leetmodel.review.messaging;

import com.leetmodel.common.api.dto.ReviewTaskReadyPayload;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageCorrelationContext;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.review.service.ReviewService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 把评审 ready 消息短事务落为本地领域任务。
 */
@Component
@ConditionalOnProperty(prefix = "review.messaging", name = "consumer-enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%review-task-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-ai-review-task-v1",
        selectorExpression = ReviewTaskReadyConsumer.EVENT_TYPE,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 2,
        consumeThreadMax = 2,
        maxReconsumeTimes = 5
)
public class ReviewTaskReadyConsumer implements RocketMQListener<byte[]> {

    /** 稳定事件类型。 */
    public static final String EVENT_TYPE = "REVIEW_TASK_READY";
    /** 公共 Inbox 使用的逻辑消费组。 */
    public static final String CONSUMER_GROUP = "cg-ai-review-task-v1";

    private final MessageCodec codec;
    private final MessageInbox inbox;
    private final ReviewService reviewService;

    /**
     * 创建评审任务消息消费者。
     *
     * @param codec 消息编解码器
     * @param inbox 消费 Inbox
     * @param reviewService 评审领域服务
     */
    public ReviewTaskReadyConsumer(
            MessageCodec codec,
            MessageInbox inbox,
            ReviewService reviewService
    ) {
        this.codec = codec;
        this.inbox = inbox;
        this.reviewService = reviewService;
    }

    @Override
    public void onMessage(byte[] body) {
        MessageEnvelopeV1<ReviewTaskReadyPayload> envelope = codec.decode(
                body, ReviewTaskReadyPayload.class);
        validate(envelope);
        try (CorrelationContext.Scope ignored = MessageCorrelationContext.open(envelope)) {
            inbox.executeOnce(CONSUMER_GROUP, envelope, () -> createTask(envelope));
        }
    }

    private void createTask(MessageEnvelopeV1<ReviewTaskReadyPayload> envelope) {
        ReviewTaskReadyPayload payload = envelope.payload();
        reviewService.createTask(payload.submissionId(), payload.teamId(), payload.problemId(),
                payload.workflowVersion(), envelope.traceId());
    }

    private void validate(MessageEnvelopeV1<ReviewTaskReadyPayload> envelope) {
        ReviewTaskReadyPayload payload = envelope.payload();
        if (!EVENT_TYPE.equals(envelope.eventType())
                || !"submission-service".equals(envelope.sourceService())
                || payload.submissionId() == null || payload.submissionId() <= 0
                || payload.teamId() == null || payload.teamId() <= 0
                || payload.problemId() == null || payload.problemId() <= 0
                || payload.workflowVersion() == null || payload.workflowVersion().isBlank()) {
            throw new MessageContractException("REVIEW_TASK_READY 消息字段不合法");
        }
    }
}
