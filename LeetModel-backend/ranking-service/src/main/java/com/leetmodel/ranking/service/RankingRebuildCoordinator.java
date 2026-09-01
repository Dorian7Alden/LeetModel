package com.leetmodel.ranking.service;

import com.leetmodel.ranking.config.RankingRebuildProperties;
import com.leetmodel.ranking.entity.RankingRebuildTask;
import com.leetmodel.ranking.mapper.RankingRebuildTaskMapper;
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
    private final Semaphore permit = new Semaphore(1);
    private final ConcurrentHashMap<Long, String> activeLeases = new ConcurrentHashMap<>();
    private final String owner = "ranking-service:" + UUID.randomUUID();

    public RankingRebuildCoordinator(
            RankingRebuildTaskMapper taskMapper,
            RankingRebuildWorker worker,
            RankingRebuildProperties properties,
            @Qualifier("rankingRebuildExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.taskMapper = taskMapper;
        this.worker = worker;
        this.properties = properties;
        this.executor = executor;
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
        activeLeases.put(candidate.getId(), token);
        try {
            executor.execute(() -> {
                try {
                    worker.execute(candidate.getId(), token);
                } finally {
                    activeLeases.remove(candidate.getId(), token);
                    permit.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            activeLeases.remove(candidate.getId(), token);
            taskMapper.scheduleRetry(candidate.getId(), token, now.plusSeconds(10),
                    "排行执行器拒绝任务", now);
            permit.release();
            log.warn("排行执行器拒绝任务: taskId={}", candidate.getId());
        }
    }

    @Scheduled(fixedDelayString = "${ranking.rebuild.heartbeat-ms:20000}")
    public void heartbeat() {
        LocalDateTime now = LocalDateTime.now();
        activeLeases.forEach((taskId, token) -> taskMapper.heartbeat(
                taskId, owner, token, now, now.plusSeconds(properties.getLeaseSeconds())));
    }
}
