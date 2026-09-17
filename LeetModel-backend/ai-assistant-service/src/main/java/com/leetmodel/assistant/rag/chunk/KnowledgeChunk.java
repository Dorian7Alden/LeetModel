package com.leetmodel.assistant.rag.chunk;

import com.leetmodel.assistant.rag.source.KnowledgeDocument;

/** 一段待 Embedding 的知识文本。 */
public record KnowledgeChunk(
        KnowledgeDocument source,
        int ordinal,
        String content,
        int estimatedTokens) {

    public KnowledgeChunk {
        if (source == null || ordinal < 0 || content == null || content.isBlank() || estimatedTokens < 1) {
            throw new IllegalArgumentException("知识片段字段非法");
        }
    }
}
