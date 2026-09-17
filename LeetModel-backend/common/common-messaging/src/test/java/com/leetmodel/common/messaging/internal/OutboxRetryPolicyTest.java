package com.leetmodel.common.messaging.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryPolicyTest {

    private final OutboxRetryPolicy policy = new OutboxRetryPolicy();

    @Test
    void shouldUseConfiguredBackoffWithTwentyPercentJitter() {
        assertWithin(policy.delay(0, "event-a"), Duration.ofSeconds(1));
        assertWithin(policy.delay(1, "event-a"), Duration.ofSeconds(5));
        assertWithin(policy.delay(2, "event-a"), Duration.ofSeconds(30));
        assertWithin(policy.delay(3, "event-a"), Duration.ofMinutes(2));
        assertWithin(policy.delay(4, "event-a"), Duration.ofMinutes(10));
        assertWithin(policy.delay(99, "event-a"), Duration.ofMinutes(30));
    }

    private void assertWithin(Duration actual, Duration base) {
        assertThat(actual.toMillis())
                .isBetween(Math.round(base.toMillis() * 0.8D), Math.round(base.toMillis() * 1.2D));
    }
}
