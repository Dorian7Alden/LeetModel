package com.leetmodel.suggestion.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SuggestionVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewTaskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long eligibilityReviewTaskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long evidenceReviewTaskId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parseArtifactId;

    private String workflowVersion;
    private String reviewWorkflowVersion;
    private String reviewEvidenceProjectionVersion;
    private String paperParsingWorkflowVersion;
    private String retrievalRunId;
    private String retrievalWorkflowVersion;
    private String knowledgeIndexVersion;
    private String knowledgeManifestVersion;
    private String knowledgeSourceVersion;
    private String resultSchemaVersion;
    private String status;
    private String currentStage;
    private Integer retryCount;
    private Integer attemptNo;
    private String errorMessage;
    private Object result;
    private List<SuggestionKnowledgeCitationVO> knowledgeCitations;
    private String modelName;
    private String aiCallId;
    private LocalDateTime createTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
