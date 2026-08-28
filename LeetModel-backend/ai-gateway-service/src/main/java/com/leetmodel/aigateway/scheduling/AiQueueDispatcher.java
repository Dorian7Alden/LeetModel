package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.config.AiSchedulingProperties;
import com.leetmodel.aigateway.entity.AiCallAttempt;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.common.ai.model.AiCallPriority;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 单实例派发器；数据库条件领取保证同一任务只有一个 owner。 */
@Component
@ConditionalOnBean(AiQueuedTaskExecutor.class)
@ConditionalOnProperty(prefix = "ai.scheduling", name = "enabled", havingValue = "true")
public class AiQueueDispatcher {

    private final AiCallTaskMapper taskMapper;
    private final AiCallAttemptMapper attemptMapper;
    private final AiFairSchedulingPolicy policy;
    private final AiQueuedTaskExecutor taskExecutor;
    private final AiSchedulingProperties properties;
    private final String owner = "ai-gateway-" + UUID.randomUUID();
    private final AtomicInteger cursor = new AtomicInteger();
    private final Semaphore totalPermits;
    private final Semaphore nonP0Permits;
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();

    public AiQueueDispatcher(AiCallTaskMapper taskMapper, AiCallAttemptMapper attemptMapper,
                             AiFairSchedulingPolicy policy, AiQueuedTaskExecutor taskExecutor,
                             AiSchedulingProperties properties) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.policy = policy;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
        this.totalPermits = new Semaphore(properties.getConcurrency());
        this.nonP0Permits = new Semaphore(Math.max(0,
                properties.getConcurrency() - properties.getReservedP0Concurrency()));
        this.workers = Executors.newFixedThreadPool(properties.getConcurrency());
    }

    @Scheduled(fixedDelayString = "${ai.scheduling.poll-delay-ms:50}")
    public void poll() {
        dispatchOnce();
    }

    public boolean dispatchOnce() {
        Instant now = Instant.now();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
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
            release(p0);
            return false;
        }
        cursor.set(decision.nextCursor());
        workers.submit(() -> executeClaimed(selected.getTaskId(), p0));
        return true;
    }

    private void executeClaimed(String taskId, boolean p0) {
        ScheduledFuture<?> heartbeatTask = null;
        AiCallAttempt attempt = null;
        try {
            AiCallTask task = selectTask(taskId);
            if (Boolean.TRUE.equals(task.getCancelRequested())) return;
            attempt = attempt(task);
            attemptMapper.insert(attempt);
            if (taskMapper.transition(taskId, task.getVersion(), "LEASED", "RUNNING", owner,
                    LocalDateTime.now()) != 1) {
                attemptMapper.transition(attempt.getAttemptId(), "PREPARED", "FAILED",
                        "AI_STATE_CONFLICT", LocalDateTime.now());
                return;
            }
            heartbeatTask = heartbeat.scheduleAtFixedRate(() -> renew(taskId),
                    properties.getHeartbeatInterval().toMillis(), properties.getHeartbeatInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
            attemptMapper.transition(attempt.getAttemptId(), "PREPARED", "DISPATCHING", null,
                    LocalDateTime.now());
            String result = taskExecutor.execute(selectTask(taskId));
            AiCallTask completed = selectTask(taskId);
            String terminal = Boolean.TRUE.equals(completed.getCancelRequested()) ? "CANCELLED" : "SUCCEEDED";
            taskMapper.completeRunning(taskId, owner, terminal, terminal.equals("SUCCEEDED") ? result : null,
                    null, LocalDateTime.now());
            attemptMapper.transition(attempt.getAttemptId(), "DISPATCHING", "SUCCEEDED", null,
                    LocalDateTime.now());
        } catch (RuntimeException exception) {
            String error = "AI_EXECUTION_" + exception.getClass().getSimpleName().toUpperCase();
            taskMapper.completeRunning(taskId, owner, "FAILED", null, error, LocalDateTime.now());
            if (attempt != null) attemptMapper.transition(attempt.getAttemptId(), "DISPATCHING", "FAILED",
                    error, LocalDateTime.now());
        } finally {
            if (heartbeatTask != null) heartbeatTask.cancel(false);
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
        LocalDateTime now = LocalDateTime.now();
        taskMapper.renewRunningLease(taskId, owner, now.plus(properties.getLeaseDuration()), now);
    }

    private void expireIfNeeded(AiCallTask task, Instant now, LocalDateTime localNow) {
        if (expired(task, now)) taskMapper.expireBeforeDispatch(task.getTaskId(), task.getVersion(), localNow);
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
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private AiCallTask selectTask(String taskId) {
        return taskMapper.selectByTaskId(taskId);
    }

    private AiCallAttempt attempt(AiCallTask task) {
        LocalDateTime now = LocalDateTime.now();
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

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
        heartbeat.shutdownNow();
    }
}
