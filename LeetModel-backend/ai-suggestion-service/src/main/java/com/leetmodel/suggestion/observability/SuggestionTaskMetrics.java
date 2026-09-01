package com.leetmodel.suggestion.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SuggestionTaskMetrics {
    private static final List<String> STATUSES = List.of(
            "WAITING", "LEASED", "RUNNING", "COMPLETED", "FAILED", "UNKNOWN");
    private final JdbcTemplate jdbcTemplate;

    public SuggestionTaskMetrics(ObjectProvider<MeterRegistry> registryProvider,
                                 JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) return;
        for (String status : STATUSES) {
            Gauge.builder("leetmodel.suggestion.tasks", this,
                            metrics -> metrics.countStatus(status))
                    .tag("status", status)
                    .description("Suggestion domain tasks by stable status")
                    .register(registry);
        }
        Gauge.builder("leetmodel.suggestion.tasks.expired_leases", this,
                        SuggestionTaskMetrics::countExpiredLeases)
                .description("Suggestion tasks whose active lease has expired")
                .register(registry);
        Gauge.builder("leetmodel.suggestion.tasks.oldest_waiting_seconds", this,
                        SuggestionTaskMetrics::oldestWaitingSeconds)
                .description("Age of the oldest due suggestion task")
                .register(registry);
    }

    private double countStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM suggestion_task WHERE deleted = 0 AND status = ?",
                Long.class, status);
        return count == null ? 0D : count.doubleValue();
    }

    private double countExpiredLeases() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM suggestion_task
                WHERE deleted = 0 AND status IN ('LEASED', 'RUNNING') AND lease_expires_at < NOW()
                """, Long.class);
        return count == null ? 0D : count.doubleValue();
    }

    private double oldestWaitingSeconds() {
        Long seconds = jdbcTemplate.queryForObject("""
                SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(create_time), NOW()), 0)
                FROM suggestion_task WHERE deleted = 0 AND status = 'WAITING' AND next_run_at <= NOW()
                """, Long.class);
        return seconds == null ? 0D : Math.max(0D, seconds.doubleValue());
    }
}
