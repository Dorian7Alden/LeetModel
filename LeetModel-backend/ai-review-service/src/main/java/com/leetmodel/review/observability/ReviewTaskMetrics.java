package com.leetmodel.review.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 正式评审领域队列的低基数状态、等待和过期租约指标。
 */
@Component
public class ReviewTaskMetrics {

    private static final List<String> STATUSES = List.of(
            "WAITING", "LEASED", "RUNNING", "COMPLETED", "FAILED", "UNKNOWN");

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry registry;

    /**
     * 注册固定状态集合的评审任务指标。
     *
     * @param registryProvider 可选指标注册表
     * @param jdbcTemplate 评审数据库访问器
     */
    public ReviewTaskMetrics(
            ObjectProvider<MeterRegistry> registryProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        MeterRegistry registry = registryProvider.getIfAvailable();
        this.registry = registry;
        if (registry == null) return;
        for (String status : STATUSES) {
            Gauge.builder("leetmodel.review.tasks", this,
                            metrics -> metrics.countStatus(status))
                    .tag("status", status)
                    .description("Review domain tasks by stable status")
                    .register(registry);
        }
        Gauge.builder("leetmodel.review.tasks.expired_leases", this,
                        ReviewTaskMetrics::countExpiredLeases)
                .description("Review tasks whose active lease has expired")
                .register(registry);
        Gauge.builder("leetmodel.review.tasks.oldest_waiting_seconds", this,
                        ReviewTaskMetrics::oldestWaitingSeconds)
                .description("Age of the oldest due review task")
                .register(registry);
    }

    /** 记录普通领取或过期租约接管。 */
    public void claimed(boolean takeover) {
        if (registry == null) return;
        try {
            registry.counter("leetmodel.review.task.claims",
                    "claim_type", takeover ? "takeover" : "normal").increment();
        } catch (RuntimeException ignored) {
            // 指标故障不得改变任务领取结果。
        }
    }

    /** 记录一次物理 attempt 的执行时间与结束状态。 */
    public void attemptFinished(String status, long elapsedNanos) {
        if (registry == null) return;
        try {
            Timer.builder("leetmodel.review.task.attempt.duration")
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
        return List.of("waiting", "leased", "running", "completed", "failed", "unknown")
                .contains(normalized) ? normalized : "unknown";
    }

    private double countStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_task WHERE deleted = 0 AND status = ?",
                Long.class, status);
        return count == null ? 0D : count.doubleValue();
    }

    private double countExpiredLeases() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM review_task
                WHERE deleted = 0 AND status IN ('LEASED', 'RUNNING') AND lease_expires_at < NOW()
                """, Long.class);
        return count == null ? 0D : count.doubleValue();
    }

    private double oldestWaitingSeconds() {
        Long seconds = jdbcTemplate.queryForObject("""
                SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(create_time), NOW()), 0)
                FROM review_task WHERE deleted = 0 AND status = 'WAITING' AND next_run_at <= NOW()
                """, Long.class);
        return seconds == null ? 0D : Math.max(0D, seconds.doubleValue());
    }
}
