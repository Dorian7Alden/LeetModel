package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatRequestCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldReadLegacySceneAsRoutingModalityWithoutInventingBusinessSource() throws Exception {
        AiChatRequest request = objectMapper.readValue("""
                {"scene":"GENERAL_TEXT","messages":[{"role":"USER","content":[{"type":"TEXT","text":"hi"}]}]}
                """, AiChatRequest.class);

        assertThat(request.effectiveModality()).isEqualTo(AiModality.TEXT);
        assertThat(request.context()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRoundTripNewModalityAndContextContract() throws Exception {
        AiCallContext context = new AiCallContext("ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.CHAT_REPLY, "message:1", "ASSISTANT_CHAT_V1",
                "PROMPT_ASSISTANT_CHAT_0001", "MODEL_CFG_ASSISTANT_TEXT_0001", null,
                AiCallPriority.P0, "assistant:message:1", Instant.parse("2099-01-01T00:00:00Z"));
        AiChatRequest request = new AiChatRequest(AiModality.TEXT, context,
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "hi", null)))),
                100, null, AiResponseFormat.TEXT, false);

        AiChatRequest restored = objectMapper.readValue(objectMapper.writeValueAsBytes(request), AiChatRequest.class);

        assertThat(restored.scene()).isNull();
        assertThat(restored.modality()).isEqualTo(AiModality.TEXT);
        assertThat(restored.context()).isEqualTo(context);
        assertThat(validator.validate(restored)).isEmpty();
    }
}
