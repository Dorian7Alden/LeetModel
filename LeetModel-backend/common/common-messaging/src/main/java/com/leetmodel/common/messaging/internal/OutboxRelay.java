package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.PermanentPublishException;
import com.leetmodel.common.messaging.PublishReceipt;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.ExecutionSpanOperation;
import com.leetmodel.common.core.telemetry.SkyWalkingExecutionSpan;
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
    private final MessageCodec codec;
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
            MessageCodec codec,
            Clock clock,
            String owner,
            int batchSize,
            Duration lease
    ) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.codec = codec;
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
        List<ClaimedOutboxMessage> messages = outbox.claimObserved(owner, batchSize, lease);
        for (ClaimedOutboxMessage claimed : messages) {
            PendingMessage message = claimed.message();
            metrics.claimed(message.topic(), claimed.takeover());
            if (!outbox.renewLease(message.eventId(), owner, lease)) {
                log.atInfo()
                        .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.OUTBOX_LEASE_LOST)
                        .addKeyValue(LogFieldNames.EVENT_ID, message.eventId())
                        .addKeyValue(LogFieldNames.MESSAGE_TOPIC, message.topic())
                        .addKeyValue(LogFieldNames.OUTCOME, "lease_lost")
                        .log("Outbox lease lost before publish");
                continue;
            }
            relay(message, claimed.takeover());
        }
    }

    private void relay(PendingMessage message, boolean takeover) {
        long started = System.nanoTime();
        try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                ExecutionSpanOperation.OUTBOX_PUBLISH, correlation(message)).attemptKind(takeover)) {
            try {
                PublishReceipt receipt = publisher.publish(message);
                outbox.markPublished(message.eventId(), owner, receipt.brokerMessageId());
                metrics.published(message.topic(), "success", System.nanoTime() - started);
                span.outcome("success");
            } catch (PermanentPublishException | MessageContractException exception) {
                span.outcome("blocked").error("contract");
                outbox.markBlocked(message.eventId(), owner, exception.getClass().getSimpleName());
                metrics.published(message.topic(), "blocked", System.nanoTime() - started);
                log.atError()
                        .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.OUTBOX_PUBLISH_BLOCKED)
                        .addKeyValue(LogFieldNames.EVENT_ID, message.eventId())
                        .addKeyValue(LogFieldNames.MESSAGE_TOPIC, message.topic())
                        .addKeyValue(LogFieldNames.RETRY_COUNT, message.retryCount())
                        .addKeyValue(LogFieldNames.OUTCOME, "blocked")
                        .setCause(exception)
                        .log("Outbox publish permanently blocked");
            } catch (RuntimeException exception) {
                span.outcome("retry").error("transport");
                Duration delay = retryPolicy.delay(message.retryCount(), message.eventId());
                Instant nextAttemptAt = Instant.now(clock).plus(delay);
                outbox.markRetry(message.eventId(), owner, nextAttemptAt,
                        exception.getClass().getSimpleName());
                metrics.published(message.topic(), "retry", System.nanoTime() - started);
                log.atWarn()
                        .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.OUTBOX_PUBLISH_RETRY)
                        .addKeyValue(LogFieldNames.EVENT_ID, message.eventId())
                        .addKeyValue(LogFieldNames.MESSAGE_TOPIC, message.topic())
                        .addKeyValue(LogFieldNames.RETRY_COUNT, message.retryCount() + 1)
                        .addKeyValue(LogFieldNames.DURATION_MS, delay.toMillis())
                        .addKeyValue(LogFieldNames.OUTCOME, "retry")
                        .addKeyValue(LogFieldNames.EXCEPTION_TYPE, exception.getClass().getName())
                        .log("Outbox publish scheduled for retry");
            }
        }
    }

    private CorrelationSnapshot correlation(PendingMessage message) {
        try {
            MessageEnvelopeV1<Object> envelope = codec.decode(
                    codec.bytes(message.payloadJson()), Object.class);
            return CorrelationSnapshot.EMPTY
                    .withTraceId(envelope.traceId())
                    .withMessage(envelope.eventId(), envelope.operationId());
        } catch (RuntimeException ignored) {
            return CorrelationSnapshot.EMPTY.withMessage(message.eventId(), null);
        }
    }

}
