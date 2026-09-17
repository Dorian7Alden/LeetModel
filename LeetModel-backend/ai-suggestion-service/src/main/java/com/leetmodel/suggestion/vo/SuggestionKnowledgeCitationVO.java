package com.leetmodel.suggestion.vo;

/** 历史建议报告可公开展示的知识引用元数据，不返回被裁剪的知识正文。 */
public record SuggestionKnowledgeCitationVO(
        String citationId,
        String title,
        String sourcePath,
        String contentHash,
        String authorityLevel,
        String applicability
) {}
