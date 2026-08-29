package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.ModelExecutionConfigProperties;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.model.ModelExecutionSnapshot;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 解析并严格校验调用方引用的不可变模型执行配置。 */
@Service
public class ModelExecutionConfigService {
    private final ModelExecutionConfigProperties properties;

    public ModelExecutionConfigService(ModelExecutionConfigProperties properties) {
        this.properties = properties;
    }

    public ModelExecutionSnapshot resolve(String callType, AiCallContext context, Object request) {
        String version = context == null ? null : context.modelExecutionConfigVersion();
        ModelExecutionConfigProperties.Definition definition =
                version == null ? null : properties.getExecutionConfigs().get(version);
        BusinessException.throwIf(definition == null || !definition.isEnabled(),
                AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_UNAVAILABLE);
        BusinessException.throwIf(!callType.equals(definition.getCallType()),
                AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH);
        if (request instanceof AiChatRequest chat) validateChat(context, chat, definition);
        else if (request instanceof AiEmbeddingRequest embedding) validateEmbedding(embedding, definition);
        else throw new BusinessException(AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH);
        return new ModelExecutionSnapshot(version, callType, definition.getLogicalModel(),
                definition.getProvider(), definition.getModel(), definition.getModality(),
                definition.getMaxTokens(), definition.getTemperature(), definition.getResponseFormat(),
                definition.getThinkingEnabled(), definition.getEmbeddingDimension(),
                definition.getMaxBatchSize(), definition.getMaxInputChars(), definition.getMaxTotalChars(),
                context.promptVersion(), context.workflowVersion());
    }

    private void validateChat(AiCallContext context, AiChatRequest request,
                              ModelExecutionConfigProperties.Definition definition) {
        boolean matches = definition.getProvider() != null && definition.getModel() != null
                && request.effectiveModality() == definition.getModality()
                && Objects.equals(request.maxTokens(), definition.getMaxTokens())
                && Objects.equals(request.temperature(), definition.getTemperature())
                && request.responseFormat() == definition.getResponseFormat()
                && Objects.equals(request.thinkingEnabled(), definition.getThinkingEnabled())
                && allows(definition.getPromptVersions(), context.promptVersion())
                && allows(definition.getWorkflowVersions(), context.workflowVersion());
        BusinessException.throwIf(!matches, AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH);
    }

    private void validateEmbedding(AiEmbeddingRequest request,
                                   ModelExecutionConfigProperties.Definition definition) {
        boolean matches = definition.getProvider() != null && definition.getModel() != null
                && Objects.equals(request.logicalModel(), definition.getLogicalModel())
                && definition.getEmbeddingDimension() != null
                && definition.getMaxBatchSize() != null && definition.getMaxInputChars() != null
                && definition.getMaxTotalChars() != null;
        BusinessException.throwIf(!matches, AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH);
    }

    private boolean allows(java.util.Set<String> values, String actual) {
        return values == null || values.isEmpty() || values.contains(actual);
    }
}
