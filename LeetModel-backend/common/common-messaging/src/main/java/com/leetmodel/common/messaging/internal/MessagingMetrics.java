package com.leetmodel.common.messaging.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 可靠消息低基数指标。
 */
public final class MessagingMetrics {

    private final Counter publishSuccess;
    private final Counter publishRetry;
    private final Counter publishBlocked;
    private final Counter inboxConsumed;
    private final Counter inboxDuplicate;

    /**
     * 创建指标并绑定 Outbox 状态仪表。
     *
     * @param registry Micrometer 注册表，可为空
     * @param outbox Outbox 仓库
     */
    public MessagingMetrics(MeterRegistry registry, JdbcMessageOutbox outbox) {
        if (registry == null) {
            publishSuccess = null;
            publishRetry = null;
            publishBlocked = null;
            inboxConsumed = null;
            inboxDuplicate = null;
            return;
        }
        publishSuccess = counter(registry, "publish", "success");
        publishRetry = counter(registry, "publish", "retry");
        publishBlocked = counter(registry, "publish", "blocked");
        inboxConsumed = counter(registry, "consume", "consumed");
        inboxDuplicate = counter(registry, "consume", "duplicate");
        Gauge.builder("leetmodel.messaging.outbox.records", outbox, value -> value.count(OutboxStatus.PENDING))
                .tag("status", "pending")
                .register(registry);
        Gauge.builder("leetmodel.messaging.outbox.records", outbox, value -> value.count(OutboxStatus.BLOCKED))
                .tag("status", "blocked")
                .register(registry);
        Gauge.builder("leetmodel.messaging.outbox.oldest.seconds", outbox,
                        JdbcMessageOutbox::oldestPendingAgeSeconds)
                .register(registry);
    }

    /** 记录发布成功。 */
    public void published() {
        increment(publishSuccess);
    }

    /** 记录等待重试。 */
    public void retried() {
        increment(publishRetry);
    }

    /** 记录稳定错误阻塞。 */
    public void blocked() {
        increment(publishBlocked);
    }

    /** 记录首次消费。 */
    public void consumed() {
        increment(inboxConsumed);
    }

    /** 记录重复消费。 */
    public void duplicate() {
        increment(inboxDuplicate);
    }

    private Counter counter(MeterRegistry registry, String operation, String outcome) {
        return Counter.builder("leetmodel.messaging.operations")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry);
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
