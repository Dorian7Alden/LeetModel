package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一请求到供应商请求的映射工具。
 */
final class ProviderRequestMapper {

    private ProviderRequestMapper() {
    }

    /**
     * 构建只有文本内容的消息列表。
     *
     * @param messages 统一消息列表
     * @return OpenAI 文本消息列表
     */
    static List<Map<String, Object>> toTextMessages(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            StringBuilder content = new StringBuilder();
            for (AiContentPart part : message.content()) {
                BusinessException.throwIf(
                        part.type() != AiContentType.TEXT,
                        AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED
                );
                BusinessException.throwIf(
                        !StringUtils.hasText(part.text()),
                        AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED
                );
                content.append(part.text());
            }
            result.add(message(message, content.toString()));
        }
        return result;
    }

    /**
     * 构建支持图片地址的多模态消息列表。
     *
     * @param messages 统一消息列表
     * @return OpenAI 多模态消息列表
     */
    static List<Map<String, Object>> toMultimodalMessages(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            boolean containsImage = message.content().stream()
                    .anyMatch(part -> part.type() == AiContentType.IMAGE_URL);
            if (!containsImage) {
                result.add(toTextMessages(List.of(message)).get(0));
                continue;
            }
            List<Map<String, Object>> content = new ArrayList<>();
            for (AiContentPart part : message.content()) {
                content.add(toContentPart(part));
            }
            Map<String, Object> providerMessage = new LinkedHashMap<>();
            providerMessage.put("role", message.role().name().toLowerCase(Locale.ROOT));
            providerMessage.put("content", content);
            result.add(providerMessage);
        }
        return result;
    }

    /**
     * 添加通用可选参数。
     *
     * @param body 请求体
     * @param request 统一请求
     */
    static void addCommonOptions(Map<String, Object> body, AiChatRequest request) {
        if (request.maxTokens() != null) body.put("max_tokens", request.maxTokens());
        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.responseFormat() == AiResponseFormat.JSON_OBJECT) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        if (request.thinkingEnabled() != null) {
            String type = Boolean.TRUE.equals(request.thinkingEnabled()) ? "enabled" : "disabled";
            body.put("thinking", Map.of("type", type));
        }
    }

    static List<Map<String, Object>> toResponsesInput(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            List<Map<String, Object>> content = new ArrayList<>();
            for (AiContentPart part : message.content()) {
                if (part.type() == AiContentType.TEXT) {
                    requireText(part.text());
                    content.add(Map.of("type", "input_text", "text", part.text()));
                } else {
                    requireUrl(part.url());
                    content.add(Map.of("type", "input_image", "image_url", part.url()));
                }
            }
            result.add(Map.of(
                    "role", message.role().name().toLowerCase(Locale.ROOT),
                    "content", content
            ));
        }
        return result;
    }

    static List<Map<String, Object>> toAnthropicMessages(List<AiMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiMessage message : messages) {
            if (message.role() == com.leetmodel.common.ai.model.AiRole.SYSTEM) continue;
            List<Map<String, Object>> content = new ArrayList<>();
            for (AiContentPart part : message.content()) {
                if (part.type() == AiContentType.TEXT) {
                    requireText(part.text());
                    content.add(Map.of("type", "text", "text", part.text()));
                } else {
                    requireUrl(part.url());
                    content.add(toAnthropicImage(part.url()));
                }
            }
            result.add(Map.of(
                    "role", message.role().name().toLowerCase(Locale.ROOT),
                    "content", content
            ));
        }
        return result;
    }

    static String anthropicSystem(List<AiMessage> messages) {
        return messages.stream()
                .filter(message -> message.role() == com.leetmodel.common.ai.model.AiRole.SYSTEM)
                .flatMap(message -> message.content().stream())
                .map(part -> {
                    BusinessException.throwIf(part.type() != AiContentType.TEXT,
                            AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
                    requireText(part.text());
                    return part.text();
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    static void addResponsesOptions(Map<String, Object> body, AiChatRequest request) {
        if (request.maxTokens() != null) body.put("max_output_tokens", request.maxTokens());
        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.responseFormat() == AiResponseFormat.JSON_OBJECT) {
            body.put("text", Map.of("format", openAiJsonSchema()));
        }
    }

    static void addAnthropicOptions(Map<String, Object> body, AiChatRequest request) {
        body.put("max_tokens", request.maxTokens() == null ? 4096 : request.maxTokens());
        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.responseFormat() == AiResponseFormat.JSON_OBJECT) {
            body.put("output_config", Map.of("format", anthropicJsonSchema()));
        }
        if (request.thinkingEnabled() != null) {
            if (Boolean.TRUE.equals(request.thinkingEnabled())) {
                body.put("thinking", Map.of(
                        "type", "enabled",
                        "budget_tokens", Math.max(1024, request.maxTokens() == null ? 4096 : request.maxTokens())
                ));
            } else {
                body.put("thinking", Map.of("type", "disabled"));
            }
        }
    }

    /**
     * 构建单条文本消息。
     *
     * @param message 统一消息
     * @param content 文本内容
     * @return 供应商消息
     */
    private static Map<String, Object> message(AiMessage message, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", message.role().name().toLowerCase(Locale.ROOT));
        result.put("content", content);
        return result;
    }

    /**
     * 转换一个多模态内容块。
     *
     * @param part 统一内容块
     * @return 供应商内容块
     */
    private static Map<String, Object> toContentPart(AiContentPart part) {
        if (part.type() == AiContentType.TEXT) {
            BusinessException.throwIf(
                    !StringUtils.hasText(part.text()),
                    AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED
            );
            return Map.of("type", "text", "text", part.text());
        }

        BusinessException.throwIf(
                !StringUtils.hasText(part.url()),
                AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED
        );
        return Map.of("type", "image_url", "image_url", Map.of("url", part.url()));
    }

    private static Map<String, Object> toAnthropicImage(String url) {
        if (!url.startsWith("data:")) {
            return Map.of("type", "image", "source", Map.of("type", "url", "url", url));
        }
        int typeEnd = url.indexOf(';');
        int dataStart = url.indexOf(',');
        BusinessException.throwIf(typeEnd < 5 || dataStart <= typeEnd,
                AiGatewayErrorCode.MEDIA_TYPE_UNSUPPORTED);
        return Map.of("type", "image", "source", Map.of(
                "type", "base64",
                "media_type", url.substring(5, typeEnd),
                "data", url.substring(dataStart + 1)
        ));
    }

    private static Map<String, Object> openAiJsonSchema() {
        return Map.of(
                "type", "json_schema",
                "name", "response",
                "schema", Map.of("type", "object", "additionalProperties", true)
        );
    }

    private static Map<String, Object> anthropicJsonSchema() {
        return Map.of(
                "type", "json_schema",
                "schema", Map.of("type", "object", "additionalProperties", true)
        );
    }

    private static void requireText(String text) {
        BusinessException.throwIf(!StringUtils.hasText(text), AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
    }

    private static void requireUrl(String url) {
        BusinessException.throwIf(!StringUtils.hasText(url), AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
    }
}
