package com.leetmodel.common.cache.internal;

/**
 * 标记业务 Redis 当前不可用。
 */
final class RedisUnavailableException extends RuntimeException {

    /**
     * 根据底层异常创建降级信号。
     *
     * @param cause 底层异常
     */
    RedisUnavailableException(Throwable cause) {
        super(cause);
    }
}
