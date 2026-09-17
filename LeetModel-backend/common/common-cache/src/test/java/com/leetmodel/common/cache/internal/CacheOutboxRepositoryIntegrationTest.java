package com.leetmodel.common.cache.internal;

import com.leetmodel.common.cache.config.CacheNamespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_CACHE_MYSQL_INTEGRATION", matches = "true")
class CacheOutboxRepositoryIntegrationTest {

    @Test
    void shouldGenerateRevisionAndTrackRetryUntilDelivered() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        CacheOutboxRepository repository = new CacheOutboxRepository(
                jdbcTemplate,
                new CacheNamespace("cache-it", "cache-test")
        );
        CacheInvalidationEvent event = null;
        try {
            event = repository.insert("test-region", "scope-" + UUID.randomUUID(), "v1");
            assertTrue(event.revision() > 0L);
            assertEquals("cache-test", jdbcTemplate.queryForObject(
                    "SELECT owner_service FROM cache_invalidation_outbox WHERE id = ?",
                    String.class,
                    event.eventId()
            ));

            repository.markFailed(event.eventId(), "RedisUnavailableException");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM cache_invalidation_outbox WHERE id = ?",
                    Integer.class,
                    event.eventId()
            ));

            repository.markDelivered(event.eventId());
            assertNotNull(jdbcTemplate.queryForObject(
                    "SELECT delivered_at FROM cache_invalidation_outbox WHERE id = ?",
                    java.sql.Timestamp.class,
                    event.eventId()
            ));
        } finally {
            if (event != null) {
                jdbcTemplate.update("DELETE FROM cache_invalidation_outbox WHERE id = ?", event.eventId());
            }
        }
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getenv().getOrDefault(
                "CACHE_MYSQL_URL",
                "jdbc:mysql://localhost:3306/lm_problem?useUnicode=true&characterEncoding=UTF-8"
        ));
        dataSource.setUsername(System.getenv().getOrDefault("CACHE_MYSQL_USERNAME", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("CACHE_MYSQL_PASSWORD", "root"));
        return new JdbcTemplate(dataSource);
    }
}
