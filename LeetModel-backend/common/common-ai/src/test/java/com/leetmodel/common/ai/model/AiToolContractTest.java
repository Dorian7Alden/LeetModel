package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiToolContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRoundTripToolDefinitionsCallsAndResults() throws Exception {
        AiChatRequest request = new AiChatRequest(AiScene.GENERAL_TEXT, null, null,
                List.of(
                        new AiMessage(AiRole.ASSISTANT, List.of(),
                                List.of(new AiToolCall("call-1", "search_problem", "{\"code\":1001}")),
                                null, null),
                        new AiMessage(AiRole.TOOL,
                                List.of(new AiContentPart(AiContentType.TEXT, "{\"items\":[]}", null)),
                                null, "call-1", "search_problem")
                ),
                100, null, AiResponseFormat.TEXT, false,
                List.of(toolDefinition()), new AiToolChoice(AiToolChoiceType.AUTO, null));

        AiChatRequest restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(request), AiChatRequest.class);

        assertThat(validator.validate(restored)).isEmpty();
        assertThat(restored.tools()).extracting(AiToolDefinition::name)
                .containsExactly("search_problem");
        assertThat(restored.messages().get(0).toolCalls().get(0).argumentsJson())
                .isEqualTo("{\"code\":1001}");
        assertThat(restored.messages().get(1).toolCallId()).isEqualTo("call-1");
    }

    @Test
    void shouldRejectDuplicateNamesUnknownNamedChoiceAndRemoteSchemaReference() {
        AiToolDefinition remote = new AiToolDefinition(AiToolType.FUNCTION, "remote_tool", "远程工具",
                Map.of("type", "object", "$ref", "https://fixture.test/schema.json"));
        AiChatRequest duplicate = new AiChatRequest(AiScene.GENERAL_TEXT, null, null,
                List.of(userMessage()), 100, null, null, false,
                List.of(toolDefinition(), toolDefinition()),
                new AiToolChoice(AiToolChoiceType.NAMED, "missing_tool"));

        assertThat(validator.validate(remote)).isNotEmpty();
        assertThat(validator.validate(duplicate)).isNotEmpty();
    }

    @Test
    void shouldKeepLegacyMessagesValidAndRejectUnlinkedToolResult() {
        AiMessage legacy = userMessage();
        AiMessage invalidTool = new AiMessage(AiRole.TOOL,
                List.of(new AiContentPart(AiContentType.TEXT, "{}", null)),
                null, null, "search_problem");

        assertThat(validator.validate(legacy)).isEmpty();
        assertThat(validator.validate(invalidTool)).isNotEmpty();
    }

    private AiMessage userMessage() {
        return new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "查询题目", null)));
    }

    private AiToolDefinition toolDefinition() {
        return new AiToolDefinition(AiToolType.FUNCTION, "search_problem", "查询已发布题目",
                Map.of(
                        "type", "object",
                        "properties", Map.of("code", Map.of("type", "integer")),
                        "required", List.of("code"),
                        "additionalProperties", false
                ));
    }
}
