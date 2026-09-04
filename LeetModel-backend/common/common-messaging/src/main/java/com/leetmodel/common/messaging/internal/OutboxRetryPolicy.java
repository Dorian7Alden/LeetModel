package com.leetmodel.common.messaging.internal;

import java.time.Duration;

/**
 * Outbox 瞬时错误退避策略。
 */
public final class OutboxRetryPolicy {

    private static final Duration[] INITIAL_DELAYS = {
            Duration.ofSeconds(1),   // 第 1 次重试间隔 1 秒
            Duration.ofSeconds(5),   // 第 2 次重试间隔 5 秒
            Duration.ofSeconds(30),  // 第 3 次重试间隔 30 秒
            Duration.ofMinutes(2),   // 第 4 次重试间隔 2 分钟
            Duration.ofMinutes(10)   // 第 5 次重试间隔 10 分钟（后续固定上限 30 分钟）
    };

    /**
     * 计算带稳定正负百分之二十抖动的退避时间。
     *
     * @param retryCount 当前失败次数
     * @param eventId 事件标识
     * @return 退避时间
     */
    public Duration delay(int retryCount, String eventId) {
        Duration base = retryCount < INITIAL_DELAYS.length
                ? INITIAL_DELAYS[retryCount]
                : Duration.ofMinutes(30);
        int bucket = Math.floorMod(eventId.hashCode(), 401);
        double factor = 0.8D + bucket / 1000D;
        return Duration.ofMillis(Math.max(100L, Math.round(base.toMillis() * factor)));
    }
}
