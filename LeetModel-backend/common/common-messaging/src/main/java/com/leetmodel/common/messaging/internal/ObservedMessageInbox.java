package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.leetmodel.common.core.telemetry.ExecutionSpanOperation;
import com.leetmodel.common.core.telemetry.SkyWalkingExecutionSpan;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 为事务 Inbox 增加低基数结果指标。
 */
@Slf4j
public final class ObservedMessageInbox implements MessageInbox {

    private final JdbcMessageInbox delegate;
    private final MessagingMetrics metrics;

    /**
     * 创建可观测 Inbox。
     *
     * @param delegate 事务 Inbox
     * @param metrics 消息指标
     */
    public ObservedMessageInbox(JdbcMessageInbox delegate, MessagingMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public InboxResult executeOnce(
            String logicalConsumerGroup,
            MessageEnvelopeV1<?> envelope,
            Runnable domainAction
    ) {
        long started = System.nanoTime();
        try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                ExecutionSpanOperation.INBOX_CONSUME)) {
            try {
                InboxResult result = delegate.executeOnce(logicalConsumerGroup, envelope, domainAction);
                metrics.consumed(logicalConsumerGroup,
                        result == InboxResult.CONSUMED ? "consumed" : "duplicate",
                        System.nanoTime() - started);
                boolean consumed = result == InboxResult.CONSUMED;
                log.atInfo()
                        .addKeyValue(LogFieldNames.EVENT_CODE, consumed
                                ? LogEventCodes.INBOX_MESSAGE_CONSUMED
                                : LogEventCodes.INBOX_MESSAGE_DUPLICATE)
                        .addKeyValue(LogFieldNames.EVENT_ID, envelope.eventId())
                        .addKeyValue(LogFieldNames.CONSUMER_GROUP, logicalConsumerGroup)
                        .addKeyValue(LogFieldNames.BUSINESS_TYPE, envelope.aggregateType())
                        .addKeyValue(LogFieldNames.DURATION_MS,
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                        .addKeyValue(LogFieldNames.OUTCOME, consumed ? "consumed" : "duplicate")
                        .log(consumed ? "Inbox message consumed" : "Inbox duplicate suppressed");
                span.outcome(consumed ? "consumed" : "duplicate");
                return result;
            } catch (RuntimeException exception) {
                span.outcome("failed").error("transaction");
                metrics.consumed(logicalConsumerGroup, "failure", System.nanoTime() - started);
                log.atWarn()
                        .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.INBOX_MESSAGE_FAILED)
                        .addKeyValue(LogFieldNames.EVENT_ID, envelope.eventId())
                        .addKeyValue(LogFieldNames.CONSUMER_GROUP, logicalConsumerGroup)
                        .addKeyValue(LogFieldNames.BUSINESS_TYPE, envelope.aggregateType())
                        .addKeyValue(LogFieldNames.DURATION_MS,
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                        .addKeyValue(LogFieldNames.OUTCOME, "failed")
                        .setCause(exception)
                        .log("Inbox message handling failed");
                throw exception;
            }
        }
    }
}
