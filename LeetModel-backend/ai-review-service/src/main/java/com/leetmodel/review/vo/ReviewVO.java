package com.leetmodel.review.vo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Data @Builder
public class ReviewVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    private String status; private String workflowVersion;
    private String versionName; private String versionDescription; private String processSummary;
    private Integer retryCount; private Integer attemptNo; private String errorMessage; private BigDecimal score;
    private String resultJson; private String modelName; private String aiCallId; private LocalDateTime finishedAt;
}
