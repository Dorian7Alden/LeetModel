package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.AiRoutingProperties;
import com.leetmodel.aigateway.config.AiModelCatalogProperties;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.provider.AiProviderAdapter;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Set;

/**
 * 统一 AI 对话服务。
 */
@Slf4j
@Service
public class AiChatService {
    private static final Set<String> STANDARD_IMAGE_MEDIA_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private final AiRoutingProperties routingProperties;
    private final AiProviderRegistry providerRegistry;
    private final AiModelCatalogProperties modelCatalog;

    /**
     * 创建统一 AI 对话服务。
     *
     * @param routingProperties 场景路由配置
     * @param providerRegistry 供应商注册表
     */
    public AiChatService(
            AiRoutingProperties routingProperties,
            AiProviderRegistry providerRegistry,
            AiModelCatalogProperties modelCatalog
    ) {
        this.routingProperties = routingProperties;
        this.providerRegistry = providerRegistry;
        this.modelCatalog = modelCatalog;
    }

    /**
     * 根据场景路由发起同步 AI 对话。
     *
     * @param request 统一请求
     * @return 统一响应
     */
    public AiChatResponse chat(AiChatRequest request) {
        // 读取场景路由
        AiRoutingProperties.Route route = routingProperties.getRoutes().get(request.scene());
        BusinessException.throwIf(
                route == null || route.getProvider() == null || route.getModel() == null,
                AiGatewayErrorCode.ROUTE_NOT_FOUND
        );
        validateCapabilities(route, request);

        // 调用供应商适配器
        String callId = UUID.randomUUID().toString();
        AiProviderAdapter adapter = providerRegistry.get(route.getProvider());
        AiChatResponse providerResponse = adapter.chat(route.getModel(), request);

        // 记录不含业务内容和密钥的调用摘要
        log.info(
                "AI 调用完成 callId={}, provider={}, model={}, totalTokens={}",
                callId,
                providerResponse.provider(),
                providerResponse.model(),
                providerResponse.usage().totalTokens()
        );
        return withCallId(callId, providerResponse);
    }

    private void validateCapabilities(AiRoutingProperties.Route route, AiChatRequest request) {
        String key = route.getProvider().name() + "/" + route.getModel();
        AiModelCatalogProperties.ModelProfile profile = modelCatalog.getModels().get(key);
        BusinessException.throwIf(profile == null || !profile.isEnabled(), AiGatewayErrorCode.MODEL_DISABLED);
        boolean inputSupported = request.messages().stream().flatMap(message -> message.content().stream())
                .allMatch(part -> profile.getInputTypes().contains(part.type()));
        BusinessException.throwIf(!inputSupported, AiGatewayErrorCode.INPUT_TYPE_UNSUPPORTED);
        BusinessException.throwIf(request.responseFormat() != null && !profile.isJsonOutput(),
                AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
        BusinessException.throwIf(Boolean.TRUE.equals(request.thinkingEnabled()) && !profile.isThinking(),
                AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
        var imageParts = request.messages().stream().flatMap(message -> message.content().stream())
                .filter(part -> part.type() == AiContentType.IMAGE_URL).toList();
        BusinessException.throwIf(profile.getMaxImages() != null && imageParts.size() > profile.getMaxImages(),
                AiGatewayErrorCode.IMAGE_COUNT_EXCEEDED);
        boolean mediaTypesSupported = imageParts.stream().allMatch(part -> supportsMediaType(profile, part.url()));
        BusinessException.throwIf(!mediaTypesSupported, AiGatewayErrorCode.MEDIA_TYPE_UNSUPPORTED);
        long estimatedImageBytes = imageParts.stream().mapToLong(part -> estimateDataUrlBytes(part.url())).sum();
        BusinessException.throwIf(profile.getMaxTotalImageBytes() != null
                        && estimatedImageBytes > profile.getMaxTotalImageBytes(),
                AiGatewayErrorCode.IMAGE_BYTES_EXCEEDED);
        BusinessException.throwIf(request.maxTokens() != null && profile.getMaxOutputTokens() != null
                        && request.maxTokens() > profile.getMaxOutputTokens(),
                AiGatewayErrorCode.OUTPUT_LIMIT_EXCEEDED);
        long textCodePoints = request.messages().stream().flatMap(message -> message.content().stream())
                .filter(part -> part.type() == AiContentType.TEXT && part.text() != null)
                .mapToLong(part -> part.text().codePointCount(0, part.text().length())).sum();
        long reservedOutput = request.maxTokens() == null ? 0 : request.maxTokens();
        BusinessException.throwIf(profile.getContextTokens() != null
                        && textCodePoints + reservedOutput > profile.getContextTokens(),
                AiGatewayErrorCode.CONTEXT_WINDOW_EXCEEDED);
    }

    private boolean supportsMediaType(AiModelCatalogProperties.ModelProfile profile, String url) {
        if (url == null || !url.startsWith("data:")) return true;
        int separator = url.indexOf(';');
        if (separator < 5) return false;
        String mediaType = url.substring(5, separator).toLowerCase();
        Set<String> configured = profile.getImageMediaTypes();
        return (configured == null || configured.isEmpty() ? STANDARD_IMAGE_MEDIA_TYPES : configured)
                .stream().map(this::normalizeMediaType).anyMatch(normalizeMediaType(mediaType)::equals);
    }

    private String normalizeMediaType(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private long estimateDataUrlBytes(String url) {
        if (url == null || !url.startsWith("data:")) return 0;
        int comma = url.indexOf(',');
        if (comma < 0) return 0;
        int encodedLength = url.length() - comma - 1;
        return encodedLength * 3L / 4L;
    }

    /**
     * 将网关调用标识写入供应商响应。
     *
     * @param callId 网关调用标识
     * @param response 供应商响应
     * @return 完整统一响应
     */
    private AiChatResponse withCallId(String callId, AiChatResponse response) {
        return new AiChatResponse(
                callId,
                response.provider(),
                response.model(),
                response.providerResponseId(),
                response.content(),
                response.reasoningContent(),
                response.finishReason(),
                response.usage()
        );
    }
}
