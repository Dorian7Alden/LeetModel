package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.AiRoutingProperties;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.provider.AiProviderAdapter;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 统一 AI 对话服务。
 */
@Slf4j
@Service
public class AiChatService {

    private final AiRoutingProperties routingProperties;
    private final AiProviderRegistry providerRegistry;

    /**
     * 创建统一 AI 对话服务。
     *
     * @param routingProperties 场景路由配置
     * @param providerRegistry 供应商注册表
     */
    public AiChatService(
            AiRoutingProperties routingProperties,
            AiProviderRegistry providerRegistry
    ) {
        this.routingProperties = routingProperties;
        this.providerRegistry = providerRegistry;
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
