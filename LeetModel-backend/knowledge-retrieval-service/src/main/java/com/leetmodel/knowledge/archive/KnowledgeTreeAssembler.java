package com.leetmodel.knowledge.archive;

import com.leetmodel.knowledge.archive.vo.KnowledgeTreeNodeVO;
import com.leetmodel.knowledge.manifest.model.ManifestDirectory;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Manifest 中按目录保存的条目组装成稳定的文件夹树。
 */
@Component
public class KnowledgeTreeAssembler {

    private static final Comparator<KnowledgeTreeNodeVO> NODE_ORDER = Comparator
            .comparing(KnowledgeTreeNodeVO::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(KnowledgeTreeNodeVO::getPath);

    public List<KnowledgeTreeNodeVO> assemble(List<ManifestDirectory> directories) {
        Map<String, KnowledgeTreeNodeVO> nodesByPath = new LinkedHashMap<>();
        if (directories == null) {
            return List.of();
        }

        for (ManifestDirectory directory : directories) {
            addDirectory(nodesByPath, directory);
        }

        List<KnowledgeTreeNodeVO> roots = connectTree(nodesByPath);
        sortAndAggregate(roots);
        return roots;
    }

    private void addDirectory(
            Map<String, KnowledgeTreeNodeVO> nodesByPath,
            ManifestDirectory directory) {
        String normalizedPath = normalizePath(directory.path());
        if (normalizedPath.isBlank()) {
            normalizedPath = normalizePath(directory.directoryName());
        }

        String[] segments = normalizedPath.split("/");
        StringBuilder currentPath = new StringBuilder();
        for (String segment : segments) {
            if (segment.isBlank()) {
                continue;
            }
            if (!currentPath.isEmpty()) {
                currentPath.append('/');
            }
            currentPath.append(segment);
            String path = currentPath.toString();
            nodesByPath.computeIfAbsent(path, ignored -> createVirtualNode(segment, path));
        }

        KnowledgeTreeNodeVO terminalNode = nodesByPath.get(normalizedPath);
        applyManifestDirectory(terminalNode, directory, normalizedPath);
    }

    private KnowledgeTreeNodeVO createVirtualNode(String name, String path) {
        KnowledgeTreeNodeVO node = new KnowledgeTreeNodeVO();
        node.setName(name);
        node.setPath(path);
        node.setTitle(name);
        node.setVirtual(true);
        return node;
    }

    private void applyManifestDirectory(
            KnowledgeTreeNodeVO node,
            ManifestDirectory directory,
            String normalizedPath) {
        node.setName(directory.directoryName());
        node.setPath(normalizedPath);
        node.setTitle(directory.title());
        node.setDescription(directory.description());
        node.setVirtual(false);
        if (directory.tags() != null && directory.tags().getMethods() != null) {
            node.setTags(new ArrayList<>(directory.tags().getMethods()));
        }

        List<KnowledgeTreeNodeVO.KnowledgeDocumentItemVO> documents = directory.documents().stream()
                .map(this::toDocumentItem)
                .toList();
        node.setDocuments(new ArrayList<>(documents));
        node.setDirectDocumentCount(documents.size());
    }

    private KnowledgeTreeNodeVO.KnowledgeDocumentItemVO toDocumentItem(ManifestDocument document) {
        KnowledgeTreeNodeVO.KnowledgeDocumentItemVO item = new KnowledgeTreeNodeVO.KnowledgeDocumentItemVO();
        item.setFile(document.file());
        item.setPath(document.relativePath());
        item.setTitle(document.title());
        item.setSummary(document.summary());
        item.setAuthorityLevel(document.authorityLevel());
        item.setDocTags(document.docTags());
        item.setMethods(document.methods());
        item.setEstimatedTokens(document.estimatedTokens() != null ? document.estimatedTokens() : 0);
        return item;
    }

    private List<KnowledgeTreeNodeVO> connectTree(Map<String, KnowledgeTreeNodeVO> nodesByPath) {
        List<KnowledgeTreeNodeVO> roots = new ArrayList<>();
        for (KnowledgeTreeNodeVO node : nodesByPath.values()) {
            String parentPath = parentPath(node.getPath());
            KnowledgeTreeNodeVO parent = nodesByPath.get(parentPath);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private int sortAndAggregate(List<KnowledgeTreeNodeVO> nodes) {
        nodes.sort(NODE_ORDER);
        int total = 0;
        for (KnowledgeTreeNodeVO node : nodes) {
            int descendantDocuments = sortAndAggregate(node.getChildren());
            int directDocuments = node.getDocuments().size();
            node.setDirectDocumentCount(directDocuments);
            node.setDocumentCount(directDocuments + descendantDocuments);
            total += node.getDocumentCount();
        }
        return total;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/')
                .replaceAll("/{2,}", "/")
                .replaceAll("^/+|/+$", "")
                .trim();
    }

    private String parentPath(String path) {
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex < 0 ? "" : path.substring(0, separatorIndex);
    }
}
