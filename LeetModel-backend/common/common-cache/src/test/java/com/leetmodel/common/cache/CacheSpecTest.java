package com.leetmodel.common.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheSpecTest {

    @Test
    void shouldAllowCanonicalLogicalKeyWithColon() {
        assertDoesNotThrow(() -> spec("detail:123"));
    }

    @Test
    void shouldRejectUnsafeLogicalKey() {
        assertThrows(IllegalArgumentException.class, () -> spec("keyword:secret value"));
    }

    @Test
    void shouldRejectLocalTtlLongerThanRedisTtl() {
        assertThrows(IllegalArgumentException.class, () -> new CacheSpec(
                "problem-detail",
                "public",
                "v1",
                "detail:123",
                Duration.ofMinutes(11),
                Duration.ofMinutes(10),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        ));
    }

    private CacheSpec spec(String logicalKey) {
        return new CacheSpec(
                "problem-detail",
                "public",
                "v1",
                logicalKey,
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );
    }
}
