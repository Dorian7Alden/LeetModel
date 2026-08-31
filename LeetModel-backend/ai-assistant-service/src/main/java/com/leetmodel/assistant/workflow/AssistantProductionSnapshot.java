package com.leetmodel.assistant.workflow;

/** 一条正式客服回复从开始到重试都不可变的生产执行快照。 */
public record AssistantProductionSnapshot(
        String productionConfigVersion,
        long productionRevision,
        String workflowVersion,
        String promptVersion,
        String modelExecutionConfigVersion,
        String toolsetVersion,
        String ragMode,
        String ragIndexVersion) {

    /** 兼容不使用工具的旧工作流构造方式。 */
    public AssistantProductionSnapshot(String productionConfigVersion,
                                       long productionRevision,
                                       String workflowVersion,
                                       String promptVersion,
                                       String modelExecutionConfigVersion,
                                       String ragMode,
                                       String ragIndexVersion) {
        this(productionConfigVersion, productionRevision, workflowVersion, promptVersion,
                modelExecutionConfigVersion, null, ragMode, ragIndexVersion);
    }
}
