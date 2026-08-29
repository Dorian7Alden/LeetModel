package com.leetmodel.evaluation.runner;

import com.leetmodel.evaluation.model.ValidatedSamplePayload;

/** Runner 执行一个确定评价槽位所需的不可变上下文。 */
public record EvaluationExperimentCommand(
        String experimentRunId,
        ValidatedSamplePayload sample,
        String workflowVersion,
        String modelExecutionConfigVersion,
        String ragIndexVersion,
        String priority
) {
}
