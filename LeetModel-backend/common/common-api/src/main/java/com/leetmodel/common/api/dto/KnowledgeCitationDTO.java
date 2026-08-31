package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一条经过路径、预算和适用性校验的知识引用快照。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCitationDTO {
    private String citationId;
    private String documentId;
    private String chunkId;
    private String title;
    private String sourcePath;
    private String section;
    private String contentHash;
    private String authorityLevel;
    private String applicability;
    private Double relevanceScore;
    private String content;
}
