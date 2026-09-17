package com.leetmodel.assistant.tool;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 在独立有界线程池内执行工具，并同时服从工具超时和回复绝对截止时间。 */
@Service
public class AssistantToolExecutionService {

    private final Executor executor;

    public AssistantToolExecutionService(
            @Qualifier("assistantToolExecutor") Executor executor) {
        this.executor = executor;
    }

    /**
     * 执行一项已校验工具调用。
     *
     * @param prepared 已校验调用
     * @param context 可信执行上下文
     * @return 工具结果
     */
    public AssistantToolOutput execute(PreparedAssistantToolCall prepared,
                                       AssistantToolExecutionContext context) {
        // 使用单工具限制与整条回复剩余时间中的较小值
        Duration remaining = Duration.between(Instant.now(), context.deadline());
        Duration timeout = prepared.tool().descriptor().timeout().compareTo(remaining) < 0
                ? prepared.tool().descriptor().timeout() : remaining;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new AssistantToolException("TOOL_TIMEOUT", "客服工具执行超时");
        }
        CompletableFuture<AssistantToolOutput> future = CompletableFuture.supplyAsync(
                () -> prepared.tool().execute(prepared.input(), context), executor);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AssistantToolException("TOOL_TIMEOUT", "客服工具执行超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new AssistantToolException("TOOL_INTERRUPTED", "客服工具执行被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof AssistantToolException toolException) throw toolException;
            throw new AssistantToolException("TOOL_EXECUTION_FAILED", "客服工具执行失败", cause);
        }
    }
}
