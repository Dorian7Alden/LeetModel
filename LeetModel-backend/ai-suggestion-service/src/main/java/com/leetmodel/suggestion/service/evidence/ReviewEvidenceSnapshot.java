package com.leetmodel.suggestion.service.evidence;

import java.util.List;

/** 原生 V2 发现或历史 V1 确定性投影形成的不可变评审依据。 */
public record ReviewEvidenceSnapshot(
        Long eligibilityReviewTaskId,
        Long evidenceReviewTaskId,
        String reviewWorkflowVersion,
        String projectionVersion,
        List<Finding> findings,
        String snapshotJson
) {
    public record Finding(String findingId, String type, String category, String severity,
                          String statement, String scoreImpact, String sourcePath,
                          List<String> paperEvidenceIds) {}
}
