package com.leetmodel.review.workflow.v2;

import java.math.BigDecimal;
import java.util.List;

/** EVIDENCE_REVIEW_V2 的稳定结果契约。 */
public record EvidenceReviewV2Output(
        BigDecimal score,
        String scoreNature,
        String overallAssessment,
        ScoringRule scoringRule,
        List<RequirementCoverage> requirementCoverage,
        List<ScoringDimension> dimensions,
        List<Finding> findings,
        List<PaperEvidence> evidence,
        List<String> limitations
) {
    public record ScoringRule(String version, String name, String statement) {}

    public record RequirementCoverage(String requirementId, String requirement,
                                      String status, String explanation,
                                      List<String> evidenceIds) {}

    public record ScoringDimension(String dimensionId, String name, BigDecimal maxScore,
                                   BigDecimal score, String reason,
                                   List<String> positiveFindingIds,
                                   List<String> deductionFindingIds) {}

    public record Finding(String findingId, String type, String category, String severity,
                          String statement, String scoreImpact, List<String> evidenceIds) {}

    public record PaperEvidence(String evidenceId, int physicalPage, String section,
                                String blockId, String observation, String quote) {}
}
