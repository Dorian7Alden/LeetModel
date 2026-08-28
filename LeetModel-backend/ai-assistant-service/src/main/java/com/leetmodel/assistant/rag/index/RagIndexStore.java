package com.leetmodel.assistant.rag.index;

import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

/** RAG 索引写入端口。 */
public interface RagIndexStore {

    int writeBatch(String indexName, List<VersionedKnowledgeChunk> chunks, List<Embedding> embeddings);
}
