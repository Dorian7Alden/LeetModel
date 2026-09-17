package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiEmbeddingContractTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldSerializeSingleAndBatchWithoutProviderConfiguration() throws Exception {
        AiEmbeddingRequest single = AiEmbeddingRequest.single("RAG_V1", context(), "中文查询");
        AiEmbeddingRequest batch = new AiEmbeddingRequest("RAG_V1", context(),
                List.of("第一个片段", "第二个片段"));

        String json = objectMapper.writeValueAsString(batch);
        AiEmbeddingRequest restored = objectMapper.readValue(json, AiEmbeddingRequest.class);

        assertThat(validator.validate(single)).isEmpty();
        assertThat(validator.validate(batch)).isEmpty();
        assertThat(restored.inputs()).containsExactly("第一个片段", "第二个片段");
        assertThat(json).doesNotContain("provider", "baseUrl", "token", "secret");
    }

    @Test
    void shouldSerializeDimensionUsageAndCallId() throws Exception {
        AiEmbeddingResponse response = new AiEmbeddingResponse("call-1", "RAG_V1", "embedding-3",
                3, List.of(new AiEmbeddingVector(0, List.of(0.1F, -0.2F, 0.3F))),
                new AiUsage(4L, 0L, null, null, null, null, 4L, AiMetricCompleteness.COMPLETE),
                AiCost.unknown());

        AiEmbeddingResponse restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(response), AiEmbeddingResponse.class);

        assertThat(validator.validate(response)).isEmpty();
        assertThat(restored.callId()).isEqualTo("call-1");
        assertThat(restored.dimension()).isEqualTo(3);
        assertThat(restored.vectors().get(0).values()).containsExactly(0.1F, -0.2F, 0.3F);
        assertThat(restored.usage().inputTokens()).isEqualTo(4L);
    }

    @Test
    void shouldRejectEmptyOversizedAndMalformedVectors() {
        AiEmbeddingRequest empty = new AiEmbeddingRequest("RAG_V1", context(), List.of(" "));
        AiEmbeddingRequest oversized = new AiEmbeddingRequest("RAG_V1", context(),
                java.util.Collections.nCopies(AiEmbeddingRequest.MAX_BATCH_SIZE + 1, "x"));
        AiEmbeddingResponse wrongDimension = new AiEmbeddingResponse("call-1", "RAG_V1", "embedding-3",
                3, List.of(new AiEmbeddingVector(0, List.of(0.1F, 0.2F))), null, null);
        AiEmbeddingResponse nonFinite = new AiEmbeddingResponse("call-2", "RAG_V1", "embedding-3",
                1, List.of(new AiEmbeddingVector(0, List.of(Float.NaN))), null, null);

        assertThat(validator.validate(empty)).isNotEmpty();
        assertThat(validator.validate(oversized)).isNotEmpty();
        assertThat(validator.validate(wrongDimension)).isNotEmpty();
        assertThat(validator.validate(nonFinite)).isNotEmpty();
    }

    private AiCallContext context() {
        return new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.RETRIEVE_CONTEXT, "rag-query:1", null, null,
                "MODEL_CFG_RAG_V1", null, AiCallPriority.P0, "rag-query:1",
                Instant.parse("2099-01-01T00:00:00Z"));
    }
}
