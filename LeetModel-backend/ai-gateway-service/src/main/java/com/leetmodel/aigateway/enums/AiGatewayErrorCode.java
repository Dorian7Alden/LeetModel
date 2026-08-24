package com.leetmodel.aigateway.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI 网关错误码。
 */
@Getter
@AllArgsConstructor
public enum AiGatewayErrorCode implements ErrorCode {
    ROUTE_NOT_FOUND(41101, "AI 调用场景未配置"),
    PROVIDER_NOT_CONFIGURED(51101, "AI 供应商未配置"),
    PROVIDER_UNAVAILABLE(51102, "AI 供应商暂不可用"),
    RESPONSE_INVALID(51103, "AI 供应商响应无效"),
    CAPABILITY_NOT_SUPPORTED(41102, "当前模型不支持请求的内容类型");

    private final int code;
    private final String message;
}
