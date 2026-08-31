package com.leetmodel.common.cache.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.cache.CacheSpec;
import com.leetmodel.common.cache.config.CacheNamespace;
import com.leetmodel.common.cache.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TieredMultiLevelCacheTest {

    private static final CacheCoordinator.ScopeVersion VERSION =
            new CacheCoordinator.ScopeVersion("generation", 3L);

    private BusinessRedisClient redisClient;
    private CacheCoordinator coordinator;
    private TieredMultiLevelCache cache;
    private JavaType javaType;

    @BeforeEach
    void setUp() {
        redisClient = mock(BusinessRedisClient.class);
        coordinator = mock(CacheCoordinator.class);
        CacheProperties properties = new CacheProperties();
        properties.setTtlJitter(0D);
        ObjectMapper objectMapper = new ObjectMapper();
        javaType = objectMapper.getTypeFactory().constructType(TestValue.class);
        cache = new TieredMultiLevelCache(
                redisClient,
                coordinator,
                new CacheNamespace("test", "test-service"),
                properties,
                objectMapper,
                new CacheMetrics(null)
        );
        when(coordinator.observed("test-region", "public")).thenReturn(VERSION);
        when(coordinator.refresh("test-region", "public")).thenReturn(VERSION);
    }

    @Test
    void shouldReadRedisOnceThenHitLocalCache() {
        when(redisClient.get(anyString()))
                .thenReturn("{\"present\":true,\"payload\":{\"value\":\"redis\"}}");
        AtomicInteger loads = new AtomicInteger();

        TestValue first = cache.get(spec("detail:1"), javaType, () -> load(loads));
        TestValue second = cache.get(spec("detail:1"), javaType, () -> load(loads));

        assertEquals(new TestValue("redis"), first);
        assertEquals(first, second);
        assertEquals(0, loads.get());
        verify(redisClient, times(1)).get(anyString());
    }

    @Test
    void shouldLoadSourceOnceAndPopulateBothCaches() {
        when(redisClient.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        TestValue first = cache.get(spec("detail:2"), javaType, () -> load(loads));
        TestValue second = cache.get(spec("detail:2"), javaType, () -> load(loads));

        assertEquals(new TestValue("database"), first);
        assertEquals(first, second);
        assertEquals(1, loads.get());
        verify(redisClient, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldCacheNegativeResult() {
        when(redisClient.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        Object first = cache.get(spec("detail:missing"), javaType, () -> {
            loads.incrementAndGet();
            return null;
        });
        Object second = cache.get(spec("detail:missing"), javaType, () -> {
            loads.incrementAndGet();
            return null;
        });

        assertNull(first);
        assertNull(second);
        assertEquals(1, loads.get());
    }

    @Test
    void shouldUseFiveSecondCacheWhenRedisIsUnavailable() {
        when(coordinator.observed("test-region", "public"))
                .thenThrow(new RedisUnavailableException(new IllegalStateException("down")));
        AtomicInteger loads = new AtomicInteger();

        TestValue first = cache.get(spec("detail:3"), javaType, () -> load(loads));
        TestValue second = cache.get(spec("detail:3"), javaType, () -> load(loads));

        assertEquals(first, second);
        assertEquals(1, loads.get());
        verify(coordinator, times(2)).redisUnavailable(any(RedisUnavailableException.class));
    }

    @Test
    void shouldNotLoadSourceTwiceWhenRedisFailsAfterSourceLoad() {
        when(redisClient.get(anyString())).thenReturn(null);
        doThrow(new RedisUnavailableException(new IllegalStateException("down")))
                .when(redisClient).set(anyString(), anyString(), any(Duration.class));
        AtomicInteger loads = new AtomicInteger();

        TestValue value = cache.get(spec("detail:4"), javaType, () -> load(loads));

        assertEquals(new TestValue("database"), value);
        assertEquals(1, loads.get());
        verify(coordinator).redisUnavailable(any(RedisUnavailableException.class));
    }

    @Test
    void shouldDeleteCorruptRedisValueAndReloadSource() {
        when(redisClient.get(anyString())).thenReturn("not-json");
        AtomicInteger loads = new AtomicInteger();

        TestValue value = cache.get(spec("detail:5"), javaType, () -> load(loads));

        assertEquals(new TestValue("database"), value);
        assertEquals(1, loads.get());
        verify(redisClient).delete(anyString());
        verify(redisClient).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldRetryWithoutWritingOldValueWhenRevisionChangesDuringLoad() {
        CacheCoordinator.ScopeVersion nextVersion =
                new CacheCoordinator.ScopeVersion("generation", 4L);
        when(coordinator.observed("test-region", "public"))
                .thenReturn(VERSION, nextVersion);
        when(coordinator.refresh("test-region", "public"))
                .thenReturn(nextVersion, nextVersion);
        when(redisClient.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();

        TestValue value = cache.get(spec("detail:6"), javaType, () ->
                new TestValue("database-" + loads.incrementAndGet()));

        assertEquals(new TestValue("database-2"), value);
        verify(redisClient, times(1)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldCoalesceConcurrentSourceLoadsInOneInstance() throws Exception {
        when(redisClient.get(anyString())).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<TestValue>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(() -> cache.get(
                            spec("detail:hot"),
                            javaType,
                            () -> {
                                loads.incrementAndGet();
                                entered.countDown();
                                try {
                                    release.await();
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(exception);
                                }
                                return new TestValue("database");
                            }
                    )))
                    .toList();
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            release.countDown();

            for (Future<TestValue> future : futures) {
                assertEquals(new TestValue("database"), future.get());
            }
            assertEquals(1, loads.get());
            verify(redisClient, times(1)).get(anyString());
        } finally {
            executor.shutdownNow();
        }
    }

    private TestValue load(AtomicInteger loads) {
        loads.incrementAndGet();
        return new TestValue("database");
    }

    private CacheSpec spec(String logicalKey) {
        return new CacheSpec(
                "test-region",
                "public",
                "v1",
                logicalKey,
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );
    }

    private record TestValue(String value) {
    }
}
