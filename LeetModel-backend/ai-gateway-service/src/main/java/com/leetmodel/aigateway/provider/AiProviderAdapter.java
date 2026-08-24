package com.leetmodel.aigateway.provider;

import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiModelInfo;
import com.leetmodel.common.ai.model.AiProvider;

import java.util.List;

/**
 * AI 供应商适配器。
 */
public interface AiProviderAdapter {

    /**
     * 返回适配器对应的供应商。
     *
     * @return 供应商
     */
    AiProvider provider();

    /**
     * 调用供应商对话接口。
     *
     * @param model 模型标识
     * @param request 统一请求
     * @return 统一响应
     */
    AiChatResponse chat(String model, AiChatRequest request);

    /**
     * 调用供应商官方模型列表接口。
     *
     * @return 模型列表
     */
    List<AiModelInfo> listModels();
}
