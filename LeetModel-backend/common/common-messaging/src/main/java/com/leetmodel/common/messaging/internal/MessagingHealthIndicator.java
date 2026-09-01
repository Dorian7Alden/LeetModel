package com.leetmodel.common.messaging.internal;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

/**
 * 暴露消息 Relay 本地可靠性状态。
 */
public final class MessagingHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");

    private final JdbcMessageOutbox outbox;

    /**
     * 创建健康检查。
     *
     * @param outbox Outbox 仓库
     */
    public MessagingHealthIndicator(JdbcMessageOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public Health health() {
        long blocked = outbox.count(OutboxStatus.BLOCKED);
        long pending = outbox.count(OutboxStatus.PENDING) + outbox.count(OutboxStatus.SENDING);
        Health.Builder builder = blocked > 0 ? Health.status(DEGRADED) : Health.up();
        return builder
                .withDetail("pending", pending)
                .withDetail("blocked", blocked)
                .withDetail("oldestPendingSeconds", outbox.oldestPendingAgeSeconds())
                .build();
    }
}
