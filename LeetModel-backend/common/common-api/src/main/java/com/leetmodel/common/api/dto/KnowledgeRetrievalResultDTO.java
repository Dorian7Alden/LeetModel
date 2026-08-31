package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 一次知识检索运行及其实际版本快照。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalResultDTO {
    private String retrievalRunId;
    private String workflowVersion;
    private String executionBranch;
    private String indexVersion;
    private String manifestVersion;
    private String sourceVersion;
    private String status;
    private List<KnowledgeCitationDTO> citations;
}
