package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiModelInfo;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容供应商适配器模板。
 */
public abstract class AbstractOpenAiCompatibleAdapter implements AiProviderAdapter {

    private final RestClient restClient;
    private final String apiKey;

    /**
     * 创建 OpenAI 兼容适配器。
     *
     * @param builder RestClient 构建器
     * @param baseUrl 供应商地址
     * @param apiKey API Key
     * @param connectTimeout 连接超时
     * @param readTimeout 读取超时
     */
    protected AbstractOpenAiCompatibleAdapter(
            RestClient.Builder builder,
            String baseUrl,
            String apiKey,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        this.apiKey = apiKey;
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(buildRequestFactory(connectTimeout, readTimeout))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 使用已经配置好的客户端创建适配器。
     *
     * @param restClient 已配置客户端
     * @param apiKey API Key
     */
    protected AbstractOpenAiCompatibleAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    /**
     * 调用供应商对话接口并转换统一响应。
     *
     * @param model 模型标识
     * @param request 统一请求
     * @return 统一响应
     */
    @Override
    public AiChatResponse chat(String model, AiChatRequest request) {
        // 检查供应商配置
        validateApiKey();

        // 构建供应商请求
        Map<String, Object> body = buildChatBody(model, request);

        // 调用并转换响应
        OpenAiCompatibleResponse response = executeChat(body);
        return toChatResponse(response);
    }

    /**
     * 调用供应商官方模型列表接口。
     *
     * @return 模型列表
     */
    @Override
    public List<AiModelInfo> listModels() {
        // 检查供应商配置
        validateApiKey();

        // 获取官方模型列表
        try {
            OpenAiCompatibleModelList response = restClient.get()
                    .uri("/models")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(OpenAiCompatibleModelList.class);
            BusinessException.throwIf(response == null || response.data() == null, AiGatewayErrorCode.RESPONSE_INVALID);
            return response.data().stream()
                    .map(model -> new AiModelInfo(model.id(), provider(), model.ownedBy()))
                    .toList();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    /**
     * 构建供应商请求体。
     *
     * @param model 模型标识
     * @param request 统一请求
     * @return 请求体
     */
    protected abstract Map<String, Object> buildChatBody(String model, AiChatRequest request);

    /**
     * 将供应商响应转换为统一响应。
     *
     * @param response 供应商响应
     * @return 统一响应
     */
    protected abstract AiChatResponse toChatResponse(OpenAiCompatibleResponse response);

    /**
     * 获取供应商枚举。
     *
     * @return 供应商
     */
    @Override
    public abstract AiProvider provider();

    /**
     * 调用对话接口。
     *
     * @param body 供应商请求体
     * @return 供应商响应
     */
    private OpenAiCompatibleResponse executeChat(Map<String, Object> body) {
        try {
            OpenAiCompatibleResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .body(body)
                    .retrieve()
                    .body(OpenAiCompatibleResponse.class);
            BusinessException.throwIf(response == null, AiGatewayErrorCode.RESPONSE_INVALID);
            BusinessException.throwIf(response.choices() == null || response.choices().isEmpty(), AiGatewayErrorCode.RESPONSE_INVALID);
            BusinessException.throwIf(response.usage() == null, AiGatewayErrorCode.RESPONSE_INVALID);
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpClientErrorException.BadRequest exception) {
            String responseBody = exception.getResponseBodyAsString().toLowerCase();
            if (responseBody.contains("context") && (responseBody.contains("length") || responseBody.contains("token"))) {
                throw new BusinessException(AiGatewayErrorCode.CONTEXT_WINDOW_EXCEEDED);
            }
            if (responseBody.contains("image") && (responseBody.contains("format") || responseBody.contains("media type"))) {
                throw new BusinessException(AiGatewayErrorCode.MEDIA_TYPE_UNSUPPORTED);
            }
            throw new BusinessException(AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
        } catch (RestClientException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    /**
     * 检查 API Key。
     */
    private void validateApiKey() {
        BusinessException.throwIf(!StringUtils.hasText(apiKey), AiGatewayErrorCode.PROVIDER_NOT_CONFIGURED);
    }

    /**
     * 构建 Bearer Token。
     *
     * @return Authorization 请求头内容
     */
    private String bearerToken() {
        return "Bearer " + apiKey;
    }

    /**
     * 创建带超时配置的请求工厂。
     *
     * @param connectTimeout 连接超时
     * @param readTimeout 读取超时
     * @return 请求工厂
     */
    private static JdkClientHttpRequestFactory buildRequestFactory(
            Duration connectTimeout,
            Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
