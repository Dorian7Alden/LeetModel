package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingMetricsTest {

    @Test
    void exposesBoundedThroughputTakeoverBacklogAndDeadLetterDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessagingMetrics metrics = new MessagingMetrics(registry,
                mock(JdbcMessageOutbox.class), mock(JdbcMessageInbox.class));

        metrics.claimed("lm-dev%review-task-v1", false);
        metrics.claimed("lm-dev%review-task-v1", true);
        metrics.published("lm-dev%review-task-v1", "retry", 1_000_000L);
        metrics.consumed("lm-dev%cg-review-task-v1", "duplicate", 2_000_000L);
        metrics.updateBroker(List.of(new RocketMqConsumerControl.ConsumerBacklogSnapshot(
                        "lm-dev%cg-review-task-v1", "lm-dev%review-task-v1", 7L, true)),
                List.of(new MessagingDeadLetterQueueDTO("ai-review-service",
                        "lm-dev%cg-review-task-v1", "%DLQ%lm-dev%cg-review-task-v1", 2L,
                        LocalDateTime.now(ZoneOffset.UTC).minusSeconds(30), true)));

        assertThat(registry.get("leetmodel.messaging.outbox.claims")
                .tag("claim_type", "takeover").counter().count()).isEqualTo(1D);
        assertThat(registry.get("leetmodel.messaging.publish")
                .tag("outcome", "retry").counter().count()).isEqualTo(1D);
        assertThat(registry.get("leetmodel.messaging.consume")
                .tag("outcome", "duplicate").counter().count()).isEqualTo(1D);
        assertThat(registry.get("leetmodel.messaging.consumer.backlog").gauge().value()).isEqualTo(7D);
        assertThat(registry.get("leetmodel.messaging.dlq.records").gauge().value()).isEqualTo(2D);
        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).noneMatch(tag ->
                        com.leetmodel.common.core.metrics.MetricTagPolicy.isForbiddenIdTag(tag.getKey())));
    }
}
