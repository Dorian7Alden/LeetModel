package com.leetmodel.assistant.rag.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 自动化测试使用的确定性内存向量 Store，不作为生产降级路径。 */
@Component
@ConditionalOnProperty(prefix = "assistant.rag", name = "store-type", havingValue = "IN_MEMORY")
public class InMemoryRagVectorSearchStore implements RagVectorSearchStore {

    private volatile List<Entry> entries = List.of();

    public void replace(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    @Override
    public List<RagVectorHit> search(List<Float> queryVector, int topK) {
        return searchEntries(queryVector, topK, null);
    }

    @Override
    public List<RagVectorHit> search(List<Float> queryVector, int topK, String ragIndexVersion) {
        return searchEntries(queryVector, topK, ragIndexVersion);
    }

    private List<RagVectorHit> searchEntries(List<Float> queryVector, int topK,
                                             String ragIndexVersion) {
        List<RagVectorHit> hits = new ArrayList<>();
        for (Entry entry : entries) {
            if (ragIndexVersion != null
                    && !ragIndexVersion.equals(entry.hit().ragIndexVersion())) continue;
            if (entry.vector().size() != queryVector.size()) {
                throw new RagStoreException("内存 Store 向量维度不匹配", false, null);
            }
            double score = (1D + cosine(queryVector, entry.vector())) / 2D;
            RagVectorHit hit = entry.hit();
            hits.add(new RagVectorHit(hit.chunkId(), hit.documentId(), hit.content(), score,
                    hit.sourcePath(), hit.title(), hit.ragIndexVersion(), hit.estimatedTokens()));
        }
        return hits.stream().sorted(Comparator.comparingDouble(RagVectorHit::score).reversed())
                .limit(topK).toList();
    }

    @Override
    public boolean isVersionReady(String ragIndexVersion, int expectedDimension) {
        return entries.stream().anyMatch(entry -> entry.vector().size() == expectedDimension
                && ragIndexVersion.equals(entry.hit().ragIndexVersion()));
    }

    private double cosine(List<Float> left, List<Float> right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.size(); index++) {
            dot += left.get(index) * right.get(index);
            leftNorm += left.get(index) * left.get(index);
            rightNorm += right.get(index) * right.get(index);
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    public record Entry(List<Float> vector, RagVectorHit hit) {
        public Entry {
            vector = List.copyOf(vector);
        }
    }
}
