package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.provider.AiProviderAdapter;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AI 供应商适配器注册表。
 */
@Component
public class AiProviderRegistry {

    private final Map<AiProvider, AiProviderAdapter> adapters;

    /**
     * 注册所有供应商适配器。
     *
     * @param providerAdapters Spring 发现的供应商适配器
     */
    public AiProviderRegistry(List<AiProviderAdapter> providerAdapters) {
        this.adapters = new EnumMap<>(AiProvider.class);
        for (AiProviderAdapter adapter : providerAdapters) {
            this.adapters.put(adapter.provider(), adapter);
        }
    }

    /**
     * 获取指定供应商适配器。
     *
     * @param provider 供应商
     * @return 供应商适配器
     */
    public AiProviderAdapter get(AiProvider provider) {
        AiProviderAdapter adapter = adapters.get(provider);
        BusinessException.throwIf(adapter == null, AiGatewayErrorCode.PROVIDER_NOT_CONFIGURED);
        return adapter;
    }
}
