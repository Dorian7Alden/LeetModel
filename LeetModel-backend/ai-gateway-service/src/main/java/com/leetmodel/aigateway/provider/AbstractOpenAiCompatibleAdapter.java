package com.leetmodel.aigateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.aigateway.config.AiApiProtocol;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiChatStreamChunk;
import com.leetmodel.common.ai.model.AiEmbeddingVector;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import com.leetmodel.common.ai.model.AiModelInfo;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

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
    public AiChatResponse streamChat(String model, AiApiProtocol protocol, AiChatRequest request,
                                     Consumer<AiChatStreamChunk> onChunk) {
        validateApiKey();
        if (protocol != AiApiProtocol.OPENAI_COMPLETIONS) {
            return chat(model, protocol, request);
        }

        Map<String, Object> body = buildChatBody(model, request);
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));

        ObjectMapper mapper = new ObjectMapper();
        StringBuilder fullContent = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        final String[] responseId = {null};
        final String[] responseModel = {model};
        final String[] finalFinishReason = {null};
        final OpenAiCompatibleResponse.Usage[] finalUsage = {null};
        Map<Integer, StreamToolCallAccumulator> streamedToolCalls = new TreeMap<>();

        try {
            restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().isError()) {
                            throw mapHttpError(new RestClientResponseException(
                                    "Upstream stream error", clientResponse.getStatusCode(),
                                    clientResponse.getStatusText(), clientResponse.getHeaders(),
                                    null, null));
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(clientResponse.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                if (!line.startsWith("data:")) continue;
                                String dataStr = line.substring(5).trim();
                                if (dataStr.isEmpty() || "[DONE]".equals(dataStr)) continue;

                                try {
                                    OpenAiCompatibleStreamChunk chunk = mapper.readValue(
                                            dataStr, OpenAiCompatibleStreamChunk.class);
                                    if (chunk.id() != null) responseId[0] = chunk.id();
                                    if (chunk.model() != null) responseModel[0] = chunk.model();
                                    if (chunk.usage() != null) finalUsage[0] = chunk.usage();

                                    if (chunk.choices() != null && !chunk.choices().isEmpty()) {
                                        OpenAiCompatibleStreamChunk.Choice choice = chunk.choices().get(0);
                                        if (choice.finishReason() != null) {
                                            finalFinishReason[0] = choice.finishReason();
                                        }
                                        if (choice.delta() != null) {
                                            String deltaContent = choice.delta().content();
                                            if (deltaContent != null && !deltaContent.isEmpty()) {
                                                fullContent.append(deltaContent);
                                                if (onChunk != null) {
                                                    onChunk.accept(new AiChatStreamChunk(
                                                            responseId[0], deltaContent, null, null));
                                                }
                                            }
                                            String reasoning = choice.delta().reasoningContent();
                                            if (reasoning != null && !reasoning.isEmpty()) {
                                                fullReasoning.append(reasoning);
                                            }
                                            mergeToolCallDeltas(streamedToolCalls,
                                                    choice.delta().toolCalls());
                                        }
                                    }
                                } catch (Exception parseErr) {
                                    // 忽略单行解析错误
                                }
                            }
                        } catch (BusinessException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
                        }
                        return null;
                    });

            OpenAiCompatibleResponse.Choice mockChoice = new OpenAiCompatibleResponse.Choice(
                    new OpenAiCompatibleResponse.Message(
                            fullContent.length() > 0 ? fullContent.toString() : null,
                            fullReasoning.length() > 0 ? fullReasoning.toString() : null,
                            completedToolCalls(streamedToolCalls)
                    ),
                    finalFinishReason[0] != null ? finalFinishReason[0] : "stop"
            );
            OpenAiCompatibleResponse fullResponse = new OpenAiCompatibleResponse(
                    responseId[0],
                    responseModel[0],
                    List.of(mockChoice),
                    finalUsage[0]
            );
            AiChatResponse unified = toChatResponse(fullResponse);
            if (onChunk != null) {
                onChunk.accept(new AiChatStreamChunk(
                        unified.callId(), null, unified.finishReason(), unified));
            }
            return unified;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_TIMEOUT);
        } catch (Exception exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private void mergeToolCallDeltas(
            Map<Integer, StreamToolCallAccumulator> accumulators,
            List<OpenAiCompatibleStreamChunk.ToolCall> deltas
    ) {
        if (deltas == null || deltas.isEmpty()) return;
        for (int position = 0; position < deltas.size(); position++) {
            OpenAiCompatibleStreamChunk.ToolCall delta = deltas.get(position);
            if (delta == null) continue;
            int index = delta.index() == null ? position : delta.index();
            StreamToolCallAccumulator accumulator = accumulators.computeIfAbsent(
                    index, ignored -> new StreamToolCallAccumulator());
            accumulator.append(delta);
        }
    }

    private List<OpenAiCompatibleResponse.ToolCall> completedToolCalls(
            Map<Integer, StreamToolCallAccumulator> accumulators
    ) {
        if (accumulators.isEmpty()) return null;
        return accumulators.values().stream()
                .map(StreamToolCallAccumulator::toToolCall)
                .toList();
    }

    private static final class StreamToolCallAccumulator {

        private String id;
        private String type;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();

        private void append(OpenAiCompatibleStreamChunk.ToolCall delta) {
            if (StringUtils.hasText(delta.id())) id = delta.id();
            if (StringUtils.hasText(delta.type())) type = delta.type();
            if (delta.function() == null) return;
            if (delta.function().name() != null) name.append(delta.function().name());
            if (delta.function().arguments() != null) {
                arguments.append(delta.function().arguments());
            }
        }

        private OpenAiCompatibleResponse.ToolCall toToolCall() {
            return new OpenAiCompatibleResponse.ToolCall(
                    id,
                    type,
                    new OpenAiCompatibleResponse.Function(
                            name.toString(),
                            arguments.toString()
                    )
            );
        }
    }

    @Override
    public AiChatResponse chat(String model, AiApiProtocol protocol, AiChatRequest request) {
        // 检查供应商配置
        validateApiKey();
        BusinessException.throwIf(
                usesToolProtocol(request) && protocol != AiApiProtocol.OPENAI_COMPLETIONS,
                AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED
        );

        return switch (protocol) {
            case OPENAI_COMPLETIONS -> {
                Map<String, Object> body = buildChatBody(model, request);
                yield toChatResponse(executeChat(body));
            }
            case OPENAI_RESPONSES -> executeResponses(model, request);
            case ANTHROPIC_MESSAGES -> executeAnthropic(model, request);
        };
    }

    /**
     * 判断请求是否包含工具协议字段。
     *
     * @param request 统一请求
     * @return 是否需要工具协议
     */
    private boolean usesToolProtocol(AiChatRequest request) {
        if (request.tools() != null && !request.tools().isEmpty()) return true;
        return request.messages().stream().anyMatch(message ->
                message.role() == com.leetmodel.common.ai.model.AiRole.TOOL
                        || message.toolCalls() != null && !message.toolCalls().isEmpty());
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

    @Override
    public ProviderEmbeddingResponse embed(String model, List<String> inputs) {
        validateApiKey();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("input", inputs);
        body.put("encoding_format", "float");
        try {
            OpenAiEmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .body(body)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);
            validateEmbedding(response, inputs.size());
            List<AiEmbeddingVector> vectors = response.data().stream()
                    .map(item -> new AiEmbeddingVector(item.index(), item.embedding())).toList();
            return new ProviderEmbeddingResponse(response.model(), response.id(), vectors,
                    embeddingUsage(response.usage()), null);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_TIMEOUT);
        } catch (RestClientException exception) {
            throw new BusinessException(AiGatewayErrorCode.RESPONSE_INVALID);
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
            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_TIMEOUT);
        } catch (RestClientException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private AiChatResponse executeResponses(String model, AiChatRequest request) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("input", ProviderRequestMapper.toResponsesInput(request.messages()));
        ProviderRequestMapper.addResponsesOptions(body, request);
        try {
            OpenAiResponsesResponse response = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .body(body)
                    .retrieve()
                    .body(OpenAiResponsesResponse.class);
            validateResponses(response);
            return response.toUnified(provider());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_TIMEOUT);
        } catch (RestClientException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private AiChatResponse executeAnthropic(String model, AiChatRequest request) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        String system = ProviderRequestMapper.anthropicSystem(request.messages());
        if (StringUtils.hasText(system)) body.put("system", system);
        body.put("messages", ProviderRequestMapper.toAnthropicMessages(request.messages()));
        ProviderRequestMapper.addAnthropicOptions(body, request);
        try {
            AnthropicMessagesResponse response = restClient.post()
                    .uri("/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .body(body)
                    .retrieve()
                    .body(AnthropicMessagesResponse.class);
            validateAnthropic(response);
            return response.toUnified(provider());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_TIMEOUT);
        } catch (RestClientException exception) {
            throw new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
    }

    private void validateResponses(OpenAiResponsesResponse response) {
        BusinessException.throwIf(response == null || response.output() == null || response.usage() == null,
                AiGatewayErrorCode.RESPONSE_INVALID);
        BusinessException.throwIf(!StringUtils.hasText(response.outputText()), AiGatewayErrorCode.RESPONSE_INVALID);
    }

    private void validateAnthropic(AnthropicMessagesResponse response) {
        BusinessException.throwIf(response == null || response.content() == null || response.usage() == null,
                AiGatewayErrorCode.RESPONSE_INVALID);
        BusinessException.throwIf(!StringUtils.hasText(response.outputText()), AiGatewayErrorCode.RESPONSE_INVALID);
    }

    private void validateEmbedding(OpenAiEmbeddingResponse response, int expectedCount) {
        BusinessException.throwIf(response == null || !StringUtils.hasText(response.model())
                        || response.data() == null || response.data().size() != expectedCount,
                AiGatewayErrorCode.RESPONSE_INVALID);
        for (OpenAiEmbeddingResponse.Item item : response.data()) {
            boolean invalid = item == null || item.embedding() == null || item.embedding().isEmpty()
                    || item.embedding().stream().anyMatch(value -> value == null || !Float.isFinite(value));
            BusinessException.throwIf(invalid, AiGatewayErrorCode.RESPONSE_INVALID);
        }
    }

    private AiUsage embeddingUsage(OpenAiEmbeddingResponse.Usage usage) {
        if (usage == null) {
            return new AiUsage(null, null, null, null, null, null, null,
                    AiMetricCompleteness.UNKNOWN);
        }
        if (usage.promptTokens() == null || usage.totalTokens() == null) {
            return new AiUsage(usage.promptTokens(), 0L, null, null, null, null,
                    usage.totalTokens(), AiMetricCompleteness.PARTIAL);
        }
        return new AiUsage(usage.promptTokens(), 0L, null, null, null, null,
                usage.totalTokens(), AiMetricCompleteness.COMPLETE);
    }

    private BusinessException mapHttpError(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString().toLowerCase();
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403 && responseBody.contains("token")) {
            return new BusinessException(AiGatewayErrorCode.UPSTREAM_AUTHENTICATION_FAILED);
        }
        if (status == 429) {
            return new AiUpstreamRateLimitException(retryAfter(exception));
        }
        if (responseBody.contains("model_not_found") || responseBody.contains("model not found")) {
            return new BusinessException(AiGatewayErrorCode.UPSTREAM_MODEL_NOT_FOUND);
        }
        if (responseBody.contains("quota") || responseBody.contains("insufficient")
                || responseBody.contains("额度")) {
            return new BusinessException(AiGatewayErrorCode.UPSTREAM_QUOTA_EXCEEDED);
        }
        if (responseBody.contains("context") && (responseBody.contains("length") || responseBody.contains("token"))) {
            return new BusinessException(AiGatewayErrorCode.CONTEXT_WINDOW_EXCEEDED);
        }
        if (responseBody.contains("image") && (responseBody.contains("format") || responseBody.contains("media type"))) {
            return new BusinessException(AiGatewayErrorCode.MEDIA_TYPE_UNSUPPORTED);
        }
        if (status == 400 || status == 422) {
            return new BusinessException(AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
        }
        if (status >= 500) {
            return new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE);
        }
        return new BusinessException(AiGatewayErrorCode.RESPONSE_INVALID);
    }

    private Duration retryAfter(RestClientResponseException exception) {
        if (exception.getResponseHeaders() == null) return null;
        String value = exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(value)) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.strip())));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                return Duration.between(ZonedDateTime.now(target.getZone()), target).isNegative()
                        ? Duration.ZERO : Duration.between(ZonedDateTime.now(target.getZone()), target);
            } catch (RuntimeException invalidDate) {
                return null;
            }
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
