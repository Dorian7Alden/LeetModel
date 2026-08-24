package com.leetmodel.review.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper = true) @TableName("review_task")
public class ReviewTask extends BaseEntity {
    private Long submissionId; private String status; private String workflowVersion;
    private Integer retryCount; private LocalDateTime nextRunAt; private LocalDateTime startedAt;
    private LocalDateTime finishedAt; private String errorMessage;
}
