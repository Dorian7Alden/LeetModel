package com.leetmodel.ranking.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** 排行重建队列的固定状态、revision 延迟与过期租约指标。 */
@Component
public class RankingRebuildMetrics {
    private static final List<String> STATUSES = List.of("IDLE", "WAITING", "RUNNING");
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry registry;

    public RankingRebuildMetrics(
            ObjectProvider<MeterRegistry> registryProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        MeterRegistry registry = registryProvider.getIfAvailable();
        this.registry = registry;
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
        Gauge.builder("leetmodel.ranking.rebuild.oldest_waiting_seconds", this,
                        RankingRebuildMetrics::oldestWaitingSeconds)
                .register(registry);
    }

    public void claimed(boolean takeover) {
        if (registry == null) return;
        try {
            registry.counter("leetmodel.ranking.rebuild.claims",
                    "claim_type", takeover ? "takeover" : "normal").increment();
        } catch (RuntimeException ignored) {
            // 指标故障不得改变任务领取结果。
        }
    }

    public void attemptFinished(String status, long elapsedNanos) {
        if (registry == null) return;
        try {
            Timer.builder("leetmodel.ranking.rebuild.attempt.duration")
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
        return List.of("idle", "waiting", "running").contains(normalized)
                ? normalized : "unknown";
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

    private double oldestWaitingSeconds() {
        Long seconds = jdbcTemplate.queryForObject("""
                SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(update_time), NOW()), 0)
                FROM ranking_rebuild_task WHERE deleted = 0 AND status = 'WAITING'
                """, Long.class);
        return seconds == null ? 0D : Math.max(0D, seconds.doubleValue());
    }
}
