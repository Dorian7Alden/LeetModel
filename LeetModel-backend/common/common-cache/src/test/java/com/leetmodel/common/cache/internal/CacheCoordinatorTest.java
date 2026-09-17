package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.cache.config.CacheNamespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheCoordinatorTest {

    private BusinessRedisClient redisClient;
    private CacheCoordinator coordinator;
    private CacheCoordinator.CacheStateListener listener;
    private ObjectMapper objectMapper;
    private Consumer<String> messageConsumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisClient = mock(BusinessRedisClient.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        coordinator = new CacheCoordinator(
                redisClient,
                new CacheNamespace("test", "test-service"),
                objectMapper
        );
        listener = mock(CacheCoordinator.CacheStateListener.class);
        coordinator.addListener(listener);
        ArgumentCaptor<Consumer<String>> consumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(redisClient).addMessageConsumer(consumerCaptor.capture());
        messageConsumer = consumerCaptor.getValue();
    }

    @Test
    void shouldInitializeAndReconcileObservedRevision() {
        when(redisClient.generation()).thenReturn("generation");
        when(redisClient.revision("generation", "region", "scope")).thenReturn(3L, 4L);
        when(redisClient.ping()).thenReturn(true);

        assertEquals(3L, coordinator.observed("region", "scope").revision());
        coordinator.reconcile();

        assertEquals(4L, coordinator.observed("region", "scope").revision());
        verify(listener).onScopeChanged("region", "scope");
    }

    @Test
    void shouldApplyPubSubMessageOnceAndIgnoreOlderRevision() throws Exception {
        CacheInvalidationEvent newest = event(8L);
        CacheInvalidationEvent older = event(7L);

        messageConsumer.accept(objectMapper.writeValueAsString(newest));
        messageConsumer.accept(objectMapper.writeValueAsString(older));

        assertEquals(8L, coordinator.observed("region", "scope").revision());
        verify(listener, times(1)).onGenerationChanged();
        verify(listener, times(1)).onScopeChanged("region", "scope");
    }

    @Test
    void shouldNotifyDegradedStateOnlyOnceUntilRecovery() {
        RedisUnavailableException failure =
                new RedisUnavailableException(new IllegalStateException("down"));
        when(redisClient.generation()).thenThrow(failure);

        org.junit.jupiter.api.Assertions.assertThrows(
                RedisUnavailableException.class,
                () -> coordinator.observed("region", "scope")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                RedisUnavailableException.class,
                () -> coordinator.observed("region", "scope")
        );

        verify(listener, times(1)).onRedisUnavailable();
    }

    private CacheInvalidationEvent event(long revision) {
        return new CacheInvalidationEvent(
                UUID.randomUUID().toString(),
                "test-service",
                "region",
                "scope",
                revision,
                "v1",
                LocalDateTime.now(),
                "generation"
        );
    }
}
