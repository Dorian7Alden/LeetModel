package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * 可靠消息低基数吞吐、积压、年龄与处理耗时指标。
 */
public final class MessagingMetrics {

    private final MeterRegistry registry;
    private final MultiGauge consumerBacklog;
    private final MultiGauge consumerOldest;
    private final MultiGauge consumerAvailable;
    private final MultiGauge deadLetterRecords;
    private final MultiGauge deadLetterOldest;
    private final MultiGauge deadLetterAvailable;

    /**
     * 兼容只提供 Outbox 的测试与轻量调用。
     */
    public MessagingMetrics(MeterRegistry registry, JdbcMessageOutbox outbox) {
        this(registry, outbox, null);
    }

    /**
     * 创建指标并绑定 Outbox/Inbox 状态仪表。
     *
     * @param registry Micrometer 注册表，可为空
     * @param outbox Outbox 仓库
     * @param inbox Inbox 仓库，可为空
     */
    public MessagingMetrics(
            MeterRegistry registry,
            JdbcMessageOutbox outbox,
            JdbcMessageInbox inbox
    ) {
        this.registry = registry;
        if (registry == null) {
            consumerBacklog = null;
            consumerOldest = null;
            consumerAvailable = null;
            deadLetterRecords = null;
            deadLetterOldest = null;
            deadLetterAvailable = null;
            return;
        }
        consumerBacklog = MultiGauge.builder("leetmodel.messaging.consumer.backlog")
                .description("Broker messages waiting behind local consumer offsets")
                .register(registry);
        consumerOldest = MultiGauge.builder("leetmodel.messaging.consumer.oldest.seconds")
                .description("Age of the oldest unconsumed Broker message")
                .register(registry);
        consumerAvailable = MultiGauge.builder("leetmodel.messaging.consumer.metrics.available")
                .description("Whether consumer backlog facts are available")
                .register(registry);
        deadLetterRecords = MultiGauge.builder("leetmodel.messaging.dlq.records")
                .description("Broker dead-letter records")
                .register(registry);
        deadLetterOldest = MultiGauge.builder("leetmodel.messaging.dlq.oldest.seconds")
                .description("Age of the oldest dead-letter record")
                .register(registry);
        deadLetterAvailable = MultiGauge.builder("leetmodel.messaging.dlq.metrics.available")
                .description("Whether DLQ facts are available")
                .register(registry);
        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder("leetmodel.messaging.outbox.records", outbox,
                            value -> value.count(status))
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .description("Outbox records by stable state")
                    .register(registry);
            Gauge.builder("leetmodel.messaging.outbox.oldest.seconds", outbox,
                            value -> value.oldestAgeSeconds(List.of(status)))
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .description("Age of the oldest Outbox record by stable state")
                    .register(registry);
        }
        if (inbox != null) {
            for (String status : List.of("PROCESSING", "CONSUMED")) {
                Gauge.builder("leetmodel.messaging.inbox.records", inbox,
                                value -> value.count(status))
                        .tag("status", status.toLowerCase(Locale.ROOT))
                        .description("Inbox records by stable state")
                        .register(registry);
            }
            Gauge.builder("leetmodel.messaging.inbox.oldest.processing.seconds", inbox,
                            JdbcMessageInbox::oldestProcessingAgeSeconds)
                    .description("Age of the oldest Inbox transaction still processing")
                    .register(registry);
        }
    }

    /** 用本服务固定消费者集合替换 Broker 快照。 */
    public void updateBroker(
            List<RocketMqConsumerControl.ConsumerBacklogSnapshot> consumers,
            List<MessagingDeadLetterQueueDTO> deadLetters
    ) {
        if (registry == null) return;
        try {
            updateBrokerSafely(consumers, deadLetters);
        } catch (RuntimeException ignored) {
            // 指标刷新失败不得改变消息业务路径；下次周期会重新覆盖快照。
        }
    }

    private void updateBrokerSafely(
            List<RocketMqConsumerControl.ConsumerBacklogSnapshot> consumers,
            List<MessagingDeadLetterQueueDTO> deadLetters
    ) {
        List<MultiGauge.Row<?>> backlogRows = new ArrayList<>();
        List<MultiGauge.Row<?>> oldestRows = new ArrayList<>();
        List<MultiGauge.Row<?>> consumerAvailabilityRows = new ArrayList<>();
        for (RocketMqConsumerControl.ConsumerBacklogSnapshot value : consumers) {
            Tags tags = consumerTags(value.consumerGroup(), value.topic());
            backlogRows.add(MultiGauge.Row.of(tags, value.backlog()));
            oldestRows.add(MultiGauge.Row.of(tags, value.oldestUnconsumedSeconds()));
            consumerAvailabilityRows.add(MultiGauge.Row.of(tags, value.available() ? 1D : 0D));
        }
        consumerBacklog.register(backlogRows, true);
        consumerOldest.register(oldestRows, true);
        consumerAvailable.register(consumerAvailabilityRows, true);

        List<MultiGauge.Row<?>> deadLetterRows = new ArrayList<>();
        List<MultiGauge.Row<?>> deadLetterAgeRows = new ArrayList<>();
        List<MultiGauge.Row<?>> deadLetterAvailabilityRows = new ArrayList<>();
        for (MessagingDeadLetterQueueDTO value : deadLetters) {
            Tags tags = dlqTags(value.consumerGroup());
            deadLetterRows.add(MultiGauge.Row.of(tags, value.messageCount()));
            deadLetterAgeRows.add(MultiGauge.Row.of(tags, oldestSeconds(value)));
            deadLetterAvailabilityRows.add(MultiGauge.Row.of(tags, value.available() ? 1D : 0D));
        }
        deadLetterRecords.register(deadLetterRows, true);
        deadLetterOldest.register(deadLetterAgeRows, true);
        deadLetterAvailable.register(deadLetterAvailabilityRows, true);
    }

    /** 记录 Outbox 领取类型。 */
    public void claimed(String topic, boolean takeover) {
        increment(counter("leetmodel.messaging.outbox.claims",
                "topic", dimension(topic),
                "claim_type", takeover ? "takeover" : "normal"), 1D);
    }

    /** 记录发布结果与耗时。 */
    public void published(String topic, String outcome, long elapsedNanos) {
        String safeTopic = dimension(topic);
        String safeOutcome = outcome(outcome);
        increment(counter("leetmodel.messaging.publish",
                "topic", safeTopic, "outcome", safeOutcome), 1D);
        record(timer("leetmodel.messaging.publish.duration",
                "topic", safeTopic, "outcome", safeOutcome), elapsedNanos);
    }

    /** 记录 Inbox 结果与短事务耗时。 */
    public void consumed(String consumerGroup, String outcome, long elapsedNanos) {
        String safeGroup = dimension(consumerGroup);
        String safeOutcome = outcome(outcome);
        increment(counter("leetmodel.messaging.consume",
                "consumer_group", safeGroup, "outcome", safeOutcome), 1D);
        record(timer("leetmodel.messaging.consume.duration",
                "consumer_group", safeGroup, "outcome", safeOutcome), elapsedNanos);
    }

    /** 记录人工接受的重放事件数量。 */
    public void replayed(int count) {
        if (count > 0) {
            increment(counter("leetmodel.messaging.operations",
                    "operation", "replay", "outcome", "accepted"), count);
        }
    }

    /** 记录一次真实 consumer 暂停。 */
    public void consumerPaused() {
        increment(counter("leetmodel.messaging.operations",
                "operation", "consumer_control", "outcome", "paused"), 1D);
    }

    /** 记录一次真实 consumer 恢复。 */
    public void consumerResumed() {
        increment(counter("leetmodel.messaging.operations",
                "operation", "consumer_control", "outcome", "resumed"), 1D);
    }

    private Counter counter(String name, String... tags) {
        if (registry == null) return null;
        try {
            return registry.counter(name, tags);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Timer timer(String name, String... tags) {
        if (registry == null) {
            return null;
        }
        try {
            return Timer.builder(name)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofMinutes(5))
                    .tags(tags)
                    .register(registry);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void increment(Counter counter, double amount) {
        if (counter != null) {
            counter.increment(amount);
        }
    }

    private void record(Timer timer, long elapsedNanos) {
        if (timer != null) {
            timer.record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
        }
    }

    private String dimension(String value) {
        if (value == null || value.isBlank() || value.length() > 255
                || !value.matches("[a-zA-Z0-9%._:-]+")) {
            return "unknown";
        }
        return value;
    }

    private String outcome(String value) {
        if (value == null) {
            return "unknown";
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "success", "retry", "blocked", "consumed", "duplicate", "failure" ->
                    value.toLowerCase(Locale.ROOT);
            default -> "unknown";
        };
    }

    private Tags consumerTags(String consumerGroup, String topic) {
        return Tags.of("consumer_group", dimension(consumerGroup), "topic", dimension(topic));
    }

    private Tags dlqTags(String consumerGroup) {
        return Tags.of("consumer_group", dimension(consumerGroup));
    }

    private double oldestSeconds(MessagingDeadLetterQueueDTO value) {
        if (!value.available() || value.oldestMessageAt() == null) return 0D;
        return Math.max(0L, java.time.Duration.between(value.oldestMessageAt(),
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)).toSeconds());
    }
}
