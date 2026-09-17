package com.leetmodel.evaluation.runner;

import java.util.Map;

/** Runner 对通用实验结果的业务解释，不携带 feature 专用 DTO。 */
public record EvaluationExperimentOutcome(
        String experimentRunId,
        String featureCode,
        String workflowVersion,
        String modelExecutionConfigVersion,
        String ragIndexVersion,
        String status,
        String failureType,
        String outputSummaryJson,
        String modelName,
        String aiCallId,
        Long durationMs,
        String errorMessage,
        Map<String, String> rawMetrics
) {
}
