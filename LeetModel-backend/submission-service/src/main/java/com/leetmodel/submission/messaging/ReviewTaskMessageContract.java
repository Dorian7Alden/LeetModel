package com.leetmodel.submission.messaging;

/**
 * 提交触发评审链路的稳定消息契约。
 */
public final class ReviewTaskMessageContract {

    /** 逻辑 Topic。 */
    public static final String TOPIC = "review-task-v1";
    /** 事件类型与 Tag。 */
    public static final String EVENT_TYPE = "REVIEW_TASK_READY";
    /** 消费组。 */
    public static final String CONSUMER_GROUP = "cg-ai-review-task-v1";
    /** 当前正式评审工作流。 */
    public static final String WORKFLOW_VERSION = "EVIDENCE_REVIEW_V2";

    private ReviewTaskMessageContract() {
    }

    /**
     * 返回一次逻辑评审动作的稳定幂等键。
     *
     * @param submissionId 提交标识
     * @param workflowVersion 工作流版本
     * @return 幂等键
     */
    public static String idempotencyKey(Long submissionId, String workflowVersion) {
        return "review:" + submissionId + ":" + workflowVersion;
    }
}
