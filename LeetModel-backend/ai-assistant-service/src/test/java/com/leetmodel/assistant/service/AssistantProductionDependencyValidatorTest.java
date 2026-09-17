package com.leetmodel.assistant.service;

import com.leetmodel.assistant.entity.AssistantProductionConfig;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.retrieval.RagVectorSearchStore;
import com.leetmodel.assistant.tool.AssistantToolRegistry;
import com.leetmodel.assistant.tool.knowledge.ExplainModelingKnowledgeTool;
import com.leetmodel.common.api.dto.ModelExecutionConfigAvailabilityDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantProductionDependencyValidatorTest {

    @Mock AiGatewayFeignClient aiGatewayClient;
    @Mock RagVectorSearchStore ragStore;
    @Mock AssistantToolRegistry toolRegistry;

    private AssistantProductionDependencyValidator validator;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(false);
        validator = new AssistantProductionDependencyValidator(
                aiGatewayClient, properties, ragStore, toolRegistry);
    }

    @Test
    void legacyNoRagWorkflowNeedsOnlyItsPrimaryModelConfig() {
        AssistantProductionConfig config = config(false);
        when(aiGatewayClient.getModelExecutionConfigAvailability(
                "MODEL_CFG_ASSISTANT_TEXT_0001", "CHAT",
                "ASSISTANT_NO_RAG_V1", "PROMPT_ASSISTANT_CHAT_0001"))
                .thenReturn(available("MODEL_CFG_ASSISTANT_TEXT_0001"));

        assertThat(validator.isReady(config)).isTrue();
        verify(toolRegistry, never()).definitions(anyString(), anyString());
    }

    @Test
    void toolWorkflowRequiresRegistryAndDedicatedKnowledgeModel() {
        AssistantProductionConfig config = config(true);
        when(aiGatewayClient.getModelExecutionConfigAvailability(
                "MODEL_CFG_ASSISTANT_TOOLS_0001", "CHAT",
                "ASSISTANT_TOOLS_NO_RAG_V1", "PROMPT_ASSISTANT_TOOLS_0001"))
                .thenReturn(available("MODEL_CFG_ASSISTANT_TOOLS_0001"));
        when(toolRegistry.definitions("ASSISTANT_TOOLSET_0001",
                "ASSISTANT_TOOLS_NO_RAG_V1")).thenReturn(List.of());
        when(aiGatewayClient.getModelExecutionConfigAvailability(
                ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION, "CHAT",
                "ASSISTANT_TOOLS_NO_RAG_V1", ExplainModelingKnowledgeTool.PROMPT_VERSION))
                .thenReturn(available(ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION));

        assertThat(validator.isReady(config)).isTrue();
        verify(aiGatewayClient).getModelExecutionConfigAvailability(
                ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION, "CHAT",
                "ASSISTANT_TOOLS_NO_RAG_V1", ExplainModelingKnowledgeTool.PROMPT_VERSION);
    }

    @Test
    void missingKnowledgeModelRejectsToolWorkflowBeforeActivation() {
        AssistantProductionConfig config = config(true);
        when(aiGatewayClient.getModelExecutionConfigAvailability(
                "MODEL_CFG_ASSISTANT_TOOLS_0001", "CHAT",
                "ASSISTANT_TOOLS_NO_RAG_V1", "PROMPT_ASSISTANT_TOOLS_0001"))
                .thenReturn(available("MODEL_CFG_ASSISTANT_TOOLS_0001"));
        when(toolRegistry.definitions(anyString(), anyString())).thenReturn(List.of());
        when(aiGatewayClient.getModelExecutionConfigAvailability(
                eq(ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION), eq("CHAT"),
                anyString(), eq(ExplainModelingKnowledgeTool.PROMPT_VERSION)))
                .thenReturn(Result.ok(new ModelExecutionConfigAvailabilityDTO(
                        ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION, false,
                        "CHAT", "UNAVAILABLE")));

        assertThat(validator.isReady(config)).isFalse();
    }

    private AssistantProductionConfig config(boolean tools) {
        AssistantProductionConfig config = new AssistantProductionConfig();
        config.setWorkflowVersion(tools
                ? "ASSISTANT_TOOLS_NO_RAG_V1" : "ASSISTANT_NO_RAG_V1");
        config.setPromptVersion(tools
                ? "PROMPT_ASSISTANT_TOOLS_0001" : "PROMPT_ASSISTANT_CHAT_0001");
        config.setModelExecutionConfigVersion(tools
                ? "MODEL_CFG_ASSISTANT_TOOLS_0001" : "MODEL_CFG_ASSISTANT_TEXT_0001");
        config.setToolsetVersion(tools ? "ASSISTANT_TOOLSET_0001" : null);
        config.setRagMode("NONE");
        return config;
    }

    private Result<ModelExecutionConfigAvailabilityDTO> available(String version) {
        return Result.ok(new ModelExecutionConfigAvailabilityDTO(
                version, true, "CHAT", "AVAILABLE"));
    }
}
