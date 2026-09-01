package com.leetmodel.ranking.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 排行重建队列的固定状态、revision 延迟与过期租约指标。 */
@Component
public class RankingRebuildMetrics {
    private static final List<String> STATUSES = List.of("IDLE", "WAITING", "RUNNING");
    private final JdbcTemplate jdbcTemplate;

    public RankingRebuildMetrics(
            ObjectProvider<MeterRegistry> registryProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) return;
        for (String status : STATUSES) {
            Gauge.builder("leetmodel.ranking.rebuild.tasks", this,
                            metrics -> metrics.countStatus(status))
                    .tag("status", status)
                    .register(registry);
        }
        Gauge.builder("leetmodel.ranking.rebuild.pending_revisions", this,
                        RankingRebuildMetrics::pendingRevisions)
                .register(registry);
        Gauge.builder("leetmodel.ranking.rebuild.expired_leases", this,
                        RankingRebuildMetrics::expiredLeases)
                .register(registry);
    }

    private double countStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ranking_rebuild_task WHERE deleted = 0 AND status = ?",
                Long.class, status);
        return count == null ? 0D : count.doubleValue();
    }

    private double pendingRevisions() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(requested_revision - completed_revision), 0)
                FROM ranking_rebuild_task WHERE deleted = 0
                """, Long.class);
        return count == null ? 0D : Math.max(0D, count.doubleValue());
    }

    private double expiredLeases() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ranking_rebuild_task
                WHERE deleted = 0 AND status = 'RUNNING' AND lease_expires_at < NOW()
                """, Long.class);
        return count == null ? 0D : count.doubleValue();
    }
}
