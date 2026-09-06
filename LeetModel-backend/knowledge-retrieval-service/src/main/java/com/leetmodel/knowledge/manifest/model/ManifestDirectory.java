package com.leetmodel.knowledge.manifest.model;

import com.leetmodel.knowledge.manifest.dto.DirectoryTagsYaml;

import java.util.List;

/**
 * 内存中的自包含目录 Manifest 领域对象。
 */
public record ManifestDirectory(
        String directoryName,
        String path,
        String title,
        String description,
        DirectoryTagsYaml tags,
        List<ManifestDocument> documents) {

    public ManifestDirectory {
        if (directoryName == null || directoryName.isBlank()) {
            throw new IllegalArgumentException("目录名称不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("目录标题不能为空");
        }
        if (documents == null) documents = List.of();
    }
}
