package com.leetmodel.assistant.service;

import com.leetmodel.assistant.entity.AssistantProductionConfig;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.retrieval.RagVectorSearchStore;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolRegistry;
import com.leetmodel.assistant.tool.knowledge.ExplainModelingKnowledgeTool;
import com.leetmodel.common.api.dto.ModelExecutionConfigAvailabilityDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.core.result.Result;
import org.springframework.stereotype.Component;

/** 激活前只读验证 AI 网关配置和固定 RAG 物理索引。 */
@Component
public class AssistantProductionDependencyValidator {

    private final AiGatewayFeignClient aiGatewayClient;
    private final RagProperties ragProperties;
    private final RagVectorSearchStore ragStore;
    private final AssistantToolRegistry toolRegistry;

    public AssistantProductionDependencyValidator(AiGatewayFeignClient aiGatewayClient,
                                                  RagProperties ragProperties,
                                                  RagVectorSearchStore ragStore,
                                                  AssistantToolRegistry toolRegistry) {
        this.aiGatewayClient = aiGatewayClient;
        this.ragProperties = ragProperties;
        this.ragStore = ragStore;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 检查模型执行配置以及适用时的固定物理 RAG 索引。
     * @param config 待激活或回滚的不可变配置
     * @return 所有运行依赖可用时为 true
     */
    public boolean isReady(AssistantProductionConfig config) {
        if (config == null) return false;

        // AI 网关只返回发布兼容性，不暴露供应商或渠道
        Result<ModelExecutionConfigAvailabilityDTO> response =
                aiGatewayClient.getModelExecutionConfigAvailability(
                        config.getModelExecutionConfigVersion(), "CHAT",
                        config.getWorkflowVersion(), config.getPromptVersion());
        boolean modelReady = response != null && response.isSuccess() && response.getData() != null
                && Boolean.TRUE.equals(response.getData().getAvailable());
        if (!modelReady) return false;
        // 工具工作流还必须同时具备本地工具集和终止式知识模型配置
        if (config.getToolsetVersion() != null) {
            try {
                toolRegistry.definitions(config.getToolsetVersion(), config.getWorkflowVersion());
            } catch (AssistantToolException exception) {
                return false;
            }
            Result<ModelExecutionConfigAvailabilityDTO> knowledgeResponse =
                    aiGatewayClient.getModelExecutionConfigAvailability(
                            ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION, "CHAT",
                            config.getWorkflowVersion(),
                            ExplainModelingKnowledgeTool.PROMPT_VERSION);
            boolean knowledgeReady = knowledgeResponse != null
                    && knowledgeResponse.isSuccess() && knowledgeResponse.getData() != null
                    && Boolean.TRUE.equals(knowledgeResponse.getData().getAvailable());
            if (!knowledgeReady) return false;
        }
        // 无 RAG 不接受索引；固定 RAG 必须检查物理索引和维度
        if ("NONE".equals(config.getRagMode())) return config.getRagIndexVersion() == null;
        return "FIXED_INDEX".equals(config.getRagMode()) && ragProperties.isEnabled()
                && config.getRagIndexVersion() != null
                && ragStore.isVersionReady(config.getRagIndexVersion(),
                ragProperties.getEmbeddingDimension());
    }
}
