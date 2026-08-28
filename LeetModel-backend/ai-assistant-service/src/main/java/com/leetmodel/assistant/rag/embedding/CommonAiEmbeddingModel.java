package com.leetmodel.assistant.rag.embedding;

import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.ai.model.AiEmbeddingVector;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** LangChain4j 0.34.0 到 common-ai 的项目内 EmbeddingModel 适配器。 */
public final class CommonAiEmbeddingModel implements EmbeddingModel {
    private final AiClient aiClient;
    private final String logicalModel;
    private final int dimension;
    private final AiEmbeddingContextFactory contextFactory;

    public CommonAiEmbeddingModel(AiClient aiClient, String logicalModel, int dimension,
                                  AiEmbeddingContextFactory contextFactory) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient");
        if (logicalModel == null || logicalModel.isBlank()) throw new IllegalArgumentException("logicalModel 不能为空");
        if (dimension <= 0) throw new IllegalArgumentException("dimension 必须为正数");
        this.logicalModel = logicalModel;
        this.dimension = dimension;
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) throw new IllegalArgumentException("Embedding 输入不能为空");
        List<TextSegment> immutableSegments = List.copyOf(segments);
        List<String> inputs = immutableSegments.stream().map(TextSegment::text).toList();
        AiEmbeddingResponse response = aiClient.embed(new AiEmbeddingRequest(logicalModel,
                contextFactory.create(immutableSegments), inputs));
        List<AiEmbeddingVector> vectors = validatedVectors(response, inputs.size());
        List<Embedding> embeddings = vectors.stream()
                .map(vector -> Embedding.from(vector.values())).toList();
        return Response.from(embeddings, tokenUsage(response));
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private List<AiEmbeddingVector> validatedVectors(AiEmbeddingResponse response, int expectedCount) {
        if (response == null || response.dimension() != dimension || response.vectors() == null
                || response.vectors().size() != expectedCount) {
            throw new IllegalStateException("Embedding 网关响应形状无效");
        }
        List<AiEmbeddingVector> sorted = response.vectors().stream()
                .sorted(Comparator.comparingInt(AiEmbeddingVector::index)).toList();
        for (int index = 0; index < sorted.size(); index++) {
            AiEmbeddingVector vector = sorted.get(index);
            if (vector.index() != index || vector.values() == null || vector.values().size() != dimension
                    || !vector.isFinite()) throw new IllegalStateException("Embedding 网关响应向量无效");
        }
        return sorted;
    }

    private TokenUsage tokenUsage(AiEmbeddingResponse response) {
        if (response.usage() == null) return null;
        Integer input = toInteger(response.usage().inputTokens());
        Integer output = toInteger(response.usage().outputTokens());
        Integer total = toInteger(response.usage().totalTokens());
        if (input == null && output == null && total == null) return null;
        return new TokenUsage(input, output, total);
    }

    private Integer toInteger(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }
}
