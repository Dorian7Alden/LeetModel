package com.leetmodel.assistant.rag.retrieval;

import java.util.List;

/** RAG 向量召回端口。 */
public interface RagVectorSearchStore {

    List<RagVectorHit> search(List<Float> queryVector, int topK);
}
