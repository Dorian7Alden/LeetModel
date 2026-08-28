package com.leetmodel.assistant.rag.retrieval;

/** 经过阈值、去重和预算裁剪后可注入工作流的知识片段。 */
public record RagRetrievedChunk(
        String content,
        double score,
        String sourcePath,
        String title,
        String ragIndexVersion,
        int estimatedTokens) {
}
