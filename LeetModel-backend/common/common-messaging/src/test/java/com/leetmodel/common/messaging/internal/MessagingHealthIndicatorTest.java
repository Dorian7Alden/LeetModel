package com.leetmodel.common.messaging.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessagingHealthIndicatorTest {

    @Test
    void singleBlockedOutboxMustBeDegradedRatherThanDown() {
        JdbcMessageOutbox outbox = mock(JdbcMessageOutbox.class);
        when(outbox.count(OutboxStatus.BLOCKED)).thenReturn(1L);
        when(outbox.count(OutboxStatus.PENDING)).thenReturn(2L);
        when(outbox.count(OutboxStatus.SENDING)).thenReturn(1L);
        when(outbox.oldestPendingAgeSeconds()).thenReturn(15L);

        Health health = new MessagingHealthIndicator(outbox).health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getStatus()).isNotEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("blocked", 1L)
                .containsEntry("pending", 3L)
                .containsEntry("oldestPendingSeconds", 15L);
    }
}
