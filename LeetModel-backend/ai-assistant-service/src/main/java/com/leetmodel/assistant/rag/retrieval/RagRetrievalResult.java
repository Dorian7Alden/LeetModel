package com.leetmodel.assistant.rag.retrieval;

import java.util.List;

/** 一次基础向量检索结果。 */
public record RagRetrievalResult(List<RagRetrievedChunk> chunks, String ragIndexVersion) {

    public RagRetrievalResult {
        chunks = List.copyOf(chunks);
    }

    public static RagRetrievalResult empty() {
        return new RagRetrievalResult(List.of(), null);
    }
}
