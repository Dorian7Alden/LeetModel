package com.leetmodel.common.core.telemetry;

import org.springframework.core.task.TaskDecorator;

/**
 * 异步线程池任务装饰器。
 *
 * <p>向线程池提交任务时捕获主线程的 CorrelationSnapshot 并在子线程执行时还原，
 * 任务结束后彻底清理，解决异步线程池 MDC 丢失与复用污染难题。</p>
 */
public final class CorrelationTaskDecorator implements TaskDecorator {

    /** 无状态单例。 */
    public static final CorrelationTaskDecorator INSTANCE = new CorrelationTaskDecorator();

    private CorrelationTaskDecorator() {
    }

    /**
     * 包装待异步执行的任务，透传全链路上下文快照并在执行完成后自动清理。
     *
     * @param runnable 原始待执行的任务对象，不能为空
     * @return 注入了父线程上下文的包装任务对象
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        CorrelationSnapshot captured = CorrelationContext.capture();
        return () -> {
            try (CorrelationContext.Scope ignored = CorrelationContext.open(captured)) {
                runnable.run();
            }
        };
    }
}
