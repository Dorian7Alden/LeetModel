package com.leetmodel.knowledge.manifest.model;

import java.util.List;

/**
 * 内存中经过标签继承与属性聚合的标准化文档清单条目。
 */
public record ManifestDocument(
        String relativePath,
        String file,
        String title,
        String summary,
        List<String> docTags,
        List<String> methods,
        List<String> compositeTags,
        String authorityLevel,
        Integer estimatedTokens,
        String directoryName,
        String directoryPath) {

    public ManifestDocument {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("文档相对路径不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("文档摘要不能为空");
        }
        if (docTags == null) docTags = List.of();
        if (methods == null) methods = List.of();
        if (compositeTags == null) compositeTags = List.of();
        if (authorityLevel == null || authorityLevel.isBlank()) {
            authorityLevel = "L4";
        }
    }
}
