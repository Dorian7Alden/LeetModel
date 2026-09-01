package com.leetmodel.review.service;

import com.leetmodel.review.config.ReviewWorkerProperties;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.mapper.ReviewTaskMapper;
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

/**
 * 只在存在本地执行许可时领取评审任务，并持续续租本实例任务。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "review.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReviewTaskWorkerCoordinator {

    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskWorker worker;
    private final ReviewWorkerProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final Semaphore permits;
    private final ConcurrentHashMap<Long, String> activeLeases = new ConcurrentHashMap<>();
    private final String owner = "ai-review-service:" + UUID.randomUUID();

    /**
     * 创建有界评审任务协调器。
     *
     * @param taskMapper 评审任务数据访问
     * @param worker 单任务执行器
     * @param properties Worker 配置
     * @param executor 有界线程池
     */
    public ReviewTaskWorkerCoordinator(
            ReviewTaskMapper taskMapper,
            ReviewTaskWorker worker,
            ReviewWorkerProperties properties,
            @Qualifier("reviewTaskExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.taskMapper = taskMapper;
        this.worker = worker;
        this.properties = properties;
        this.executor = executor;
        this.permits = new Semaphore(properties.getConcurrency());
    }

    /**
     * 按本实例剩余并发许可领取到期或过期租约任务。
     */
    @Scheduled(fixedDelayString = "${review.worker.poll-delay-ms:1000}")
    public void poll() {
        while (permits.tryAcquire()) {
            LocalDateTime now = LocalDateTime.now();
            ReviewTask candidate = taskMapper.selectNextClaimable(now);
            if (candidate == null) {
                permits.release();
                return;
            }
            String token = UUID.randomUUID().toString();
            int claimed = taskMapper.claim(candidate.getId(), owner, token, now,
                    now.plusSeconds(properties.getLeaseSeconds()));
            if (claimed == 0) {
                permits.release();
                continue;
            }
            activeLeases.put(candidate.getId(), token);
            submit(candidate.getId(), token);
        }
    }

    /**
     * 为本实例尚在执行的任务统一续租；进程崩溃后心跳自然停止。
     */
    @Scheduled(fixedDelayString = "${review.worker.heartbeat-ms:20000}")
    public void heartbeat() {
        LocalDateTime now = LocalDateTime.now();
        activeLeases.forEach((taskId, token) -> taskMapper.heartbeat(
                taskId, owner, token, now, now.plusSeconds(properties.getLeaseSeconds())));
    }

    private void submit(Long taskId, String token) {
        try {
            executor.execute(() -> {
                try {
                    worker.execute(taskId, owner, token);
                } finally {
                    activeLeases.remove(taskId, token);
                    permits.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            activeLeases.remove(taskId, token);
            taskMapper.releaseClaim(taskId, token);
            permits.release();
            log.warn("评审执行器拒绝任务，已释放租约: taskId={}", taskId);
        }
    }
}
