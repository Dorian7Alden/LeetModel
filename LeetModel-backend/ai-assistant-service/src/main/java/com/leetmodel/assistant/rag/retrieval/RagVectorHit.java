package com.leetmodel.assistant.rag.retrieval;

/** 向量 Store 返回的原始命中。 */
public record RagVectorHit(
        String chunkId,
        String documentId,
        String content,
        double score,
        String sourcePath,
        String title,
        String ragIndexVersion,
        int estimatedTokens) {
}
