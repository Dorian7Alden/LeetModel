package com.leetmodel.knowledge.cache;

import com.leetmodel.common.api.dto.KnowledgeCitationDTO;

import java.util.List;

/**
 * L2 任务语义缓存实体。
 */
public record L2SemanticEntry(
        String entryId,
        String query,
        String category,
        List<Float> embedding,
        List<KnowledgeCitationDTO> citations,
        long createdAt) {
}
