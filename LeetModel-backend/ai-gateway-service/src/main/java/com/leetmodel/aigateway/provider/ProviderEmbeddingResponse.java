package com.leetmodel.aigateway.provider;

import com.leetmodel.common.ai.model.AiCost;
import com.leetmodel.common.ai.model.AiEmbeddingVector;
import com.leetmodel.common.ai.model.AiUsage;

import java.util.List;

/** 供应商适配层返回的 Embedding 事实，不包含网关 callId。 */
public record ProviderEmbeddingResponse(
        String model,
        String providerResponseId,
        List<AiEmbeddingVector> vectors,
        AiUsage usage,
        AiCost cost
) {
}
