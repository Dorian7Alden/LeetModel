package com.leetmodel.knowledge.archive.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库索引手动重建触发结果响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIndexRebuildVO {

    private String oldIndexVersion;
    private String newIndexVersion;
    private int indexedDocuments;
    private String status;
    private String message;
}
