package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 论文 PDF 的不可变版本化解析产物。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperParseDTO {
    private Long artifactId;
    private Long submissionId;
    private String workflowVersion;
    private String schemaVersion;
    private String contentSha256;
    private String status;
    private Integer pageCount;
    private Boolean truncated;
    private String qualityJson;
    private String documentJson;
    private String errorMessage;
}
