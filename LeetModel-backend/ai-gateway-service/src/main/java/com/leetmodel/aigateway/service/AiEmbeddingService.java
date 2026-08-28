package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.AiEmbeddingProperties;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.provider.ProviderEmbeddingResponse;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.ai.model.AiEmbeddingVector;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.core.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** 统一 Embedding 调用与逻辑模型能力校验。 */
@Service
public class AiEmbeddingService {
    private final AiEmbeddingProperties properties;
    private final AiProviderRegistry providerRegistry;
    private final AiCallAuditService callAuditService;

    public AiEmbeddingService(AiEmbeddingProperties properties, AiProviderRegistry providerRegistry,
                              AiCallAuditService callAuditService) {
        this.properties = properties;
        this.providerRegistry = providerRegistry;
        this.callAuditService = callAuditService;
    }

    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        String callId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        AiEmbeddingProperties.Binding binding = properties.getEmbeddingModels().get(request.logicalModel());
        String provider = binding == null || binding.getProvider() == null
                ? null : binding.getProvider().name();
        String model = binding == null ? null : binding.getModel();
        try {
            BusinessException.throwIf(binding == null || !binding.isEnabled(), AiGatewayErrorCode.MODEL_DISABLED);
            validateContext(request);
            validateInputs(request.inputs(), binding);
            ProviderEmbeddingResponse providerResponse = providerRegistry.get(binding.getProvider())
                    .embed(binding.getModel(), request.inputs());
            validateResponse(request.inputs().size(), binding.getDimension(), providerResponse);
            callAuditService.recordEmbeddingSuccess(callId, request, provider, model, providerResponse,
                    binding.getDimension(), System.currentTimeMillis() - startedAt);
            return new AiEmbeddingResponse(callId, request.logicalModel(), providerResponse.model(),
                    binding.getDimension(), providerResponse.vectors(), providerResponse.usage(),
                    providerResponse.cost());
        } catch (RuntimeException exception) {
            callAuditService.recordEmbeddingFailure(callId, request, provider, model, exception,
                    System.currentTimeMillis() - startedAt);
            throw exception;
        }
    }

    private void validateContext(AiEmbeddingRequest request) {
        boolean ragEmbedding = request.context() != null
                && request.context().featureCode() == AiFeatureCode.RAG
                && (request.context().operationCode() == AiOperationCode.INDEX_DOCUMENTS
                || request.context().operationCode() == AiOperationCode.RETRIEVE_CONTEXT);
        BusinessException.throwIf(!ragEmbedding, AiGatewayErrorCode.CAPABILITY_NOT_SUPPORTED);
    }

    private void validateInputs(List<String> inputs, AiEmbeddingProperties.Binding binding) {
        BusinessException.throwIf(inputs.size() > binding.getMaxBatchSize(),
                AiGatewayErrorCode.EMBEDDING_BATCH_EXCEEDED);
        long total = 0;
        for (String input : inputs) {
            int length = input.codePointCount(0, input.length());
            BusinessException.throwIf(length > binding.getMaxInputChars(),
                    AiGatewayErrorCode.EMBEDDING_INPUT_EXCEEDED);
            total += length;
        }
        BusinessException.throwIf(total > binding.getMaxTotalChars(),
                AiGatewayErrorCode.EMBEDDING_TOTAL_INPUT_EXCEEDED);
    }

    private void validateResponse(int expectedCount, int expectedDimension,
                                  ProviderEmbeddingResponse response) {
        BusinessException.throwIf(response == null || response.model() == null || response.model().isBlank()
                        || response.vectors() == null || response.vectors().size() != expectedCount,
                AiGatewayErrorCode.RESPONSE_INVALID);
        HashSet<Integer> indexes = new HashSet<>();
        for (AiEmbeddingVector vector : response.vectors()) {
            boolean invalid = vector == null || vector.index() < 0 || vector.index() >= expectedCount
                    || vector.values() == null || vector.values().size() != expectedDimension
                    || !vector.isFinite() || !indexes.add(vector.index());
            BusinessException.throwIf(invalid, AiGatewayErrorCode.EMBEDDING_DIMENSION_MISMATCH);
        }
    }
}
