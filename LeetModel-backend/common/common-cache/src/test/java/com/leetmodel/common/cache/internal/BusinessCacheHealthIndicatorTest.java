package com.leetmodel.common.cache.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessCacheHealthIndicatorTest {

    @Test
    void unavailableRedisMustBeDegradedRatherThanDown() {
        BusinessRedisClient redisClient = mock(BusinessRedisClient.class);
        CacheOutboxRepository repository = mock(CacheOutboxRepository.class);
        when(redisClient.ping()).thenReturn(false);
        when(repository.pendingCount()).thenReturn(3L);

        Health health = new BusinessCacheHealthIndicator(redisClient, repository).health();

        assertThat(health.getStatus()).isEqualTo(new Status("DEGRADED"));
        assertThat(health.getDetails()).containsEntry("pendingOutbox", 3L);
    }

    @Test
    void redisFailureMustNotBecomeLivenessDownSignal() {
        BusinessRedisClient redisClient = mock(BusinessRedisClient.class);
        CacheOutboxRepository repository = mock(CacheOutboxRepository.class);
        when(redisClient.ping()).thenThrow(new IllegalStateException("redis unavailable"));

        Health health = new BusinessCacheHealthIndicator(redisClient, repository).health();

        assertThat(health.getStatus()).isNotEqualTo(Status.DOWN);
        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
    }
}
