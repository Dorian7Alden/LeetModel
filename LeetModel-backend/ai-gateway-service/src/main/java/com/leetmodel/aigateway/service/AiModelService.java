package com.leetmodel.aigateway.service;

import com.leetmodel.common.ai.model.AiModelInfo;
import com.leetmodel.common.ai.model.AiProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 供应商官方模型目录服务。
 */
@Service
public class AiModelService {

    private final AiProviderRegistry providerRegistry;

    /**
     * 创建模型目录服务。
     *
     * @param providerRegistry 供应商注册表
     */
    public AiModelService(AiProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * 查询供应商官方模型列表。
     *
     * @param provider 供应商
     * @return 模型列表
     */
    public List<AiModelInfo> listModels(AiProvider provider) {
        return providerRegistry.get(provider).listModels();
    }
}
