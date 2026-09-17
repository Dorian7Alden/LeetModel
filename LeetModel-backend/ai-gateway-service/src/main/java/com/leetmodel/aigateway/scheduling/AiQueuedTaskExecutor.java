package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.entity.AiCallTask;

/** 执行已持久化的单个原子调用；实现不得自行透明重试。 */
@FunctionalInterface
public interface AiQueuedTaskExecutor {
    String execute(AiCallTask task);
}
