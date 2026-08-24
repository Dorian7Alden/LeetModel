package com.leetmodel.common.ai.client;

import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;

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
}
