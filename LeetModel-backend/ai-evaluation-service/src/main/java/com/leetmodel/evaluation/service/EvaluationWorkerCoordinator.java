package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.config.EvaluationWorkerProperties;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.observability.EvaluationDispatchMetrics;
import com.leetmodel.common.core.logging.DomainTaskLogEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "evaluation.worker", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EvaluationWorkerCoordinator {
    private final EvaluationRunAttemptMapper runMapper;
    private final EvaluationService evaluationService;
    private final EvaluationWorkerProperties properties;
    private final OnlineCorePressureGuard pressureGuard;
    private final ThreadPoolTaskExecutor executor;
    private final EvaluationDispatchMetrics metrics;
    private final Semaphore permits;
    private final ArrayBlockingQueue<Long> signals;
    private final ConcurrentHashMap<Long, String> activeLeases = new ConcurrentHashMap<>();
    private final String owner = "ai-evaluation-service:" + UUID.randomUUID();

    public EvaluationWorkerCoordinator(EvaluationRunAttemptMapper runMapper,
                                       EvaluationService evaluationService,
                                       EvaluationWorkerProperties properties,
                                       OnlineCorePressureGuard pressureGuard,
                                       @Qualifier("evaluationTaskExecutor") ThreadPoolTaskExecutor executor,
                                       EvaluationDispatchMetrics metrics) {
        this.runMapper = runMapper;
        this.evaluationService = evaluationService;
        this.properties = properties;
        this.pressureGuard = pressureGuard;
        this.executor = executor;
        this.metrics = metrics;
        this.permits = new Semaphore(properties.getConcurrency());
        this.signals = new ArrayBlockingQueue<>(properties.getSignalCapacity());
    }

    public void wakeup(Long runAttemptId) {
        if (runAttemptId != null && signals.offer(runAttemptId)) drain();
    }

    @Scheduled(fixedDelayString = "${evaluation.worker.signal-drain-ms:500}")
    public void drain() {
        if (signals.isEmpty()) return;
        if (pressureGuard.shouldPauseBatch()) return;
        while (permits.tryAcquire()) {
            Long runId = signals.poll();
            if (runId == null) {
                permits.release();
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            String token = UUID.randomUUID().toString();
            if (runMapper.claim(runId, owner, token, now,
                    now.plusSeconds(properties.getLeaseSeconds())) == 0) {
                permits.release();
                continue;
            }
            metrics.claimed();
            EvaluationRunAttempt claimed = runMapper.selectById(runId);
            DomainTaskLogEvents.claimed(log, "evaluation", runId,
                    claimed == null ? null : claimed.getAttemptNo(), false);
            activeLeases.put(runId, token);
            submit(runId, token);
        }
    }

    @Scheduled(fixedDelayString = "${evaluation.worker.heartbeat-ms:20000}")
    public void heartbeat() {
        LocalDateTime now = LocalDateTime.now();
        activeLeases.forEach((runId, token) -> runMapper.heartbeat(
                runId, owner, token, now, now.plusSeconds(properties.getLeaseSeconds())));
    }

    private void submit(Long runId, String token) {
        try {
            executor.execute(() -> {
                long started = System.nanoTime();
                try {
                    evaluationService.executeClaimed(runId, token);
                } finally {
                    try {
                        EvaluationRunAttempt completed = runMapper.selectById(runId);
                        metrics.attemptFinished(completed == null ? null : completed.getStatus(),
                                System.nanoTime() - started);
                        DomainTaskLogEvents.finished(log, "evaluation", runId,
                                completed == null ? null : completed.getAttemptNo(),
                                completed == null ? null : completed.getStatus(),
                                System.nanoTime() - started);
                    } catch (RuntimeException exception) {
                        log.debug("评价 attempt 指标不可用: type={}",
                                exception.getClass().getSimpleName());
                    } finally {
                        activeLeases.remove(runId, token);
                        permits.release();
                        drain();
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            activeLeases.remove(runId, token);
            runMapper.releaseClaim(runId, token, LocalDateTime.now());
            permits.release();
            DomainTaskLogEvents.executorRejected(log, "evaluation", runId);
        }
    }
}
