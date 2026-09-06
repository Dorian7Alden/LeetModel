package com.leetmodel.knowledge.manifest.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局聚合的知识清单领域对象，提供快速白名单校验与路径查找。
 */
public class KnowledgeManifest {

    private final String manifestVersion;
    private final List<ManifestDirectory> directories;
    private final List<ManifestDocument> allDocuments;
    private final Map<String, ManifestDocument> pathLookup;
    private final Set<String> validRelativePaths;

    public KnowledgeManifest(String manifestVersion, List<ManifestDirectory> directories) {
        this.manifestVersion = manifestVersion;
        this.directories = directories != null ? List.copyOf(directories) : List.of();
        this.allDocuments = this.directories.stream()
                .flatMap(dir -> dir.documents().stream())
                .toList();
        this.pathLookup = this.allDocuments.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ManifestDocument::relativePath,
                        doc -> doc,
                        (existing, replacement) -> existing
                ));
        this.validRelativePaths = Collections.unmodifiableSet(pathLookup.keySet());
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public List<ManifestDirectory> getDirectories() {
        return directories;
    }

    public List<ManifestDocument> getAllDocuments() {
        return allDocuments;
    }

    public boolean isValidPath(String relativePath) {
        if (relativePath == null) return false;
        return validRelativePaths.contains(relativePath.replace('\\', '/'));
    }

    public ManifestDocument findByPath(String relativePath) {
        if (relativePath == null) return null;
        return pathLookup.get(relativePath.replace('\\', '/'));
    }

    public Set<String> getValidRelativePaths() {
        return validRelativePaths;
    }
}
