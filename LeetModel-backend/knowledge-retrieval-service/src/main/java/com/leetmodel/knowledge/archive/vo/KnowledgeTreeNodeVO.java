package com.leetmodel.knowledge.archive.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库大纲与标签树视图响应节点。
 */
@Data
public class KnowledgeTreeNodeVO {

    private String name;
    private String path;
    private String title;
    private String description;
    private int documentCount;
    private List<String> tags = new ArrayList<>();
    private List<KnowledgeTreeNodeVO> children = new ArrayList<>();
    private List<KnowledgeDocumentItemVO> documents = new ArrayList<>();

    @Data
    public static class KnowledgeDocumentItemVO {
        private String file;
        private String path;
        private String title;
        private String summary;
        private String authorityLevel;
        private List<String> docTags = new ArrayList<>();
        private List<String> methods = new ArrayList<>();
        private int estimatedTokens;
    }
}
