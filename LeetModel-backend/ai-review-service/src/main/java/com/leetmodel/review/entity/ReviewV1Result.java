package com.leetmodel.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_v1_result")
public class ReviewV1Result extends BaseEntity {
    private Long taskId;
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private String workflowVersion;
    private BigDecimal score;
    private String resultJson;
    private String modelName;
    private String aiCallId;
}
