package com.leetmodel.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_v3_result")
public class ReviewV3Result extends BaseEntity {
    private Long taskId;
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private Long parseArtifactId;
    private String workflowVersion;
    private String resultSchemaVersion;
    private String scoringRuleVersion;
    private BigDecimal score;
    private BigDecimal phase1Score;
    private BigDecimal phase2Score;
    private String phase1Json;
    private String taskPlanJson;
    private String phase2Json;
    private String resultJson;
    private String modelName;
    private String aiCallId;
}
