package com.leetmodel.common.cache.internal;

import com.leetmodel.common.cache.CacheVersionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpCacheSupportTest {

    @Test
    void shouldUseShortTimeBucketInsteadOfPermanentDisabledEtag() {
        CacheVersionView version = new NoOpCacheSupport().current("region", "scope");

        assertTrue(version.degraded());
        assertTrue(version.generation().matches("disabled-[0-9]+"));
    }
}
