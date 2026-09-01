package com.leetmodel.common.core.telemetry;

import org.springframework.core.task.TaskDecorator;

/**
 * 在有界线程池中传播关联快照，并在成功或异常结束时恢复工作线程。
 */
public final class CorrelationTaskDecorator implements TaskDecorator {

    /** 无状态单例。 */
    public static final CorrelationTaskDecorator INSTANCE = new CorrelationTaskDecorator();

    private CorrelationTaskDecorator() {
    }

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
