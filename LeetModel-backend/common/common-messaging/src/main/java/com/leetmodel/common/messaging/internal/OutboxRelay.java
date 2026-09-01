package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.PermanentPublishException;
import com.leetmodel.common.messaging.PublishReceipt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 通过短租约将本地 Outbox 至少一次投递到 RocketMQ。
 */
@Slf4j
public final class OutboxRelay {

    private final JdbcMessageOutbox outbox;
    private final MessagePublisher publisher;
    private final OutboxRetryPolicy retryPolicy;
    private final MessagingMetrics metrics;
    private final Clock clock;
    private final String owner;
    private final int batchSize;
    private final Duration lease;

    /**
     * 创建 Outbox Relay。
     *
     * @param outbox Outbox 仓库
     * @param publisher 消息传输端口
     * @param retryPolicy 重试退避策略
     * @param metrics 消息指标
     * @param clock 时间源
     * @param owner 当前实例租约标识
     * @param batchSize 单批上限
     * @param lease 租约时长
     */
    public OutboxRelay(
            JdbcMessageOutbox outbox,
            MessagePublisher publisher,
            OutboxRetryPolicy retryPolicy,
            MessagingMetrics metrics,
            Clock clock,
            String owner,
            int batchSize,
            Duration lease
    ) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.clock = clock;
        this.owner = owner;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /**
     * 周期领取并发布到期消息。
     */
    @Scheduled(fixedDelayString = "${leetmodel.messaging.relay.interval-ms:1000}")
    public void relayPending() {
        List<PendingMessage> messages = outbox.claim(owner, batchSize, lease);
        for (PendingMessage message : messages) {
            if (!outbox.renewLease(message.eventId(), owner, lease)) {
                log.info("消息 Outbox 已由其他 Relay 接管，跳过本次发送: eventId={}", message.eventId());
                continue;
            }
            relay(message);
        }
    }

    private void relay(PendingMessage message) {
        try {
            PublishReceipt receipt = publisher.publish(message);
            outbox.markPublished(message.eventId(), owner, receipt.brokerMessageId());
            metrics.published();
        } catch (PermanentPublishException | MessageContractException exception) {
            outbox.markBlocked(message.eventId(), owner, exception.getClass().getSimpleName());
            metrics.blocked();
            log.error("消息 Outbox 因稳定错误被阻塞: eventId={}, topic={}",
                    message.eventId(), message.topic(), exception);
        } catch (RuntimeException exception) {
            Duration delay = retryPolicy.delay(message.retryCount(), message.eventId());
            Instant nextAttemptAt = Instant.now(clock).plus(delay);
            outbox.markRetry(message.eventId(), owner, nextAttemptAt,
                    exception.getClass().getSimpleName());
            metrics.retried();
            log.warn("消息 Outbox 发布失败并进入退避: eventId={}, retry={}, delayMs={}",
                    message.eventId(), message.retryCount() + 1, delay.toMillis());
        }
    }
}
