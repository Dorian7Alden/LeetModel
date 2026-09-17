package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** V4 专业 Markdown 论文评审终态契约。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepEvidenceReviewV4Output implements Serializable {
    private static final long serialVersionUID = 1L;

    private String workflowVersion;
    private String resultSchemaVersion;
    private BigDecimal score;
    private String scoreNature;
    private String overallAssessmentMarkdown;
    private DeepEvidenceReviewV3Output.ScoringRuleMeta scoringRule;
    private List<Dimension> dimensions;
    private List<Finding> findings;
    private List<DeepEvidenceReviewV3Output.V3RequirementCoverage> requirementCoverage;
    private List<KnowledgeBasis> knowledgeBasis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimension implements Serializable {
        private static final long serialVersionUID = 1L;
        private String dimensionCode;
        private String dimensionName;
        private BigDecimal score;
        private BigDecimal maxScore;
        private String reasonMarkdown;
        private List<String> strengthFindingIds;
        private List<String> issueFindingIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding implements Serializable {
        private static final long serialVersionUID = 1L;
        private String findingId;
        private String findingType;
        private String priority;
        private String importance;
        private String dimensionCode;
        private String category;
        private String title;
        private String explanationMarkdown;
        private String whyItMattersMarkdown;
        private String scoreImpact;
        private Integer physicalPage;
        private List<String> anchorBlockIds;
        private List<EvidenceQuote> evidenceQuotes;
        private List<String> knowledgeBasisIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceQuote implements Serializable {
        private static final long serialVersionUID = 1L;
        private String evidenceId;
        private String blockId;
        private Integer physicalPage;
        private String sectionTitle;
        private String blockType;
        private String quoteMarkdown;
        private String contentHash;
        private Boolean truncated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeBasis implements Serializable {
        private static final long serialVersionUID = 1L;
        private String basisId;
        private String citationId;
        private String title;
        private String section;
        private String sourcePath;
        private String contentHash;
        private String authorityLevel;
        private String supportMarkdown;
        private String applicabilityMarkdown;
    }
}
