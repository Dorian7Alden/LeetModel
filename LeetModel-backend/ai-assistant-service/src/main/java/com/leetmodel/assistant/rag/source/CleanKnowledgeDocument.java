package com.leetmodel.assistant.rag.source;

/** 已清洗且可进入切分阶段的知识文档。 */
public record CleanKnowledgeDocument(KnowledgeDocument source, String content) {

    public CleanKnowledgeDocument {
        if (source == null || content == null || content.isBlank()) {
            throw new IllegalArgumentException("清洗文档及正文不能为空");
        }
    }

    public String sourceCitation() {
        return source.relativePath();
    }
}
