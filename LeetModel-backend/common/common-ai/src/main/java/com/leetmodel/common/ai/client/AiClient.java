package com.leetmodel.common.ai.client;

import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;

/**
 * 业务服务使用的统一 AI 客户端。
 */
public interface AiClient {

    /**
     * 发起同步 AI 对话。
     *
     * @param request 统一对话请求
     * @return 统一对话响应
     */
    AiChatResponse chat(AiChatRequest request);

    /** 发起同步 Embedding 调用；Chat-only 的旧实现保持源码兼容。 */
    default AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        throw new AiClientException(50003, "当前 AI 客户端不支持 Embedding");
    }
}
