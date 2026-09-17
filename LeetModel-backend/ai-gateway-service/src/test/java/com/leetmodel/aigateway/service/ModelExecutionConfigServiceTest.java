package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.ModelExecutionConfigProperties;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.common.ai.model.*;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelExecutionConfigServiceTest {

    @Test
    void resolvesExactPhysicalModelAndParametersIntoSnapshot() {
        ModelExecutionConfigProperties properties = properties();
        ModelExecutionConfigService service = new ModelExecutionConfigService(properties);

        var snapshot = service.resolve("CHAT", context(), request(0.2));

        assertThat(snapshot.modelExecutionConfigVersion()).isEqualTo("MODEL_CFG_ASSISTANT_TEXT_0001");
        assertThat(snapshot.model()).isEqualTo("deepseek-physical-202608");
        assertThat(snapshot.temperature()).isEqualTo(0.2);
        assertThat(snapshot.promptVersion()).isEqualTo("PROMPT_ASSISTANT_CHAT_0001");
    }

    @Test
    void rejectsCallerParametersThatDriftFromPublishedConfig() {
        ModelExecutionConfigService service = new ModelExecutionConfigService(properties());

        assertThatThrownBy(() -> service.resolve("CHAT", context(), request(0.8)))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(
                                AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH.getCode()));
    }

    @Test
    void availabilityChecksWorkflowAndPromptWithoutExposingPhysicalRoute() {
        ModelExecutionConfigService service = new ModelExecutionConfigService(properties());

        assertThat(service.availability("MODEL_CFG_ASSISTANT_TEXT_0001", "CHAT",
                "ASSISTANT_CHAT_V1", "PROMPT_ASSISTANT_CHAT_0001").getAvailable()).isTrue();
        assertThat(service.availability("MODEL_CFG_ASSISTANT_TEXT_0001", "CHAT",
                "ASSISTANT_RAG_V1", "PROMPT_ASSISTANT_CHAT_0001").getAvailable()).isFalse();
    }

    @Test
    void rejectsToolsUnlessPublishedExecutionConfigAllowsThem() {
        ModelExecutionConfigProperties properties = properties();
        ModelExecutionConfigService service = new ModelExecutionConfigService(properties);

        assertThatThrownBy(() -> service.resolve("CHAT", context(), toolRequest()))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(
                                AiGatewayErrorCode.MODEL_EXECUTION_CONFIG_MISMATCH.getCode()));

        properties.getExecutionConfigs().get("MODEL_CFG_ASSISTANT_TEXT_0001").setTools(true);
        assertThat(service.resolve("CHAT", context(), toolRequest()).tools()).isTrue();
    }

    private ModelExecutionConfigProperties properties() {
        ModelExecutionConfigProperties.Definition definition = new ModelExecutionConfigProperties.Definition();
        definition.setCallType("CHAT");
        definition.setProvider(AiProvider.NEW_API);
        definition.setModel("deepseek-physical-202608");
        definition.setModality(AiModality.TEXT);
        definition.setMaxTokens(1500);
        definition.setTemperature(0.2);
        definition.setResponseFormat(AiResponseFormat.TEXT);
        definition.setThinkingEnabled(false);
        definition.setPromptVersions(Set.of("PROMPT_ASSISTANT_CHAT_0001"));
        definition.setWorkflowVersions(Set.of("ASSISTANT_CHAT_V1"));
        ModelExecutionConfigProperties properties = new ModelExecutionConfigProperties();
        properties.setExecutionConfigs(java.util.Map.of("MODEL_CFG_ASSISTANT_TEXT_0001", definition));
        return properties;
    }

    private AiCallContext context() {
        return new AiCallContext("ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.CHAT_REPLY, "message:1", "ASSISTANT_CHAT_V1",
                "PROMPT_ASSISTANT_CHAT_0001", "MODEL_CFG_ASSISTANT_TEXT_0001", null,
                AiCallPriority.P0, "assistant:message:1", Instant.now().plusSeconds(60));
    }

    private AiChatRequest request(double temperature) {
        return new AiChatRequest(AiModality.TEXT, context(),
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "question", null)))),
                1500, temperature, AiResponseFormat.TEXT, false);
    }

    private AiChatRequest toolRequest() {
        AiToolDefinition tool = new AiToolDefinition(AiToolType.FUNCTION,
                "search_problem", "查询题目", java.util.Map.of("type", "object"));
        return new AiChatRequest(AiModality.TEXT, context(),
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "question", null)))),
                1500, 0.2, AiResponseFormat.TEXT, false,
                List.of(tool), new AiToolChoice(AiToolChoiceType.AUTO, null));
    }
}
