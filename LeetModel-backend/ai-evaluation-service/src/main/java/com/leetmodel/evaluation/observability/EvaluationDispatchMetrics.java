package com.leetmodel.evaluation.observability;

import com.leetmodel.evaluation.service.OnlineCorePressureGuard;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class EvaluationDispatchMetrics {
    private static final List<String> STATUSES = List.of(
            "WAITING", "RUNNING", "SUCCEEDED", "FAILED", "UNKNOWN", "CANCELLED");
    private final JdbcTemplate jdbcTemplate;
    private final OnlineCorePressureGuard pressureGuard;
    private final MeterRegistry registry;

    public EvaluationDispatchMetrics(ObjectProvider<MeterRegistry> registryProvider,
                                     JdbcTemplate jdbcTemplate,
                                     OnlineCorePressureGuard pressureGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.pressureGuard = pressureGuard;
        MeterRegistry registry = registryProvider.getIfAvailable();
        this.registry = registry;
        if (registry == null) return;
        for (String status : STATUSES) {
            Gauge.builder("leetmodel.evaluation.slots", this,
                            metrics -> metrics.countStatus(status))
                    .tag("status", status.toLowerCase(Locale.ROOT))
                    .register(registry);
        }
        Gauge.builder("leetmodel.evaluation.slots.waiting", this,
                        EvaluationDispatchMetrics::waiting)
                .register(registry);
        Gauge.builder("leetmodel.evaluation.slots.oldest_waiting_seconds", this,
                        EvaluationDispatchMetrics::oldestWaitingSeconds)
                .register(registry);
        Gauge.builder("leetmodel.evaluation.slots.expired_leases", this,
                        EvaluationDispatchMetrics::expired)
                .register(registry);
        Gauge.builder("leetmodel.evaluation.batch.paused", pressureGuard,
                        guard -> guard.isPausedSnapshot() ? 1D : 0D)
                .register(registry);
    }

    public void claimed() {
        if (registry == null) return;
        try {
            registry.counter("leetmodel.evaluation.slot.claims",
                    "claim_type", "normal").increment();
        } catch (RuntimeException ignored) {
            // 指标故障不得改变任务领取结果。
        }
    }

    public void recoveredUnknown() {
        if (registry == null) return;
        try {
            registry.counter("leetmodel.evaluation.slot.claims",
                    "claim_type", "takeover_unknown").increment();
        } catch (RuntimeException ignored) {
            // 指标故障不得改变恢复结果。
        }
    }

    public void attemptFinished(String status, long elapsedNanos) {
        if (registry == null) return;
        try {
            Timer.builder("leetmodel.evaluation.slot.attempt.duration")
                    .tag("outcome", outcome(status))
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
        } catch (RuntimeException ignored) {
            // 指标故障不得改变任务终态。
        }
    }

    private String outcome(String status) {
        if (status == null) return "unknown";
        String normalized = status.toLowerCase(Locale.ROOT);
        return List.of("waiting", "running", "succeeded", "failed", "unknown", "cancelled")
                .contains(normalized) ? normalized : "unknown";
    }

    private double waiting() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt "
                + "WHERE deleted = 0 AND status = 'WAITING'", Long.class);
        return count == null ? 0D : count.doubleValue();
    }

    private double countStatus(String status) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt "
                + "WHERE deleted = 0 AND status = ?", Long.class, status);
        return count == null ? 0D : count.doubleValue();
    }

    private double oldestWaitingSeconds() {
        Long seconds = jdbcTemplate.queryForObject("SELECT COALESCE(TIMESTAMPDIFF(SECOND, "
                + "MIN(create_time), NOW()), 0) FROM evaluation_run_attempt "
                + "WHERE deleted = 0 AND status = 'WAITING' AND next_run_at <= NOW()", Long.class);
        return seconds == null ? 0D : Math.max(0D, seconds.doubleValue());
    }

    private double expired() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt "
                + "WHERE deleted = 0 AND status = 'RUNNING' AND lease_expires_at < NOW()", Long.class);
        return count == null ? 0D : count.doubleValue();
    }
}
