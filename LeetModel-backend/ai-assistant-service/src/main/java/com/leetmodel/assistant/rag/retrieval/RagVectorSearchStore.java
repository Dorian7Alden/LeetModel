package com.leetmodel.assistant.rag.retrieval;

import java.util.List;

/** RAG 向量召回端口。 */
public interface RagVectorSearchStore {

    List<RagVectorHit> search(List<Float> queryVector, int topK);

    default List<RagVectorHit> search(List<Float> queryVector, int topK, String ragIndexVersion) {
        return search(queryVector, topK).stream()
                .filter(hit -> ragIndexVersion.equals(hit.ragIndexVersion())).toList();
    }
}
