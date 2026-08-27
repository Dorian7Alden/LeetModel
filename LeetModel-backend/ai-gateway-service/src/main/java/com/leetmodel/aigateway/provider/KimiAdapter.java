package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.KimiProperties;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kimi 官方 Chat Completions 适配器。
 */
@Component
public class KimiAdapter extends AbstractOpenAiCompatibleAdapter {

    /**
     * 创建 Kimi 适配器。
     *
     * @param builder RestClient 构建器
     * @param properties Kimi 配置
     */
    @Autowired
    public KimiAdapter(RestClient.Builder builder, KimiProperties properties) {
        super(
                builder,
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getConnectTimeout(),
                properties.getReadTimeout()
        );
    }

    KimiAdapter(RestClient restClient, String apiKey) {
        super(restClient, apiKey);
    }

    /**
     * 返回 Kimi 供应商。
     *
     * @return Kimi
     */
    @Override
    public AiProvider provider() {
        return AiProvider.KIMI;
    }

    /**
     * 构建 Kimi 多模态请求体。
     *
     * @param model 模型标识
     * @param request 统一请求
     * @return 请求体
     */
    @Override
    protected Map<String, Object> buildChatBody(String model, AiChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", ProviderRequestMapper.toMultimodalMessages(request.messages()));
        body.put("stream", false);
        ProviderRequestMapper.addCommonOptions(body, request);
        return body;
    }

    /**
     * 转换 Kimi 响应和用量。
     *
     * @param response Kimi 响应
     * @return 统一响应
     */
    @Override
    protected AiChatResponse toChatResponse(OpenAiCompatibleResponse response) {
        OpenAiCompatibleResponse.Choice choice = response.choices().get(0);
        OpenAiCompatibleResponse.Usage source = response.usage();
        Long cacheHitTokens = source.cachedTokens();
        Long cacheMissTokens = cacheHitTokens == null || source.promptTokens() == null
                ? null
                : Math.max(0, source.promptTokens() - cacheHitTokens);
        AiUsage usage = new AiUsage(
                source.promptTokens(),
                cacheHitTokens,
                cacheMissTokens,
                source.completionTokens(),
                null,
                source.totalTokens(),
                true
        );
        return new AiChatResponse(
                null,
                provider(),
                response.model(),
                response.id(),
                choice.message().content(),
                choice.message().reasoningContent(),
                choice.finishReason(),
                usage
        );
    }
}
