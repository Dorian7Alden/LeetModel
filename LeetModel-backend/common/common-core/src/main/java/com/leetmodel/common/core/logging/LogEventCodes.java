package com.leetmodel.common.core.logging;

/** 关键运行阶段的稳定事件编码；编码描述事件类别，不包含实例或业务标识。 */
public final class LogEventCodes {

    public static final String UNCLASSIFIED                  = "UNCLASSIFIED";                  // 未分类兜底事件
    public static final String HTTP_REQUEST_COMPLETED        = "HTTP_REQUEST_COMPLETED";        // HTTP 请求正常完成
    public static final String HTTP_REQUEST_FAILED           = "HTTP_REQUEST_FAILED";           // HTTP 请求处理失败
    public static final String OUTBOX_LEASE_LOST             = "OUTBOX_LEASE_LOST";             // 事务 Outbox 消息租约丢失
    public static final String OUTBOX_PUBLISH_BLOCKED        = "OUTBOX_PUBLISH_BLOCKED";        // 事务 Outbox 消息永久阻断
    public static final String OUTBOX_PUBLISH_RETRY          = "OUTBOX_PUBLISH_RETRY";          // 事务 Outbox 消息调度重试
    public static final String INBOX_MESSAGE_CONSUMED        = "INBOX_MESSAGE_CONSUMED";        // 消费端 Inbox 消息成功处理
    public static final String INBOX_MESSAGE_DUPLICATE       = "INBOX_MESSAGE_DUPLICATE";       // 消费端 Inbox 重复消息抑制
    public static final String INBOX_MESSAGE_FAILED          = "INBOX_MESSAGE_FAILED";          // 消费端 Inbox 事务处理失败
    public static final String DOMAIN_TASK_CLAIMED           = "DOMAIN_TASK_CLAIMED";           // 领域租约任务被当前 Worker 领取
    public static final String DOMAIN_TASK_COMPLETED         = "DOMAIN_TASK_COMPLETED";         // 领域租约任务执行成功
    public static final String DOMAIN_TASK_FAILED            = "DOMAIN_TASK_FAILED";            // 领域租约任务尝试失败
    public static final String DOMAIN_TASK_EXECUTOR_REJECTED = "DOMAIN_TASK_EXECUTOR_REJECTED"; // 线程池满载拒绝领域任务
    public static final String AI_CALL_COMPLETED             = "AI_CALL_COMPLETED";             // AI 大模型调用成功完成
    public static final String AI_CALL_FAILED                = "AI_CALL_FAILED";                // AI 大模型调用失败
    public static final String AI_CALL_RESULT_UNKNOWN        = "AI_CALL_RESULT_UNKNOWN";        // AI 调用超时且上游状态未知
    public static final String AI_CALL_AUDIT_WRITE_FAILED    = "AI_CALL_AUDIT_WRITE_FAILED";    // AI 调用审计记录落库失败
    public static final String DEPENDENCY_CALL_FAILED        = "DEPENDENCY_CALL_FAILED";        // 外部下游依赖调用失败
    public static final String DEPENDENCY_CALL_RECOVERED     = "DEPENDENCY_CALL_RECOVERED";     // 外部下游依赖调用重试恢复
    public static final String REQUEST_REJECTED              = "REQUEST_REJECTED";              // 业务校验或参数不合规阻断
    public static final String SYSTEM_FAILURE                = "SYSTEM_FAILURE";                // 未捕获的系统内部严重故障
    public static final String STORAGE_OPERATION_COMPLETED   = "STORAGE_OPERATION_COMPLETED";   // 对象存储操作成功完成
    public static final String STORAGE_OPERATION_FAILED      = "STORAGE_OPERATION_FAILED";      // 对象存储操作发生失败
    public static final String CAPACITY_PROTECTION_ACTIVATED = "CAPACITY_PROTECTION_ACTIVATED"; // 触发服务过载保护限流

    private LogEventCodes() {
    }
}
