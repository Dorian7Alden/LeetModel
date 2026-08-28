package com.leetmodel.assistant.rag.retrieval;

import com.leetmodel.assistant.rag.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Query Embedding → ES Top K → 阈值 → 去重 → Token 预算的 RAG V1 检索器。 */
@Component
public class RagRetriever {

    private final RagProperties properties;
    private final EmbeddingModel embeddingModel;
    private final RagVectorSearchStore store;

    public RagRetriever(RagProperties properties,
                        @Qualifier("ragQueryEmbeddingModel") EmbeddingModel embeddingModel,
                        RagVectorSearchStore store) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
        this.store = store;
    }

    public RagRetrievalResult retrieve(String query) {
        if (query == null || query.isBlank()) {
            return RagRetrievalResult.empty();
        }
        Embedding embedding;
        try {
            embedding = embeddingModel.embed(query).content();
        } catch (RuntimeException exception) {
            throw new RagRetrievalException(RagRetrievalException.Type.EMBEDDING, exception);
        }
        if (embedding == null || embedding.vector().length != properties.getEmbeddingDimension()) {
            throw new RagRetrievalException(RagRetrievalException.Type.DIMENSION,
                    new IllegalStateException("Query Embedding 维度不匹配"));
        }
        List<Float> vector = new ArrayList<>(embedding.vector().length);
        for (float value : embedding.vector()) {
            if (!Float.isFinite(value)) {
                throw new RagRetrievalException(RagRetrievalException.Type.DIMENSION,
                        new IllegalStateException("Query Embedding 含非有限值"));
            }
            vector.add(value);
        }

        List<RagVectorHit> hits;
        try {
            hits = store.search(vector, properties.getTopK());
        } catch (RagStoreException exception) {
            throw new RagRetrievalException(exception.isTimeout()
                    ? RagRetrievalException.Type.TIMEOUT : RagRetrievalException.Type.ELASTICSEARCH, exception);
        } catch (RuntimeException exception) {
            throw new RagRetrievalException(RagRetrievalException.Type.ELASTICSEARCH, exception);
        }
        return filter(hits);
    }

    private RagRetrievalResult filter(List<RagVectorHit> hits) {
        List<RagRetrievedChunk> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String version = null;
        int usedTokens = 0;
        for (RagVectorHit hit : hits) {
            if (hit == null || hit.score() < properties.getScoreThreshold()
                    || hit.content() == null || hit.content().isBlank()) {
                continue;
            }
            String duplicateKey = hit.chunkId() == null || hit.chunkId().isBlank()
                    ? hit.documentId() + "\0" + hit.content() : hit.chunkId();
            if (!seen.add(duplicateKey)) {
                continue;
            }
            int tokens = Math.max(1, hit.estimatedTokens());
            if (usedTokens + tokens > properties.getTokenBudget()) {
                continue;
            }
            if (version == null) {
                version = hit.ragIndexVersion();
            } else if (!version.equals(hit.ragIndexVersion())) {
                throw new RagRetrievalException(RagRetrievalException.Type.ELASTICSEARCH,
                        new IllegalStateException("检索结果包含混合索引版本"));
            }
            result.add(new RagRetrievedChunk(hit.content(), hit.score(), hit.sourcePath(), hit.title(),
                    hit.ragIndexVersion(), tokens));
            usedTokens += tokens;
        }
        return result.isEmpty() ? RagRetrievalResult.empty() : new RagRetrievalResult(result, version);
    }
}
