package com.leetmodel.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCacheSupportTest {

    @Test
    void shouldReturnPublicCacheHeadersAndMatchEtagList() {
        HttpCacheSupport.Validator validator = HttpCacheSupport.validator(
                new CacheVersionView("abc123", 7L, false),
                "v1",
                "detail:123",
                Duration.ofSeconds(60)
        );

        assertTrue(validator.matches("W/\"old\", " + validator.etag()));
        ResponseEntity<String> response = validator.notModified();
        assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
        assertEquals(validator.etag(), response.getHeaders().getETag());
        assertEquals(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic().getHeaderValue(),
                response.getHeaders().getCacheControl());
    }

    @Test
    void shouldChangeEtagWhenRevisionChanges() {
        String first = validator(1L, false).etag();
        String second = validator(2L, false).etag();

        assertNotEquals(first, second);
    }

    @Test
    void shouldCapMaxAgeAtFiveSecondsWhileDegraded() {
        HttpCacheSupport.Validator validator = validator(0L, true);

        assertEquals(Duration.ofSeconds(5), validator.maxAge());
    }

    private HttpCacheSupport.Validator validator(long revision, boolean degraded) {
        return HttpCacheSupport.validator(
                new CacheVersionView("generation", revision, degraded),
                "v1",
                "page:hash",
                Duration.ofMinutes(1)
        );
    }
}
