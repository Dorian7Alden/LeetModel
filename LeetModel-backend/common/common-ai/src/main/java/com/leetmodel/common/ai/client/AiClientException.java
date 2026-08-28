package com.leetmodel.common.ai.client;

import lombok.Getter;

/**
 * 业务服务调用 AI 网关失败时抛出的客户端异常。
 */
@Getter
public class AiClientException extends RuntimeException {

    private final int code;

    /**
     * 创建 AI 客户端异常。
     *
     * @param code 业务错误码
     * @param message 错误消息
     */
    public AiClientException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AiClientException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
