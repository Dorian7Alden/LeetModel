package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.config.AiSchedulingProperties;
import com.leetmodel.aigateway.entity.AiCallAttempt;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.observability.AiGatewayMetrics;
import com.leetmodel.aigateway.provider.AiUpstreamRateLimitException;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.logging.AiCallLogEvents;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.ExecutionSpanOperation;
import com.leetmodel.common.core.telemetry.SkyWalkingExecutionSpan;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 单实例派发器；数据库条件领取保证同一任务只有一个 owner。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.scheduling", name = "enabled", havingValue = "true")
public class AiQueueDispatcher {

    private final AiCallTaskMapper taskMapper;
    private final AiCallAttemptMapper attemptMapper;
    private final AiFairSchedulingPolicy policy;
    private final AiQueuedTaskExecutor taskExecutor;
    private final AiSchedulingProperties properties;
    private final AiRateLimitBackoff rateLimitBackoff;
    private final AiTaskWaitRegistry waitRegistry;
    private final AiGatewayMetrics metrics;
    private final String owner = "ai-gateway-" + UUID.randomUUID();
    private final AtomicInteger cursor = new AtomicInteger();
    private final Semaphore totalPermits;
    private final Semaphore nonP0Permits;
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();

    public AiQueueDispatcher(AiCallTaskMapper taskMapper, AiCallAttemptMapper attemptMapper,
                             AiFairSchedulingPolicy policy, AiQueuedTaskExecutor taskExecutor,
                             AiSchedulingProperties properties, AiRateLimitBackoff rateLimitBackoff,
                             AiTaskWaitRegistry waitRegistry, AiGatewayMetrics metrics) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.policy = policy;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
        this.rateLimitBackoff = rateLimitBackoff;
        this.waitRegistry = waitRegistry;
        this.metrics = metrics;
        this.totalPermits = new Semaphore(properties.getConcurrency());
        this.nonP0Permits = new Semaphore(Math.max(0,
                properties.getConcurrency() - properties.getReservedP0Concurrency()));
        this.workers = metrics.monitor(
                Executors.newFixedThreadPool(properties.getConcurrency()), "aiQueueWorkers");
    }

    @Scheduled(fixedDelayString = "${ai.scheduling.poll-delay-ms:50}")
    public void poll() {
        dispatchOnce();
    }

    public boolean dispatchOnce() {
        if (!rateLimitBackoff.allowDispatch()) return false;
        Instant now = Instant.now();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        List<AiCallTask> queued = taskMapper.selectQueued(500);
        queued.forEach(task -> expireIfNeeded(task, now, localNow));
        queued = queued.stream().filter(task -> !expired(task, now)).toList();
        AiFairSchedulingPolicy.SchedulingDecision decision = policy.select(queued.stream()
                .map(this::candidate).toList(), cursor.get(), now);
        if (decision.selected() == null) return false;
        AiCallTask selected = queued.stream()
                .filter(task -> task.getTaskId().equals(decision.selected().taskId())).findFirst().orElseThrow();
        boolean p0 = "P0".equals(selected.getEffectivePriority());
        if (!acquire(p0)) return false;
        LocalDateTime leaseExpiry = localNow.plus(properties.getLeaseDuration());
        if (taskMapper.claimQueued(selected.getTaskId(), selected.getVersion(), owner, leaseExpiry, localNow) != 1) {
            metrics.dispatched(selected.getEffectivePriority(), "state_conflict");
            release(p0);
            return false;
        }
        cursor.set(decision.nextCursor());
        metrics.dispatched(selected.getEffectivePriority(), "claimed");
        try {
            workers.submit(() -> executeClaimed(selected.getTaskId(), p0));
        } catch (RejectedExecutionException exception) {
            metrics.dispatched(selected.getEffectivePriority(), "executor_rejected");
            release(p0);
            throw exception;
        }
        return true;
    }

    private void executeClaimed(String taskId, boolean p0) {
        ScheduledFuture<?> heartbeatTask = null;
        AiCallAttempt attempt = null;
        SkyWalkingExecutionSpan executionSpan = null;
        String attemptState = "PREPARED";
        try {
            AiCallTask task = selectTask(taskId);
            if (Boolean.TRUE.equals(task.getCancelRequested())) {
                waitRegistry.complete(task);
                return;
            }
            attempt = attempt(task);
            executionSpan = SkyWalkingExecutionSpan.open(
                    ExecutionSpanOperation.AI_PROVIDER,
                    CorrelationSnapshot.EMPTY
                            .withTraceId(task.getTraceId())
                            .withDomainTask(task.getTaskId(), attempt.getAttemptNo())
                            .withAiCallId(task.getCallId()))
                    .attemptKind(task.getAttemptCount() != null && task.getAttemptCount() > 0)
                    .aiCallType(task.getCallType())
                    .aiPriority(task.getEffectivePriority());
            attemptMapper.insert(attempt);
            if (taskMapper.transition(taskId, task.getVersion(), "LEASED", "RUNNING", owner,
                    utcNow()) != 1) {
                attemptMapper.transition(attempt.getAttemptId(), "PREPARED", "FAILED",
                        "AI_STATE_CONFLICT", utcNow());
                executionSpan.outcome("state_conflict").error("state_conflict");
                return;
            }
            heartbeatTask = heartbeat.scheduleAtFixedRate(() -> renew(taskId),
                    properties.getHeartbeatInterval().toMillis(), properties.getHeartbeatInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (attemptMapper.transition(attempt.getAttemptId(), "PREPARED", "DISPATCHING", null,
                    utcNow()) != 1) throw new IllegalStateException("AI attempt 派发状态冲突");
            attemptState = "DISPATCHING";
            String result = taskExecutor.execute(selectTask(taskId));
            if (attemptMapper.transition(attempt.getAttemptId(), "DISPATCHING", "ACKNOWLEDGED", null,
                    utcNow()) != 1) throw new IllegalStateException("AI attempt 确认状态冲突");
            attemptState = "ACKNOWLEDGED";
            rateLimitBackoff.onSuccess();
            AiCallTask completed = selectTask(taskId);
            String terminal = Boolean.TRUE.equals(completed.getCancelRequested()) ? "CANCELLED" : "SUCCEEDED";
            if (taskMapper.completeRunning(taskId, owner, terminal,
                    terminal.equals("SUCCEEDED") ? result : null, null, utcNow()) != 1) {
                throw new IllegalStateException("AI 任务终态持久化冲突");
            }
            if (attemptMapper.transition(attempt.getAttemptId(), "ACKNOWLEDGED", "SUCCEEDED", null,
                    utcNow()) != 1) throw new IllegalStateException("AI attempt 终态持久化冲突");
            attemptState = "SUCCEEDED";
            AiCallTask terminalTask = selectTask(taskId);
            metrics.terminal(terminalTask, terminal.toLowerCase());
            waitRegistry.complete(terminalTask);
            executionSpan.outcome(terminal.toLowerCase());
        } catch (RuntimeException exception) {
            if (exception instanceof AiUpstreamRateLimitException rateLimited) {
                rateLimitBackoff.onRateLimited(rateLimited.getRetryAfter());
            }
            boolean resultUncertain = "ACKNOWLEDGED".equals(attemptState);
            String error = resultUncertain ? "AI_UPSTREAM_RESULT_UNKNOWN"
                    : "AI_EXECUTION_" + exception.getClass().getSimpleName().toUpperCase();
            taskMapper.completeRunning(taskId, owner, "FAILED", null, error, utcNow());
            if (attempt != null && !"SUCCEEDED".equals(attemptState)) {
                attemptMapper.transition(attempt.getAttemptId(), attemptState,
                        resultUncertain ? "UNKNOWN" : "FAILED", error, utcNow());
            }
            waitRegistry.fail(taskId, exception);
            metrics.terminal(safelySelectTask(taskId), resultUncertain
                    ? "upstream_result_unknown" : "failed");
            AiCallTask failed = safelySelectTask(taskId);
            if (executionSpan != null) {
                executionSpan.outcome(resultUncertain ? "upstream_result_unknown" : "failed")
                        .error(resultUncertain ? "result_unknown" : errorKind(exception));
            }
            if (resultUncertain) {
                AiCallLogEvents.resultUnknown(log,
                        failed == null ? null : failed.getCallId(),
                        failed == null ? null : failed.getCallType(),
                        failed == null ? null : failed.getEffectivePriority(),
                        taskId, attempt == null ? null : attempt.getAttemptNo());
            } else {
                AiCallLogEvents.failed(log,
                        failed == null ? null : failed.getCallId(),
                        failed == null ? null : failed.getCallType(),
                        failed == null ? null : failed.getEffectivePriority(),
                        error, 0, exception);
            }
        } finally {
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            if (executionSpan != null) executionSpan.close();
            release(p0);
        }
    }

    private boolean acquire(boolean p0) {
        if (!totalPermits.tryAcquire()) return false;
        if (!p0 && !nonP0Permits.tryAcquire()) {
            totalPermits.release();
            return false;
        }
        return true;
    }

    private void release(boolean p0) {
        if (!p0) nonP0Permits.release();
        totalPermits.release();
    }

    private void renew(String taskId) {
        LocalDateTime now = utcNow();
        taskMapper.renewRunningLease(taskId, owner, now.plus(properties.getLeaseDuration()), now);
    }

    private void expireIfNeeded(AiCallTask task, Instant now, LocalDateTime localNow) {
        if (expired(task, now)
                && taskMapper.expireBeforeDispatch(task.getTaskId(), task.getVersion(), localNow) == 1) {
            metrics.terminal(task, "expired");
        }
    }

    private boolean expired(AiCallTask task, Instant now) {
        Instant deadline = toInstant(task.getDeadline());
        Duration waiting = Duration.between(toInstant(task.getQueuedAt()), now);
        return !deadline.isAfter(now) || waiting.toMillis() > task.getMaxQueueWaitMs();
    }

    private AiFairSchedulingPolicy.Candidate candidate(AiCallTask task) {
        return new AiFairSchedulingPolicy.Candidate(task.getTaskId(),
                AiCallPriority.valueOf(task.getEffectivePriority()), toInstant(task.getQueuedAt()),
                toInstant(task.getDeadline()));
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private AiCallTask selectTask(String taskId) {
        return taskMapper.selectByTaskId(taskId);
    }

    private AiCallTask safelySelectTask(String taskId) {
        try {
            return selectTask(taskId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private AiCallAttempt attempt(AiCallTask task) {
        LocalDateTime now = utcNow();
        AiCallAttempt attempt = new AiCallAttempt();
        attempt.setAttemptId(UUID.randomUUID().toString());
        attempt.setTaskId(task.getTaskId());
        attempt.setAttemptNo(task.getAttemptCount() + 1);
        attempt.setState("PREPARED");
        attempt.setOwner(owner);
        attempt.setPreparedAt(now);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        attempt.setDeleted(0);
        return attempt;
    }

    private String errorKind(RuntimeException exception) {
        if (exception instanceof AiUpstreamRateLimitException) return "rate_limited";
        return "execution_failure";
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
        heartbeat.shutdownNow();
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
