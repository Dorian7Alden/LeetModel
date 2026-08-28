package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.NewApiProperties;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** new-api OpenAI 兼容 Relay 适配器；不实现渠道选择或重试。 */
@Component
public class NewApiAdapter extends AbstractOpenAiCompatibleAdapter {

    @Autowired
    public NewApiAdapter(RestClient.Builder builder, NewApiProperties properties) {
        super(builder, properties.getBaseUrl(), properties.getRelayToken(),
                properties.getConnectTimeout(), properties.getReadTimeout());
    }

    NewApiAdapter(RestClient restClient, String relayToken) {
        super(restClient, relayToken);
    }

    @Override
    public AiProvider provider() {
        return AiProvider.NEW_API;
    }

    @Override
    protected Map<String, Object> buildChatBody(String model, AiChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", ProviderRequestMapper.toMultimodalMessages(request.messages()));
        body.put("stream", false);
        ProviderRequestMapper.addCommonOptions(body, request);
        return body;
    }

    @Override
    protected AiChatResponse toChatResponse(OpenAiCompatibleResponse response) {
        OpenAiCompatibleResponse.Choice choice = response.choices().get(0);
        AiUsage usage = toUsage(response.usage());
        return new AiChatResponse(null, provider(), response.model(), response.id(),
                choice.message().content(), choice.message().reasoningContent(),
                choice.finishReason(), usage);
    }

    private AiUsage toUsage(OpenAiCompatibleResponse.Usage source) {
        if (source == null) {
            return new AiUsage(null, null, null, null, null, null, null,
                    AiMetricCompleteness.UNKNOWN);
        }
        Long cacheHit = source.promptCacheHitTokens() != null
                ? source.promptCacheHitTokens() : source.cachedTokens();
        Long cacheMiss = source.promptCacheMissTokens();
        if (cacheMiss == null && cacheHit != null && source.promptTokens() != null) {
            cacheMiss = Math.max(0, source.promptTokens() - cacheHit);
        }
        Long reasoning = source.completionTokensDetails() == null
                ? null : source.completionTokensDetails().reasoningTokens();
        boolean complete = source.promptTokens() != null
                && source.completionTokens() != null && source.totalTokens() != null;
        boolean any = source.promptTokens() != null || source.completionTokens() != null
                || source.totalTokens() != null || cacheHit != null || cacheMiss != null || reasoning != null;
        AiMetricCompleteness completeness = complete ? AiMetricCompleteness.COMPLETE
                : any ? AiMetricCompleteness.PARTIAL : AiMetricCompleteness.UNKNOWN;
        return new AiUsage(source.promptTokens(), source.completionTokens(), reasoning,
                cacheHit, null, cacheMiss, source.totalTokens(), completeness);
    }
}
