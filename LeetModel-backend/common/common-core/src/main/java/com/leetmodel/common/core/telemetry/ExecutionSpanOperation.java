package com.leetmodel.common.core.telemetry;

/**
 * 异步物理执行边界的固定 SkyWalking operation。
 *
 * <p>只允许在此目录增加 operation，避免把 Topic、任务标识、URL 或其他运行时值
 * 拼进 operation name 造成高基数。</p>
 */
public enum ExecutionSpanOperation {
    /** Outbox Relay 的一次真实发布尝试。 */
    OUTBOX_PUBLISH("Messaging/OutboxPublishAttempt"),
    /** Inbox 去重与领域落库组成的消费短事务。 */
    INBOX_CONSUME("Messaging/InboxConsumeAttempt"),
    /** 评审任务的一次租约执行。 */
    REVIEW_WORKER("Worker/ReviewAttempt"),
    /** 论文建议任务的一次租约执行。 */
    SUGGESTION_WORKER("Worker/SuggestionAttempt"),
    /** 评价运行的一次租约执行。 */
    EVALUATION_WORKER("Worker/EvaluationAttempt"),
    /** 排行重建的一次租约执行。 */
    RANKING_REBUILD_WORKER("Worker/RankingRebuildAttempt"),
    /** AI 队列的一次 provider 物理调用。 */
    AI_PROVIDER("AI/ProviderAttempt"),
    /** 过期 AI 租约的一次恢复判定。 */
    AI_RECOVERY("AI/RecoveryAttempt");

    private final String operationName;

    ExecutionSpanOperation(String operationName) {
        this.operationName = operationName;
    }

    /**
     * 获取固定的低基数 SkyWalking 操作名称。
     *
     * @return 预定义的标准操作名称字符串，不包含任何动态业务或运行时标识
     */
    public String operationName() {
        return operationName;
    }
}
