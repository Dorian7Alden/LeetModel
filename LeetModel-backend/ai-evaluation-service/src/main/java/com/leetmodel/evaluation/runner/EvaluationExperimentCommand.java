package com.leetmodel.evaluation.runner;

import com.leetmodel.evaluation.model.ValidatedSamplePayload;

/** Runner 执行一个确定评价槽位所需的不可变上下文。 */
public record EvaluationExperimentCommand(
        String experimentRunId,
        String evaluationTaskId,
        String slotKey,
        Integer attemptNo,
        String idempotencyKey,
        ValidatedSamplePayload sample,
        String workflowVersion,
        String modelExecutionConfigVersion,
        String ragIndexVersion,
        String priority
) {
    /** 兼容 Runner 单元测试和非持久化适配器；生产调度始终提供完整上下文。 */
    public EvaluationExperimentCommand(String experimentRunId, ValidatedSamplePayload sample,
                                       String workflowVersion,
                                       String modelExecutionConfigVersion,
                                       String ragIndexVersion, String priority) {
        this(experimentRunId, null, experimentRunId, 1,
                "evaluation:" + experimentRunId + ":attempt:1", sample, workflowVersion,
                modelExecutionConfigVersion, ragIndexVersion, priority);
    }
}
