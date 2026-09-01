package com.leetmodel.evaluation.messaging;

import com.leetmodel.common.api.dto.EvaluationSlotReadyPayload;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.service.EvaluationWorkerCoordinator;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "evaluation.messaging", name = "consumer-enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%evaluation-task-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-ai-evaluation-task-v1",
        selectorExpression = EvaluationSlotMessageContract.EVENT_TYPE,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 1,
        consumeThreadMax = 1,
        maxReconsumeTimes = 5
)
public class EvaluationSlotReadyConsumer implements RocketMQListener<byte[]> {
    private final MessageCodec codec;
    private final MessageInbox inbox;
    private final EvaluationRunAttemptMapper runMapper;
    private final EvaluationWorkerCoordinator coordinator;

    public EvaluationSlotReadyConsumer(MessageCodec codec, MessageInbox inbox,
                                       EvaluationRunAttemptMapper runMapper,
                                       EvaluationWorkerCoordinator coordinator) {
        this.codec = codec;
        this.inbox = inbox;
        this.runMapper = runMapper;
        this.coordinator = coordinator;
    }

    @Override
    public void onMessage(byte[] body) {
        MessageEnvelopeV1<EvaluationSlotReadyPayload> envelope = codec.decode(
                body, EvaluationSlotReadyPayload.class);
        validate(envelope);
        TraceIdUtil.setTraceId(envelope.traceId());
        try {
            EvaluationSlotReadyPayload payload = envelope.payload();
            inbox.executeOnce(EvaluationSlotMessageContract.CONSUMER_GROUP, envelope,
                    () -> runMapper.markWakeup(payload.runAttemptId(), payload.evaluationTaskId(),
                            payload.attemptNo(), LocalDateTime.now()));
            // Inbox 已提交后进程退出时，Broker 重投仍可再次恢复本地信号。
            coordinator.wakeup(payload.runAttemptId());
        } finally {
            TraceIdUtil.removeTraceId();
        }
    }

    private void validate(MessageEnvelopeV1<EvaluationSlotReadyPayload> envelope) {
        EvaluationSlotReadyPayload payload = envelope.payload();
        if (!EvaluationSlotMessageContract.EVENT_TYPE.equals(envelope.eventType())
                || envelope.schemaVersion() != 1
                || !"ai-evaluation-service".equals(envelope.sourceService())
                || !"evaluation-slot".equals(envelope.aggregateType())
                || payload.evaluationTaskId() == null || payload.evaluationTaskId() <= 0
                || payload.runAttemptId() == null || payload.runAttemptId() <= 0
                || !payload.runAttemptId().toString().equals(envelope.aggregateId())
                || payload.slotKey() == null || payload.slotKey().isBlank()
                || payload.attemptNo() == null || payload.attemptNo() <= 0
                || payload.featureCode() == null || payload.featureCode().isBlank()
                || payload.datasetVersion() == null || payload.datasetVersion().isBlank()) {
            throw new MessageContractException("EVALUATION_SLOT_READY 消息字段不合法");
        }
    }
}
