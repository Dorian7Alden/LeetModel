package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.chunk.KnowledgeChunk;

/** 带稳定标识和索引版本的知识片段。 */
public record VersionedKnowledgeChunk(
        String documentId,
        String chunkId,
        KnowledgeChunk chunk,
        RagVersionSet versions) {
}
