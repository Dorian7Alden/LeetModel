package com.leetmodel.common.core.logging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureLogLimiterTest {

    @Test
    void shouldEmitFirstPeriodicSummaryAndRecoveryWhileCountingSuppression() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MutableClock clock = new MutableClock();
        LogRateLimitProperties properties = new LogRateLimitProperties();
        properties.setSummaryInterval(Duration.ofSeconds(10));
        FailureLogLimiter limiter = new FailureLogLimiter(registry, properties, clock);

        assertThat(limiter.onFailure("ai.cost-enrichment", LogEventCodes.DEPENDENCY_CALL_FAILED).kind())
                .isEqualTo(FailureLogLimiter.Kind.FIRST);
        assertThat(limiter.onFailure("ai.cost-enrichment", LogEventCodes.DEPENDENCY_CALL_FAILED).kind())
                .isEqualTo(FailureLogLimiter.Kind.SUPPRESSED);
        assertThat(limiter.onFailure("ai.cost-enrichment", LogEventCodes.DEPENDENCY_CALL_FAILED).kind())
                .isEqualTo(FailureLogLimiter.Kind.SUPPRESSED);

        assertThat(registry.get(FailureLogLimiter.SUPPRESSED_METRIC)
                .tag("event_code", LogEventCodes.DEPENDENCY_CALL_FAILED).counter().count())
                .isEqualTo(2D);

        clock.advance(Duration.ofSeconds(10));
        FailureLogLimiter.Decision summary = limiter.onFailure(
                "ai.cost-enrichment", LogEventCodes.DEPENDENCY_CALL_FAILED);
        assertThat(summary.kind()).isEqualTo(FailureLogLimiter.Kind.SUMMARY);
        assertThat(summary.suppressedCount()).isEqualTo(2L);
        assertThat(summary.totalFailures()).isEqualTo(4L);

        FailureLogLimiter.Decision recovery = limiter.onRecovery("ai.cost-enrichment");
        assertThat(recovery.kind()).isEqualTo(FailureLogLimiter.Kind.RECOVERY);
        assertThat(recovery.totalFailures()).isEqualTo(4L);
        assertThat(limiter.onRecovery("ai.cost-enrichment").shouldLog()).isFalse();
    }

    @Test
    void shouldRejectHighCardinalityKeysAndBoundState() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogRateLimitProperties properties = new LogRateLimitProperties();
        properties.setMaxKeys(1);
        FailureLogLimiter limiter = new FailureLogLimiter(registry, properties);

        assertThatThrownBy(() -> limiter.onFailure("task:123", "FAIL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(limiter.onFailure("dependency.one", "FAIL").kind())
                .isEqualTo(FailureLogLimiter.Kind.FIRST);
        assertThat(limiter.onFailure("dependency.two", "FAIL").kind())
                .isEqualTo(FailureLogLimiter.Kind.SUPPRESSED);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-09-02T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
