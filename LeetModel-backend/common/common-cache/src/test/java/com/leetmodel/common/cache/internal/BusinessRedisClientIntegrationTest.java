package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.cache.config.CacheNamespace;
import com.leetmodel.common.cache.config.CacheProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_CACHE_REDIS_INTEGRATION", matches = "true")
class BusinessRedisClientIntegrationTest {

    private final CacheProperties properties = new CacheProperties();
    private final CacheNamespace namespace = new CacheNamespace("cache-it", "cache-test");
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private BusinessRedisClient client;

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
    }

    @Test
    void shouldReadWriteExpireAndDeleteValue() {
        client = new BusinessRedisClient(properties, namespace, objectMapper);
        String key = "lm:cache-it:test:" + UUID.randomUUID();

        client.set(key, "value", Duration.ofSeconds(10));

        assertEquals("value", client.get(key));
        client.delete(key);
        assertNull(client.get(key));
    }

    @Test
    void shouldPublishOnlyMonotonicallyIncreasingRevision() throws Exception {
        client = new BusinessRedisClient(properties, namespace, objectMapper);
        String generation = client.generation();
        String scopeKey = "scope-" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();
        client.addMessageConsumer(message -> {
            payload.set(message);
            delivered.countDown();
        });
        client.ensureSubscribed();

        CacheInvalidationEvent current = event(generation, scopeKey, 12L);
        client.applyInvalidation(current);
        client.applyInvalidation(event(generation, scopeKey, 11L));

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(12L,
                objectMapper.readValue(payload.get(), CacheInvalidationEvent.class).revision());
        assertEquals(12L, client.revision(generation, "test-region", scopeKey));
        client.delete(namespace.revisionKey(generation, "test-region", scopeKey));
    }

    private CacheInvalidationEvent event(String generation, String scopeKey, long revision) {
        return new CacheInvalidationEvent(
                UUID.randomUUID().toString(),
                namespace.ownerService(),
                "test-region",
                scopeKey,
                revision,
                "v1",
                LocalDateTime.now(),
                generation
        );
    }
}
