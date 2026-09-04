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
 * 基于 Spring RestClient 实现的 HTTP AI 网关客户端。
 *
 * <p>负责将统一请求通过内网 HTTP 发送给 ai-gateway-service，
 * 并将 HTTP 传输异常与业务错误统一转换为 AiClientException。</p>
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
     * 构造基于 RestClient 的 HTTP AI 客户端。
     *
     * @param restClient 已配置目标基础 URL 与超时参数的 RestClient 实例，不能为空
     */
    public HttpAiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 发起同步 AI 对话 HTTP 调用。
     *
     * @param request 统一对话请求对象，不能为空
     * @return 统一对话响应对象
     * @throws AiClientException 当网络故障、超时或网关返回非成功响应时抛出
     */
    @Override
    public AiChatResponse chat(AiChatRequest request) {
        try {
            Result<AiChatResponse> result = restClient.post()
                    .uri("/internal/ai/chat")
                    .body(request)
                    .retrieve()
                    .body(CHAT_RESPONSE_TYPE);
            return requireData(result);
        } catch (RestClientResponseException exception) {
            throw transportFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(50002, "AI 网关调用超时或不可用", exception);
        }
    }

    /**
     * 发起同步文本向量嵌入 HTTP 调用。
     *
     * @param request 统一 Embedding 请求对象，不能为空
     * @return 统一 Embedding 响应对象
     * @throws AiClientException 当网络故障、超时或网关返回非成功响应时抛出
     */
    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        try {
            Result<AiEmbeddingResponse> result = restClient.post()
                    .uri("/internal/ai/embeddings")
                    .body(request)
                    .retrieve()
                    .body(EMBEDDING_RESPONSE_TYPE);
            return requireData(result);
        } catch (RestClientResponseException exception) {
            throw transportFailure(exception);
        } catch (ResourceAccessException exception) {
            throw new AiClientException(50002, "AI 网关调用超时或不可用", exception);
        }
    }

    /**
     * 提取 Result 包装器中的数据载荷，并在失败时转换为客户端异常。
     *
     * @param result 网关返回的统一 Result 对象
     * @param <T>    响应数据类型
     * @return 解包后的非空数据对象
     * @throws AiClientException 当响应为空、业务 code 非 0 或 data 为 null 时抛出
     */
    private <T> T requireData(Result<T> result) {
        if (result == null) throw new AiClientException(50001, "AI 网关未返回响应");
        if (!result.isSuccess()) throw new AiClientException(result.getCode(), result.getMessage());
        if (result.getData() == null) throw new AiClientException(50001, "AI 网关返回空数据");
        return result.getData();
    }

    /**
     * 将 RestClient 底层 HTTP 状态码异常包装为标准 AiClientException。
     *
     * @param exception 捕获的 RestClientResponseException 异常对象
     * @return 格式化了 HTTP 状态码的统一客户端异常实例
     */
    private AiClientException transportFailure(RestClientResponseException exception) {
        return new AiClientException(50002,
                "AI 网关 HTTP 调用失败: " + exception.getStatusCode().value(), exception);
    }
}
