package com.leetmodel.ranking.messaging;

import com.leetmodel.common.api.dto.FinalSubmissionChangedPayload;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageCodec;
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

/** 把最终提交变化事件合并为单题排行重建请求。 */
@Component
@ConditionalOnProperty(prefix = "ranking.messaging", name = "consumer-enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%submission-event-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-ranking-submission-v1",
        selectorExpression = FinalSubmissionChangedConsumer.EVENT_TYPE,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 1,
        consumeThreadMax = 1,
        maxReconsumeTimes = 5
)
public class FinalSubmissionChangedConsumer implements RocketMQListener<byte[]> {
    public static final String EVENT_TYPE = "FINAL_SUBMISSION_CHANGED";
    public static final String CONSUMER_GROUP = "cg-ranking-submission-v1";

    private final MessageCodec codec;
    private final MessageInbox inbox;
    private final RankingRebuildRequestService requestService;

    public FinalSubmissionChangedConsumer(
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
        MessageEnvelopeV1<FinalSubmissionChangedPayload> envelope = codec.decode(
                body, FinalSubmissionChangedPayload.class);
        FinalSubmissionChangedPayload payload = envelope.payload();
        if (!EVENT_TYPE.equals(envelope.eventType())
                || !"submission-service".equals(envelope.sourceService())
                || payload.teamId() == null || payload.teamId() <= 0
                || payload.problemId() == null || payload.problemId() <= 0
                || payload.submissionId() == null || payload.submissionId() <= 0
                || payload.lockedAt() == null) {
            throw new MessageContractException("FINAL_SUBMISSION_CHANGED 消息字段不合法");
        }
        TraceIdUtil.setTraceId(envelope.traceId());
        try {
            inbox.executeOnce(CONSUMER_GROUP, envelope,
                    () -> requestService.request(payload.problemId(), envelope.traceId()));
        } finally {
            TraceIdUtil.removeTraceId();
        }
    }
}
