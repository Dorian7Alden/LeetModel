package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.common.core.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 单实例异步等待注册表；数据库任务仍是唯一恢复事实。 */
@Component
public class AiTaskWaitRegistry {
    private final ConcurrentHashMap<String, CompletableFuture<AiCallTask>> waits = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeouts = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-result-wait-timeout");
        thread.setDaemon(true);
        return thread;
    });

    public CompletableFuture<AiCallTask> register(String taskId, Duration timeout) {
        CompletableFuture<AiCallTask> future = waits.computeIfAbsent(taskId, ignored -> new CompletableFuture<>());
        timeouts.schedule(() -> {
            if (waits.remove(taskId, future)) future.completeExceptionally(
                    new BusinessException(AiGatewayErrorCode.AI_RESULT_PENDING));
        }, Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        return future;
    }

    public void complete(AiCallTask task) {
        if (task == null) return;
        CompletableFuture<AiCallTask> future = waits.remove(task.getTaskId());
        if (future != null) future.complete(task);
    }

    public void fail(String taskId, RuntimeException exception) {
        CompletableFuture<AiCallTask> future = waits.remove(taskId);
        if (future != null) future.completeExceptionally(exception);
    }

    @PreDestroy
    void shutdown() {
        timeouts.shutdownNow();
    }
}
