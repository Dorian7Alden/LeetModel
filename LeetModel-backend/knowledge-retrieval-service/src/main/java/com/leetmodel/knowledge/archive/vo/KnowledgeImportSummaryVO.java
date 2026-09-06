package com.leetmodel.knowledge.archive.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库 ZIP 自包含导入报告视图对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeImportSummaryVO {

    private int totalDirectories;
    private int totalDocuments;
    private int totalTags;
    private String manifestVersion;
    private String status;
    private String message;
}
