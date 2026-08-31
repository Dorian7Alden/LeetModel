package com.leetmodel.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_v2_result")
public class ReviewV2Result extends BaseEntity {
    private Long taskId;
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private Long parseArtifactId;
    private String workflowVersion;
    private String resultSchemaVersion;
    private String scoringRuleVersion;
    private BigDecimal score;
    private String resultJson;
    private String modelName;
    private String aiCallId;
}
