package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * V3 终态全局评审交付产物契约。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepEvidenceReviewV3Output implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal score;
    private String scoreNature;
    private String workflowVersion;
    private String overallAssessment;
    private ScoringRuleMeta scoringRule;
    private List<V3ScoringDimension> dimensions;
    private List<V3Finding> findings;
    private List<V3Observation> observations;
    private List<V3RequirementCoverage> requirementCoverage;
    private List<V3SubTaskSummary> subTaskSummaries;
    private List<FineGrainedAnchor> anchors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoringRuleMeta implements Serializable {
        private static final long serialVersionUID = 1L;
        private String version;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class V3ScoringDimension implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dimensionCode;
        private String dimensionName;
        private BigDecimal maxScore;
        private BigDecimal score;
        private String reason;
        private List<String> positiveFindingIds;
        private List<String> deductionFindingIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class V3Finding implements Serializable {
        private static final long serialVersionUID = 1L;
        private String findingId;
        private String dimensionCode;
        private String type;
        private String severity;
        private String statement;
        private String scoreImpact;
        private String blockId;
        private Integer physicalPage;
        private List<String> observationIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class V3Observation implements Serializable {
        private static final long serialVersionUID = 1L;
        private String observationId;
        private String blockId;
        private Integer physicalPage;
        private String type;
        private String summary;
        private String rawSnippet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class V3RequirementCoverage implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requirementId;
        private Integer questionNo;
        private String questionTitle;
        private String status;
        private String explanation;
        private List<String> evidenceBlockIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class V3SubTaskSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskId;
        private String taskName;
        private Integer questionNo;
        private BigDecimal score;
        private BigDecimal maxScore;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FineGrainedAnchor implements Serializable {
        private static final long serialVersionUID = 1L;
        private String anchorId;
        private String blockId;
        private Integer physicalPage;
        private String anchorType;
        private String highlightText;
        private String findingId;
    }
}
