package com.leetmodel.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_parse_artifact")
public class PaperParseArtifact extends BaseEntity {
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
