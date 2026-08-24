package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.DeepSeekProperties;
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
 * DeepSeek 官方 Chat Completions 适配器。
 */
@Component
public class DeepSeekAdapter extends AbstractOpenAiCompatibleAdapter {

    /**
     * 创建 DeepSeek 适配器。
     *
     * @param builder RestClient 构建器
     * @param properties DeepSeek 配置
     */
    @Autowired
    public DeepSeekAdapter(RestClient.Builder builder, DeepSeekProperties properties) {
        super(
                builder,
                properties.getBaseUrl(),
                properties.getApiKey(),
                properties.getConnectTimeout(),
                properties.getReadTimeout()
        );
    }

    DeepSeekAdapter(RestClient restClient, String apiKey) {
        super(restClient, apiKey);
    }

    /**
     * 返回 DeepSeek 供应商。
     *
     * @return DeepSeek
     */
    @Override
    public AiProvider provider() {
        return AiProvider.DEEPSEEK;
    }

    /**
     * 构建 DeepSeek 文本请求体。
     *
     * @param model 模型标识
     * @param request 统一请求
     * @return 请求体
     */
    @Override
    protected Map<String, Object> buildChatBody(String model, AiChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", ProviderRequestMapper.toTextMessages(request.messages()));
        body.put("stream", false);
        ProviderRequestMapper.addCommonOptions(body, request);
        return body;
    }

    /**
     * 转换 DeepSeek 响应和用量。
     *
     * @param response DeepSeek 响应
     * @return 统一响应
     */
    @Override
    protected AiChatResponse toChatResponse(OpenAiCompatibleResponse response) {
        OpenAiCompatibleResponse.Choice choice = response.choices().get(0);
        OpenAiCompatibleResponse.Usage source = response.usage();
        Long reasoningTokens = source.completionTokensDetails() == null
                ? null
                : source.completionTokensDetails().reasoningTokens();
        AiUsage usage = new AiUsage(
                source.promptTokens(),
                source.promptCacheHitTokens(),
                source.promptCacheMissTokens(),
                source.completionTokens(),
                reasoningTokens,
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
