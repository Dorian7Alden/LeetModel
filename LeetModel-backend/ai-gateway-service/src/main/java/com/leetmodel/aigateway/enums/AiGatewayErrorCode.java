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
    ROUTE_NOT_FOUND(41201, "AI 调用场景未配置"),
    PROVIDER_NOT_CONFIGURED(51201, "AI 供应商未配置"),
    PROVIDER_UNAVAILABLE(51202, "AI 供应商暂不可用"),
    RESPONSE_INVALID(51203, "AI 供应商响应无效"),
    PROVIDER_TIMEOUT(51204, "AI 模型调用超时，结果状态未知"),
    UPSTREAM_AUTHENTICATION_FAILED(51205, "AI 上游认证失败"),
    UPSTREAM_QUOTA_EXCEEDED(51206, "AI 上游额度不足"),
    UPSTREAM_RATE_LIMITED(51207, "AI 上游请求受限"),
    UPSTREAM_MODEL_NOT_FOUND(51208, "AI 上游模型不存在"),
    CAPABILITY_NOT_SUPPORTED(41202, "当前模型不支持请求的能力"),
    MODEL_DISABLED(41203, "当前模型未配置或已停用"),
    INPUT_TYPE_UNSUPPORTED(41204, "当前模型不支持请求的输入类型"),
    MEDIA_TYPE_UNSUPPORTED(41205, "当前模型不支持该媒体类型"),
    IMAGE_COUNT_EXCEEDED(41206, "图片数量超过当前模型上限"),
    IMAGE_BYTES_EXCEEDED(41207, "图片总体积超过当前模型上限"),
    CONTEXT_WINDOW_EXCEEDED(41208, "请求可能超过当前模型上下文窗口"),
    OUTPUT_LIMIT_EXCEEDED(41209, "最大输出超过当前模型上限"),
    EMBEDDING_BATCH_EXCEEDED(41210, "Embedding 批量超过逻辑模型上限"),
    EMBEDDING_INPUT_EXCEEDED(41211, "Embedding 单条输入超过逻辑模型上限"),
    EMBEDDING_TOTAL_INPUT_EXCEEDED(41212, "Embedding 输入总量超过逻辑模型上限"),
    EMBEDDING_DIMENSION_MISMATCH(51209, "Embedding 响应维度与模型配置不一致");

    private final int code;
    private final String message;
}
