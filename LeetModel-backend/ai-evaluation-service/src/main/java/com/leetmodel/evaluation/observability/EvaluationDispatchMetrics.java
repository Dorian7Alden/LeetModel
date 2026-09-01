package com.leetmodel.evaluation.observability;

import com.leetmodel.evaluation.service.OnlineCorePressureGuard;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EvaluationDispatchMetrics {
    private final JdbcTemplate jdbcTemplate;
    private final OnlineCorePressureGuard pressureGuard;

    public EvaluationDispatchMetrics(ObjectProvider<MeterRegistry> registryProvider,
                                     JdbcTemplate jdbcTemplate,
                                     OnlineCorePressureGuard pressureGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.pressureGuard = pressureGuard;
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) return;
        Gauge.builder("leetmodel.evaluation.slots.waiting", this,
                        EvaluationDispatchMetrics::waiting)
                .register(registry);
        Gauge.builder("leetmodel.evaluation.slots.expired_leases", this,
                        EvaluationDispatchMetrics::expired)
                .register(registry);
        Gauge.builder("leetmodel.evaluation.batch.paused", pressureGuard,
                        guard -> guard.isPausedSnapshot() ? 1D : 0D)
                .register(registry);
    }

    private double waiting() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt "
                + "WHERE deleted = 0 AND status = 'WAITING'", Long.class);
        return count == null ? 0D : count.doubleValue();
    }

    private double expired() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM evaluation_run_attempt "
                + "WHERE deleted = 0 AND status = 'RUNNING' AND lease_expires_at < NOW()", Long.class);
        return count == null ? 0D : count.doubleValue();
    }
}
