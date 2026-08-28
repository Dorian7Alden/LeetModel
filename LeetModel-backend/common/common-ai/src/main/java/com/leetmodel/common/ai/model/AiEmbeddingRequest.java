package com.leetmodel.common.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 统一 Embedding 请求。逻辑模型由网关解析，调用方不得传递渠道或密钥。
 *
 * @param logicalModel 稳定的逻辑模型名
 * @param context 不含原文的业务调用上下文
 * @param inputs 单条或批量原文；具体模型可在网关配置更严格的限制
 */
public record AiEmbeddingRequest(
        @NotBlank @Size(max = 100) String logicalModel,
        @NotNull @Valid AiCallContext context,
        @NotEmpty @Size(max = MAX_BATCH_SIZE)
        List<@NotBlank @Size(max = MAX_INPUT_CHARS) String> inputs
) {
    public static final int MAX_BATCH_SIZE = 128;
    public static final int MAX_INPUT_CHARS = 32_768;

    public AiEmbeddingRequest {
        if (inputs != null) inputs = List.copyOf(inputs);
    }

    public static AiEmbeddingRequest single(String logicalModel, AiCallContext context, String input) {
        return new AiEmbeddingRequest(logicalModel, context, List.of(input));
    }
}
