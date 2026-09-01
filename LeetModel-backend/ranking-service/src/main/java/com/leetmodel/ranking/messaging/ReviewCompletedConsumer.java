package com.leetmodel.ranking.messaging;

import com.leetmodel.common.api.dto.ReviewCompletedPayload;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageCorrelationContext;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.ranking.service.RankingRebuildRequestService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 把评审完成事件合并为单题排行重建请求。 */
@Component
@ConditionalOnProperty(prefix = "ranking.messaging", name = "consumer-enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%review-event-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-ranking-review-v1",
        selectorExpression = ReviewCompletedConsumer.EVENT_TYPE,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 1,
        consumeThreadMax = 1,
        maxReconsumeTimes = 5
)
public class ReviewCompletedConsumer implements RocketMQListener<byte[]> {
    public static final String EVENT_TYPE = "REVIEW_COMPLETED";
    public static final String CONSUMER_GROUP = "cg-ranking-review-v1";

    private final MessageCodec codec;
    private final MessageInbox inbox;
    private final RankingRebuildRequestService requestService;

    public ReviewCompletedConsumer(
            MessageCodec codec,
            MessageInbox inbox,
            RankingRebuildRequestService requestService
    ) {
        this.codec = codec;
        this.inbox = inbox;
        this.requestService = requestService;
    }

    @Override
    public void onMessage(byte[] body) {
        MessageEnvelopeV1<ReviewCompletedPayload> envelope = codec.decode(
                body, ReviewCompletedPayload.class);
        ReviewCompletedPayload payload = envelope.payload();
        if (!EVENT_TYPE.equals(envelope.eventType())
                || !"ai-review-service".equals(envelope.sourceService())
                || payload.reviewTaskId() == null || payload.reviewTaskId() <= 0
                || payload.submissionId() == null || payload.submissionId() <= 0
                || payload.teamId() == null || payload.teamId() <= 0
                || payload.problemId() == null || payload.problemId() <= 0
                || payload.workflowVersion() == null || payload.workflowVersion().isBlank()
                || payload.finishedAt() == null) {
            throw new MessageContractException("REVIEW_COMPLETED 消息字段不合法");
        }
        try (CorrelationContext.Scope ignored = MessageCorrelationContext.open(envelope)) {
            inbox.executeOnce(CONSUMER_GROUP, envelope,
                    () -> requestService.request(payload.problemId(), envelope.traceId()));
        }
    }
}
