package com.leetmodel.knowledge.archive.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库物理索引状态响应实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIndexStatusVO {

    private String indexAlias;
    private String activeIndexVersion;
    private int totalDocuments;
    private int totalChunks;
    private boolean healthy;
}
