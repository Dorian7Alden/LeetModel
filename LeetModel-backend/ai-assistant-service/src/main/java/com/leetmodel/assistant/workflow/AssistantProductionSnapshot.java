package com.leetmodel.assistant.workflow;

/** 一条正式客服回复从开始到重试都不可变的生产执行快照。 */
public record AssistantProductionSnapshot(
        String productionConfigVersion,
        long productionRevision,
        String workflowVersion,
        String promptVersion,
        String modelExecutionConfigVersion,
        String ragMode,
        String ragIndexVersion) {
}
