package com.leetmodel.assistant.rag.source;

import java.util.List;

/** 批量加载结果；调用方必须显式处理 failures 后才能建立索引。 */
public record KnowledgeLoadResult(
        List<KnowledgeDocument> documents,
        List<KnowledgeLoadFailure> failures) {

    public KnowledgeLoadResult {
        documents = List.copyOf(documents);
        failures = List.copyOf(failures);
    }

    public boolean successful() {
        return failures.isEmpty();
    }
}
