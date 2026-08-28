package com.leetmodel.aigateway.scheduling;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AiRateLimitBackoffTest {

    @Test
    void appliesBoundedRetryAfterAndRecoversGradually() {
        MutableClock clock = new MutableClock();
        AiRateLimitBackoff backoff = new AiRateLimitBackoff(clock);
        assertThat(backoff.onRateLimited(Duration.ofMinutes(5))).isEqualTo(Duration.ofSeconds(30));
        assertThat(backoff.allowDispatch()).isFalse();
        clock.advance(Duration.ofSeconds(30));
        assertThat(backoff.allowDispatch()).isTrue();

        backoff.onRateLimited(null);
        assertThat(backoff.snapshot().consecutiveRateLimits()).isEqualTo(2);
        clock.advance(Duration.ofSeconds(2));
        backoff.onSuccess();
        assertThat(backoff.snapshot().consecutiveRateLimits()).isEqualTo(1);
        assertThat(backoff.allowDispatch()).isFalse();
        clock.advance(Duration.ofMillis(250));
        assertThat(backoff.allowDispatch()).isTrue();
        backoff.onSuccess();
        assertThat(backoff.snapshot().consecutiveRateLimits()).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-28T12:00:00Z");
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
