package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.InboxResult;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;

/**
 * 为事务 Inbox 增加低基数结果指标。
 */
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
        try {
            InboxResult result = delegate.executeOnce(logicalConsumerGroup, envelope, domainAction);
            metrics.consumed(logicalConsumerGroup,
                    result == InboxResult.CONSUMED ? "consumed" : "duplicate",
                    System.nanoTime() - started);
            return result;
        } catch (RuntimeException exception) {
            metrics.consumed(logicalConsumerGroup, "failure", System.nanoTime() - started);
            throw exception;
        }
    }
}
