package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.ModelExecutionConfigProperties;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.model.ModelExecutionSnapshot;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.api.dto.ModelExecutionConfigAvailabilityDTO;
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
                definition.getThinkingEnabled(), definition.isTools(), definition.getEmbeddingDimension(),
                definition.getMaxBatchSize(), definition.getMaxInputChars(), definition.getMaxTotalChars(),
                context.promptVersion(), context.workflowVersion());
    }

    /**
     * 发布前只读检查模型执行配置与工作流引用是否兼容。
     * @param version 模型执行配置版本
     * @param callType 调用类型
     * @param workflowVersion 工作流版本
     * @param promptVersion Prompt 版本
     * @return 不包含物理模型、供应商或渠道的可用性结果
     */
    public ModelExecutionConfigAvailabilityDTO availability(String version, String callType,
                                                             String workflowVersion,
                                                             String promptVersion) {
        ModelExecutionConfigProperties.Definition definition =
                version == null ? null : properties.getExecutionConfigs().get(version);
        boolean available = definition != null && definition.isEnabled()
                && Objects.equals(callType, definition.getCallType())
                && allows(definition.getWorkflowVersions(), workflowVersion)
                && allows(definition.getPromptVersions(), promptVersion);
        String reason = available ? "AVAILABLE" : "UNAVAILABLE_OR_INCOMPATIBLE";
        return new ModelExecutionConfigAvailabilityDTO(version, available, callType, reason);
    }

    private void validateChat(AiCallContext context, AiChatRequest request,
                              ModelExecutionConfigProperties.Definition definition) {
        boolean matches = definition.getProvider() != null && definition.getModel() != null
                && request.effectiveModality() == definition.getModality()
                && Objects.equals(request.maxTokens(), definition.getMaxTokens())
                && Objects.equals(request.temperature(), definition.getTemperature())
                && request.responseFormat() == definition.getResponseFormat()
                && Objects.equals(request.thinkingEnabled(), definition.getThinkingEnabled())
                && (!usesToolProtocol(request) || definition.isTools())
                && allows(definition.getPromptVersions(), context.promptVersion())
                && allows(definition.getWorkflowVersions(), context.workflowVersion());
        BusinessException.throwIf(!matches, AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH);
    }

    /**
     * 判断请求是否使用工具协议。
     *
     * @param request 对话请求
     * @return 是否使用工具
     */
    private boolean usesToolProtocol(AiChatRequest request) {
        if (request.tools() != null && !request.tools().isEmpty()) return true;
        return request.messages().stream().anyMatch(message ->
                message.role() == com.leetmodel.common.ai.model.AiRole.TOOL
                        || message.toolCalls() != null && !message.toolCalls().isEmpty());
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
