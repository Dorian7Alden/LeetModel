package com.leetmodel.review.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
@Data @EqualsAndHashCode(callSuper = true) @TableName("review_result")
public class ReviewResult extends BaseEntity {
    private Long taskId; private Long submissionId; private Long teamId; private Long problemId;
    private String workflowVersion; private BigDecimal totalScore; private String resultJson;
}
