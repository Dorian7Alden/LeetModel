package com.leetmodel.assistant.rag.embedding;

import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.*;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonAiEmbeddingModelTest {

    @Test
    void shouldMapBatchInIndexOrderAndExposeTokenUsage() {
        AiClient client = mock(AiClient.class);
        when(client.embed(any())).thenReturn(new AiEmbeddingResponse("call-1", "RAG_V1",
                "embedding-model", 3, List.of(
                new AiEmbeddingVector(1, List.of(0F, 1F, 0F)),
                new AiEmbeddingVector(0, List.of(1F, 0F, 0F))),
                new AiUsage(7L, 0L, null, null, null, null, 7L,
                        AiMetricCompleteness.COMPLETE), null));
        CommonAiEmbeddingModel model = new CommonAiEmbeddingModel(client, "RAG_V1", 3,
                segments -> context(AiOperationCode.INDEX_DOCUMENTS));

        var response = model.embedAll(List.of(TextSegment.from("线性规划"), TextSegment.from("图论")));

        assertThat(response.content().get(0).vector()).containsExactly(1F, 0F, 0F);
        assertThat(response.content().get(1).vector()).containsExactly(0F, 1F, 0F);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(response.tokenUsage().outputTokenCount()).isZero();
        assertThat(model.dimension()).isEqualTo(3);
        verify(client).embed(any(AiEmbeddingRequest.class));
    }

    @Test
    void shouldRejectPartialBatchAndDimensionDrift() {
        AiClient client = mock(AiClient.class);
        when(client.embed(any())).thenReturn(new AiEmbeddingResponse("call-1", "RAG_V1",
                "embedding-model", 2, List.of(new AiEmbeddingVector(0, List.of(1F, 0F))), null, null));
        CommonAiEmbeddingModel model = new CommonAiEmbeddingModel(client, "RAG_V1", 3,
                segments -> context(AiOperationCode.RETRIEVE_CONTEXT));

        assertThatThrownBy(() -> model.embed("查询")).isInstanceOf(IllegalStateException.class);
    }

    private static AiCallContext context(AiOperationCode operation) {
        return new AiCallContext("ai-assistant-service", AiFeatureCode.RAG, operation,
                "rag:1", null, null, "MODEL_CFG_RAG_V1", null, AiCallPriority.P0,
                "rag:1", Instant.parse("2099-01-01T00:00:00Z"));
    }
}
