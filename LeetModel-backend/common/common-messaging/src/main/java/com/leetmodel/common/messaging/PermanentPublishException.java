package com.leetmodel.common.messaging;

/**
 * 表示配置或契约错误，Outbox 应阻塞并等待人工修复。
 */
public final class PermanentPublishException extends RuntimeException {

    /**
     * 创建永久发布异常。
     *
     * @param message 失败原因
     * @param cause 根因
     */
    public PermanentPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
