package com.leetmodel.review.vo;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @Builder
public class ReviewVO {
    private Long taskId; private Long submissionId; private String status; private String workflowVersion;
    private Integer retryCount; private String errorMessage; private BigDecimal totalScore;
    private String resultJson; private LocalDateTime finishedAt;
}
