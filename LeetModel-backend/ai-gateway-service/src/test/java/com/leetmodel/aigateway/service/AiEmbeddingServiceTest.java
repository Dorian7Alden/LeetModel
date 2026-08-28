package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.AiEmbeddingProperties;
import com.leetmodel.aigateway.provider.AiProviderAdapter;
import com.leetmodel.aigateway.provider.ProviderEmbeddingResponse;
import com.leetmodel.common.ai.model.*;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiEmbeddingServiceTest {
    private AiProviderAdapter adapter;
    private AiEmbeddingService service;

    @BeforeEach
    void setUp() {
        AiEmbeddingProperties properties = new AiEmbeddingProperties();
        AiEmbeddingProperties.Binding binding = new AiEmbeddingProperties.Binding();
        binding.setProvider(AiProvider.NEW_API);
        binding.setModel("embedding-model");
        binding.setDimension(3);
        binding.setMaxBatchSize(2);
        binding.setMaxInputChars(5);
        binding.setMaxTotalChars(8);
        properties.getEmbeddingModels().put("RAG_V1", binding);

        adapter = mock(AiProviderAdapter.class);
        when(adapter.provider()).thenReturn(AiProvider.NEW_API);
        service = new AiEmbeddingService(properties, new AiProviderRegistry(List.of(adapter)));
    }

    @Test
    void shouldResolveLogicalModelAndReturnGatewayCallId() {
        when(adapter.embed("embedding-model", List.of("中文"))).thenReturn(response(1, 3));

        AiEmbeddingResponse result = service.embed(request(List.of("中文"), AiOperationCode.RETRIEVE_CONTEXT));

        assertThat(result.callId()).isNotBlank();
        assertThat(result.logicalModel()).isEqualTo("RAG_V1");
        assertThat(result.dimension()).isEqualTo(3);
    }

    @Test
    void shouldRejectUnknownModelEmptyContextAndInputLimits() {
        assertThatThrownBy(() -> service.embed(new AiEmbeddingRequest("UNKNOWN", context(
                AiOperationCode.RETRIEVE_CONTEXT), List.of("x")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.embed(request(List.of("123456"), AiOperationCode.RETRIEVE_CONTEXT)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.embed(request(List.of("12345", "6789"), AiOperationCode.INDEX_DOCUMENTS)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.embed(request(List.of("1", "2", "3"), AiOperationCode.INDEX_DOCUMENTS)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectWrongDimensionDuplicateIndexAndWrongOperation() {
        when(adapter.embed(anyString(), anyList())).thenReturn(response(1, 2));
        assertThatThrownBy(() -> service.embed(request(List.of("x"), AiOperationCode.RETRIEVE_CONTEXT)))
                .isInstanceOf(BusinessException.class);

        when(adapter.embed(anyString(), anyList())).thenReturn(new ProviderEmbeddingResponse(
                "embedding-model", null, List.of(new AiEmbeddingVector(0, List.of(1F, 2F, 3F)),
                new AiEmbeddingVector(0, List.of(1F, 2F, 3F))), null, null));
        assertThatThrownBy(() -> service.embed(request(List.of("x", "y"), AiOperationCode.INDEX_DOCUMENTS)))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.embed(request(List.of("x"), AiOperationCode.CHAT_REPLY)))
                .isInstanceOf(BusinessException.class);
    }

    private ProviderEmbeddingResponse response(int count, int dimension) {
        return new ProviderEmbeddingResponse("embedding-model", "provider-1",
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> new AiEmbeddingVector(index,
                                java.util.Collections.nCopies(dimension, 0.1F))).toList(), null, null);
    }

    private AiEmbeddingRequest request(List<String> inputs, AiOperationCode operation) {
        return new AiEmbeddingRequest("RAG_V1", context(operation), inputs);
    }

    private AiCallContext context(AiOperationCode operation) {
        return new AiCallContext("ai-assistant-service", operation == AiOperationCode.CHAT_REPLY
                ? AiFeatureCode.AI_ASSISTANT : AiFeatureCode.RAG, operation, "task:1", null, null,
                "MODEL_CFG_RAG_V1", null, AiCallPriority.P0, "embedding:1",
                Instant.parse("2099-01-01T00:00:00Z"));
    }
}
