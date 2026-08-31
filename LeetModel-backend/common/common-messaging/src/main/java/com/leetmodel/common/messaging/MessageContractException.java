package com.leetmodel.common.messaging;

/**
 * 表示消息不符合发布契约，重试不会自动修复。
 */
public final class MessageContractException extends RuntimeException {

    /**
     * 创建契约异常。
     *
     * @param message 失败原因
     */
    public MessageContractException(String message) {
        super(message);
    }

    /**
     * 创建带根因的契约异常。
     *
     * @param message 失败原因
     * @param cause 根因
     */
    public MessageContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
