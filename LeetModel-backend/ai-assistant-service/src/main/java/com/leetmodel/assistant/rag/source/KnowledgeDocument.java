package com.leetmodel.assistant.rag.source;

import java.time.Instant;
import java.util.List;

/** 一篇已经安全加载、尚未清洗和切分的知识文档。 */
public record KnowledgeDocument(
        String relativePath,
        String title,
        List<String> tags,
        String summary,
        List<String> hierarchy,
        String contentHash,
        Instant lastModifiedAt,
        long byteSize,
        String content) {
}
