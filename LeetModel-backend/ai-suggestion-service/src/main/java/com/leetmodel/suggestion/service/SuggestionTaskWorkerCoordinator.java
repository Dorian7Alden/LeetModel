package com.leetmodel.suggestion.service;

import com.leetmodel.suggestion.config.SuggestionWorkerProperties;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.observability.SuggestionTaskMetrics;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "suggestion.worker", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SuggestionTaskWorkerCoordinator {
    private final SuggestionTaskMapper taskMapper;
    private final SuggestionService suggestionService;
    private final SuggestionWorkerProperties properties;
    private final ThreadPoolTaskExecutor executor;
    private final SuggestionTaskMetrics metrics;
    private final Semaphore permits;
    private final ArrayBlockingQueue<Long> signals;
    private final ConcurrentHashMap<Long, String> activeLeases = new ConcurrentHashMap<>();
    private final String owner = "ai-suggestion-service:" + UUID.randomUUID();

    public SuggestionTaskWorkerCoordinator(
            SuggestionTaskMapper taskMapper,
            SuggestionService suggestionService,
            SuggestionWorkerProperties properties,
            @Qualifier("suggestionTaskExecutor") ThreadPoolTaskExecutor executor,
            SuggestionTaskMetrics metrics
    ) {
        this.taskMapper = taskMapper;
        this.suggestionService = suggestionService;
        this.properties = properties;
        this.executor = executor;
        this.metrics = metrics;
        this.permits = new Semaphore(properties.getConcurrency());
        this.signals = new ArrayBlockingQueue<>(properties.getSignalCapacity());
    }

    /** 仅接收有界内存信号；队列满或进程退出时由数据库对账再次发布。 */
    public void wakeup(Long taskId) {
        if (taskId != null && signals.offer(taskId)) drain();
    }

    @Scheduled(fixedDelayString = "${suggestion.worker.signal-drain-ms:500}")
    public void drain() {
        while (permits.tryAcquire()) {
            Long taskId = signals.poll();
            if (taskId == null) {
                permits.release();
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            String token = UUID.randomUUID().toString();
            SuggestionTask candidate = taskMapper.selectById(taskId);
            if (taskMapper.claim(taskId, owner, token, now,
                    now.plusSeconds(properties.getLeaseSeconds())) == 0) {
                permits.release();
                continue;
            }
            boolean takeover = candidate != null
                    && ("LEASED".equals(candidate.getStatus()) || "RUNNING".equals(candidate.getStatus()));
            metrics.claimed(takeover);
            DomainTaskLogEvents.claimed(log, "suggestion", taskId,
                    candidate == null || candidate.getAttemptNo() == null
                            ? null : candidate.getAttemptNo() + 1, takeover);
            activeLeases.put(taskId, token);
            Integer attemptNo = candidate == null || candidate.getAttemptNo() == null
                    ? 1 : candidate.getAttemptNo() + 1;
            CorrelationSnapshot correlation = CorrelationSnapshot.EMPTY
                    .withTraceId(candidate == null ? null : candidate.getTraceId())
                    .withDomainTask(taskId.toString(), attemptNo)
                    .withAiCallId(candidate == null ? null : candidate.getAiCallId());
            submit(taskId, token, takeover, correlation);
        }
    }

    @Scheduled(fixedDelayString = "${suggestion.worker.heartbeat-ms:20000}")
    public void heartbeat() {
        LocalDateTime now = LocalDateTime.now();
        activeLeases.forEach((taskId, token) -> taskMapper.heartbeat(
                taskId, owner, token, now, now.plusSeconds(properties.getLeaseSeconds())));
    }

    private void submit(Long taskId, String token, boolean takeover, CorrelationSnapshot correlation) {
        try {
            executor.execute(() -> {
                long started = System.nanoTime();
                try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                        ExecutionSpanOperation.SUGGESTION_WORKER, correlation).attemptKind(takeover)) {
                    try {
                        suggestionService.executeClaimed(taskId, owner, token);
                    } finally {
                        try {
                            SuggestionTask completed = taskMapper.selectById(taskId);
                            String status = completed == null ? null : completed.getStatus();
                            metrics.attemptFinished(status, System.nanoTime() - started);
                            DomainTaskLogEvents.finished(log, "suggestion", taskId,
                                    completed == null ? null : completed.getAttemptNo(), status,
                                    System.nanoTime() - started);
                            finishSpan(span, status);
                        } catch (RuntimeException exception) {
                            span.outcome("observation_failed").error("observation");
                            log.debug("建议 attempt 指标不可用: type={}",
                                    exception.getClass().getSimpleName());
                        } finally {
                            activeLeases.remove(taskId, token);
                            permits.release();
                            drain();
                        }
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            activeLeases.remove(taskId, token);
            taskMapper.releaseClaim(taskId, token);
            permits.release();
            DomainTaskLogEvents.executorRejected(log, "suggestion", taskId);
        }
    }

    private void finishSpan(SkyWalkingExecutionSpan span, String status) {
        String outcome = status == null ? "unresolved" : status;
        span.outcome(outcome);
        if ("FAILED".equals(status)) span.error("domain_failure");
        if ("UNKNOWN".equals(status)) span.error("result_unknown");
    }
}
