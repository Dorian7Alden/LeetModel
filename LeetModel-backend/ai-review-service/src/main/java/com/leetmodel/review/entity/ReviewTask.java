package com.leetmodel.review.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper = true) @TableName("review_task")
public class ReviewTask extends BaseEntity {
    private Long submissionId; private Long versionId; private Long teamId; private Long problemId;
    private String status; private String workflowVersion;
    private String promptSnapshot; private Integer retryCount; private Integer attemptNo;
    private LocalDateTime nextRunAt; private LocalDateTime startedAt;
    private LocalDateTime finishedAt; private String errorMessage;
    @TableField(exist = false) private String experimentRunId;
    @TableField(exist = false) private String evaluationTaskId;
    @TableField(exist = false) private String experimentIdempotencyKey;
    @TableField(exist = false) private String modelExecutionConfigVersion;
}
