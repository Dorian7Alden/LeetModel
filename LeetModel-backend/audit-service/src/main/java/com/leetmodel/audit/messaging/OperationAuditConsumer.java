package com.leetmodel.audit.messaging;

import com.leetmodel.audit.metrics.AuditMetrics;
import com.leetmodel.audit.service.AuditArchiveService;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.messaging.MessageCorrelationContext;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.OperationAuditMessageCodec;
import com.leetmodel.common.messaging.OperationAuditResources;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 专用操作审计 Topic 消费者；异常向 Broker 抛出以触发固定重试/DLQ。 */
@Component
@ConditionalOnProperty(prefix = "leetmodel.audit", name = "consumer-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%leetmodel-operation-audit-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-audit-archive-v1",
        selectorExpression = OperationAuditResources.TAG,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 1,
        consumeThreadMax = 1,
        maxReconsumeTimes = OperationAuditResources.MAX_RECONSUME_TIMES
)
public class OperationAuditConsumer implements RocketMQListener<byte[]> {
    private final OperationAuditMessageCodec codec;
    private final AuditArchiveService archiveService;
    private final AuditMetrics metrics;

    public OperationAuditConsumer(OperationAuditMessageCodec codec,
                                  AuditArchiveService archiveService,
                                  AuditMetrics metrics) {
        this.codec = codec;
        this.archiveService = archiveService;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(byte[] body) {
        long started = System.nanoTime();
        MessageEnvelopeV1<OperationAuditPayloadV1> envelope;
        try {
            envelope = codec.decode(body);
        } catch (RuntimeException exception) {
            metrics.rejected();
            throw new MessageContractException("审计消息契约拒绝", exception);
        }
        try (CorrelationContext.Scope ignored = MessageCorrelationContext.open(envelope)) {
            archiveService.archive(envelope);
            metrics.processing(System.nanoTime() - started);
        } catch (RuntimeException exception) {
            metrics.failed();
            throw exception;
        }
    }
}
