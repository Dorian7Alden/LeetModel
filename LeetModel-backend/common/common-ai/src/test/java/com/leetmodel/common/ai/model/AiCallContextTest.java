package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallContextTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldSerializeStableCodesAndDeadline() throws Exception {
        AiCallContext context = validContext();

        String json = objectMapper.writeValueAsString(context);
        AiCallContext restored = objectMapper.readValue(json, AiCallContext.class);

        assertThat(json).contains("\"featureCode\":\"AI_ASSISTANT\"")
                .contains("\"operationCode\":\"CHAT_REPLY\"")
                .doesNotContain("operationCompatible");
        assertThat(restored).isEqualTo(context);
        assertThat(validator.validate(restored)).isEmpty();
    }

    @Test
    void shouldRejectMismatchedOperationAndMissingRequiredMetadata() {
        AiCallContext context = new AiCallContext(" ", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.FORMAL_REVIEW, null, null, null, null, null,
                null, " ", Instant.EPOCH);

        assertThat(validator.validate(context))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("callerService", "priority", "idempotencyKey", "deadline", "operationCompatible");
    }

    private AiCallContext validContext() {
        return new AiCallContext("ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.CHAT_REPLY, "message:42", "ASSISTANT_CHAT_V1",
                "PROMPT_ASSISTANT_CHAT_0001", "MODEL_CFG_ASSISTANT_CHAT_0001", null,
                AiCallPriority.P0, "assistant-message:42", Instant.parse("2099-01-01T00:00:00Z"));
    }
}
