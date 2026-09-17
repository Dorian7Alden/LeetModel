package com.leetmodel.assistant.rag.index;

/** 一次索引构建的四类可查询版本。 */
public record RagVersionSet(
        String contentVersion,
        String embeddingModelVersion,
        String chunkPolicyVersion,
        String ragIndexVersion) {
}
