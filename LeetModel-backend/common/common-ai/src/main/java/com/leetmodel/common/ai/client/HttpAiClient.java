package com.leetmodel.common.ai.client;

import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.core.result.Result;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 基于 HTTP 的 AI 网关客户端。
 */
public class HttpAiClient implements AiClient {

    private static final ParameterizedTypeReference<Result<AiChatResponse>> CHAT_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<Result<AiEmbeddingResponse>> EMBEDDING_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    /**
     * 创建 HTTP AI 客户端。
     *
     * @param restClient AI 网关 RestClient
     */
    public HttpAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 发起同步 AI 对话。
     *
     * @param request 统一对话请求
     * @return 统一对话响应
     */
    @Override
    public AiChatResponse chat(AiChatRequest request) {
        try {
            return requireData(restClient.post().uri("/internal/ai/chat").body(request)
                    .retrieve().body(CHAT_RESPONSE_TYPE));
        } catch (RestClientResponseException exception) {
            throw transportFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(50002, "AI 网关调用超时或不可用", exception);
        }
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        try {
            return requireData(restClient.post().uri("/internal/ai/embeddings").body(request)
                    .retrieve().body(EMBEDDING_RESPONSE_TYPE));
        } catch (RestClientResponseException exception) {
            throw transportFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(50002, "AI 网关调用超时或不可用", exception);
        }
    }

    private <T> T requireData(Result<T> result) {
        if (result == null) throw new AiClientException(50001, "AI 网关未返回响应");
        if (!result.isSuccess()) throw new AiClientException(result.getCode(), result.getMessage());
        if (result.getData() == null) throw new AiClientException(50001, "AI 网关返回空数据");
        return result.getData();
    }

    private AiClientException transportFailure(RestClientResponseException exception) {
        return new AiClientException(50002,
                "AI 网关 HTTP 调用失败: " + exception.getStatusCode().value(), exception);
    }
}
