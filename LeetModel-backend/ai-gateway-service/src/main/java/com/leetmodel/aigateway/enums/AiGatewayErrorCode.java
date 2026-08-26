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
    CAPABILITY_NOT_SUPPORTED(41102, "当前模型不支持请求的能力"),
    MODEL_DISABLED(41103, "当前模型未配置或已停用"),
    INPUT_TYPE_UNSUPPORTED(41104, "当前模型不支持请求的输入类型"),
    MEDIA_TYPE_UNSUPPORTED(41105, "当前模型不支持该媒体类型"),
    IMAGE_COUNT_EXCEEDED(41106, "图片数量超过当前模型上限"),
    IMAGE_BYTES_EXCEEDED(41107, "图片总体积超过当前模型上限"),
    CONTEXT_WINDOW_EXCEEDED(41108, "请求可能超过当前模型上下文窗口"),
    OUTPUT_LIMIT_EXCEEDED(41109, "最大输出超过当前模型上限");

    private final int code;
    private final String message;
}
