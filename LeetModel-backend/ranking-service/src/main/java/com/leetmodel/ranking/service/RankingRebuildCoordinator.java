package com.leetmodel.ranking.service;

import com.leetmodel.ranking.config.RankingRebuildProperties;
import com.leetmodel.ranking.entity.RankingRebuildTask;
import com.leetmodel.ranking.mapper.RankingRebuildTaskMapper;
import com.leetmodel.ranking.observability.RankingRebuildMetrics;
import com.leetmodel.common.core.logging.DomainTaskLogEvents;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.ExecutionSpanOperation;
import com.leetmodel.common.core.telemetry.SkyWalkingExecutionSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/** 单实例并发 1 的排行重建协调器。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ranking.rebuild", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RankingRebuildCoordinator {
    private final RankingRebuildTaskMapper taskMapper;
    private final RankingRebuildWorker worker;
    private final RankingRebuildProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final RankingRebuildMetrics metrics;
    private final Semaphore permit = new Semaphore(1);
    private final ConcurrentHashMap<Long, String> activeLeases = new ConcurrentHashMap<>();
    private final String owner = "ranking-service:" + UUID.randomUUID();

    public RankingRebuildCoordinator(
            RankingRebuildTaskMapper taskMapper,
            RankingRebuildWorker worker,
            RankingRebuildProperties properties,
            @Qualifier("rankingRebuildExecutor") ThreadPoolTaskExecutor executor,
            RankingRebuildMetrics metrics
    ) {
        this.taskMapper = taskMapper;
        this.worker = worker;
        this.properties = properties;
        this.executor = executor;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${ranking.rebuild.poll-delay-ms:1000}")
    public void poll() {
        if (!permit.tryAcquire()) return;
        LocalDateTime now = LocalDateTime.now();
        RankingRebuildTask candidate = taskMapper.selectNextClaimable(now);
        if (candidate == null) {
            permit.release();
            return;
        }
        String token = UUID.randomUUID().toString();
        int claimed = taskMapper.claim(candidate.getId(), owner, token, now,
                now.plusSeconds(properties.getLeaseSeconds()));
        if (claimed == 0) {
            permit.release();
            return;
        }
        boolean takeover = "RUNNING".equals(candidate.getStatus());
        metrics.claimed(takeover);
        DomainTaskLogEvents.claimed(log, "ranking_rebuild", candidate.getId(),
                candidate.getRetryCount() == null ? null : candidate.getRetryCount() + 1, takeover);
        activeLeases.put(candidate.getId(), token);
        Integer attemptNo = candidate.getRetryCount() == null ? 1 : candidate.getRetryCount() + 1;
        CorrelationSnapshot correlation = CorrelationSnapshot.EMPTY
                .withTraceId(candidate.getTraceId())
                .withDomainTask(candidate.getId().toString(), attemptNo);
        try {
            executor.execute(() -> {
                long started = System.nanoTime();
                try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                        ExecutionSpanOperation.RANKING_REBUILD_WORKER, correlation).attemptKind(takeover)) {
                    try {
                        worker.execute(candidate.getId(), token);
                    } finally {
                        try {
                            RankingRebuildTask completed = taskMapper.selectById(candidate.getId());
                            String status = completed == null ? null : completed.getStatus();
                            metrics.attemptFinished(status, System.nanoTime() - started);
                            DomainTaskLogEvents.finished(log, "ranking_rebuild", candidate.getId(),
                                    completed == null || completed.getRetryCount() == null
                                            ? null : completed.getRetryCount() + 1,
                                    status, System.nanoTime() - started);
                            span.outcome(status == null ? "unresolved" : status);
                            if ("FAILED".equals(status)) span.error("domain_failure");
                        } catch (RuntimeException exception) {
                            span.outcome("observation_failed").error("observation");
                            log.debug("排行 attempt 指标不可用: type={}",
                                    exception.getClass().getSimpleName());
                        } finally {
                            activeLeases.remove(candidate.getId(), token);
                            permit.release();
                        }
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            activeLeases.remove(candidate.getId(), token);
            taskMapper.scheduleRetry(candidate.getId(), token, now.plusSeconds(10),
                    "排行执行器拒绝任务", now);
            permit.release();
            DomainTaskLogEvents.executorRejected(log, "ranking_rebuild", candidate.getId());
        }
    }

    @Scheduled(fixedDelayString = "${ranking.rebuild.heartbeat-ms:20000}")
    public void heartbeat() {
        LocalDateTime now = LocalDateTime.now();
        activeLeases.forEach((taskId, token) -> taskMapper.heartbeat(
                taskId, owner, token, now, now.plusSeconds(properties.getLeaseSeconds())));
    }
}
