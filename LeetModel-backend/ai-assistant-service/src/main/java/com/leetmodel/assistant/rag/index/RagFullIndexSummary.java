package com.leetmodel.assistant.rag.index;

/** 全量索引命令允许输出的非敏感汇总。 */
public record RagFullIndexSummary(
        int documentCount,
        int chunkCount,
        int failureCount,
        String ragIndexVersion) {
}
