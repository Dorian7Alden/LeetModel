package com.leetmodel.common.ai.client;

import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.core.result.Result;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/**
 * 基于 HTTP 的 AI 网关客户端。
 */
public class HttpAiClient implements AiClient {

    private static final ParameterizedTypeReference<Result<AiChatResponse>> RESPONSE_TYPE =
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
        // 调用 AI 网关
        Result<AiChatResponse> result = restClient.post()
                .uri("/internal/ai/chat")
                .body(request)
                .retrieve()
                .body(RESPONSE_TYPE);

        // 转换网关错误
        if (result == null) throw new AiClientException(50001, "AI 网关未返回响应");
        if (!result.isSuccess()) throw new AiClientException(result.getCode(), result.getMessage());
        return result.getData();
    }
}
