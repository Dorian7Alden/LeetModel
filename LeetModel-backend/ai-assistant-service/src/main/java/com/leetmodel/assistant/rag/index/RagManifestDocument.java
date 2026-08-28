package com.leetmodel.assistant.rag.index;

/** 当前读索引中每篇文档的增量比较信息。 */
public record RagManifestDocument(
        String documentId,
        String sourcePath,
        String contentHash,
        String embeddingModelVersion,
        String chunkPolicyVersion) {
}
