package com.leetmodel.review.vo;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @Builder
public class ReviewVO {
    private Long taskId; private Long submissionId; private String status; private String workflowVersion;
    private String versionName; private String versionDescription; private String processSummary;
    private Integer retryCount; private Integer attemptNo; private String errorMessage; private BigDecimal score;
    private String resultJson; private String modelName; private LocalDateTime finishedAt;
}
