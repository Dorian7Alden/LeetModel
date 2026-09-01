package com.leetmodel.suggestion.messaging;

import com.leetmodel.common.api.dto.SuggestionTaskReadyPayload;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.service.SuggestionTaskWorkerCoordinator;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "suggestion.messaging", name = "consumer-enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%suggestion-task-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-ai-suggestion-task-v1",
        selectorExpression = SuggestionTaskMessageContract.EVENT_TYPE,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 1,
        consumeThreadMax = 1,
        maxReconsumeTimes = 5
)
public class SuggestionTaskReadyConsumer implements RocketMQListener<byte[]> {
    private final MessageCodec codec;
    private final MessageInbox inbox;
    private final SuggestionTaskMapper taskMapper;
    private final SuggestionTaskWorkerCoordinator coordinator;

    public SuggestionTaskReadyConsumer(MessageCodec codec, MessageInbox inbox,
                                       SuggestionTaskMapper taskMapper,
                                       SuggestionTaskWorkerCoordinator coordinator) {
        this.codec = codec;
        this.inbox = inbox;
        this.taskMapper = taskMapper;
        this.coordinator = coordinator;
    }

    @Override
    public void onMessage(byte[] body) {
        MessageEnvelopeV1<SuggestionTaskReadyPayload> envelope = codec.decode(
                body, SuggestionTaskReadyPayload.class);
        validate(envelope);
        TraceIdUtil.setTraceId(envelope.traceId());
        try {
            SuggestionTaskReadyPayload payload = envelope.payload();
            inbox.executeOnce(SuggestionTaskMessageContract.CONSUMER_GROUP, envelope,
                    () -> taskMapper.markWakeup(payload.taskId(), payload.submissionId(),
                            payload.workflowVersion(), LocalDateTime.now()));
            // 重复投递也再次发出本地信号，覆盖 Inbox 已提交后进程退出的窗口。
            coordinator.wakeup(payload.taskId());
        } finally {
            TraceIdUtil.removeTraceId();
        }
    }

    private void validate(MessageEnvelopeV1<SuggestionTaskReadyPayload> envelope) {
        SuggestionTaskReadyPayload payload = envelope.payload();
        if (!SuggestionTaskMessageContract.EVENT_TYPE.equals(envelope.eventType())
                || !"ai-suggestion-service".equals(envelope.sourceService())
                || payload.taskId() == null || payload.taskId() <= 0
                || payload.submissionId() == null || payload.submissionId() <= 0
                || payload.workflowVersion() == null || payload.workflowVersion().isBlank()) {
            throw new MessageContractException("SUGGESTION_TASK_READY 消息字段不合法");
        }
    }
}
