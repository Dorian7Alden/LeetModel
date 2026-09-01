package com.leetmodel.common.core.logging;

/** 关键运行阶段的稳定事件编码；编码描述事件类别，不包含实例或业务标识。 */
public final class LogEventCodes {

    public static final String UNCLASSIFIED = "UNCLASSIFIED";
    public static final String HTTP_REQUEST_COMPLETED = "HTTP_REQUEST_COMPLETED";
    public static final String HTTP_REQUEST_FAILED = "HTTP_REQUEST_FAILED";
    public static final String OUTBOX_LEASE_LOST = "OUTBOX_LEASE_LOST";
    public static final String OUTBOX_PUBLISH_BLOCKED = "OUTBOX_PUBLISH_BLOCKED";
    public static final String OUTBOX_PUBLISH_RETRY = "OUTBOX_PUBLISH_RETRY";
    public static final String INBOX_MESSAGE_CONSUMED = "INBOX_MESSAGE_CONSUMED";
    public static final String INBOX_MESSAGE_DUPLICATE = "INBOX_MESSAGE_DUPLICATE";
    public static final String INBOX_MESSAGE_FAILED = "INBOX_MESSAGE_FAILED";
    public static final String DOMAIN_TASK_CLAIMED = "DOMAIN_TASK_CLAIMED";
    public static final String DOMAIN_TASK_COMPLETED = "DOMAIN_TASK_COMPLETED";
    public static final String DOMAIN_TASK_FAILED = "DOMAIN_TASK_FAILED";
    public static final String DOMAIN_TASK_EXECUTOR_REJECTED = "DOMAIN_TASK_EXECUTOR_REJECTED";
    public static final String AI_CALL_COMPLETED = "AI_CALL_COMPLETED";
    public static final String AI_CALL_FAILED = "AI_CALL_FAILED";
    public static final String AI_CALL_RESULT_UNKNOWN = "AI_CALL_RESULT_UNKNOWN";
    public static final String AI_CALL_AUDIT_WRITE_FAILED = "AI_CALL_AUDIT_WRITE_FAILED";

    private LogEventCodes() {
    }
}
