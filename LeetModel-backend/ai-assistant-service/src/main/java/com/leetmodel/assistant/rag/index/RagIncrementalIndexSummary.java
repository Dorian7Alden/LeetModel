package com.leetmodel.assistant.rag.index;

/** 增量索引的非敏感执行汇总。 */
public record RagIncrementalIndexSummary(
        int documentCount,
        int chunkCount,
        int addedCount,
        int modifiedCount,
        int deletedCount,
        int failureCount,
        String ragIndexVersion) {
}
