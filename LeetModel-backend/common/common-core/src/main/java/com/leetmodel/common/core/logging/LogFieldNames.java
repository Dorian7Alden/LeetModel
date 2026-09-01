package com.leetmodel.common.core.logging;

/**
 * 统一结构化日志字段名。
 *
 * <p>调用方只能从这里选择低基数、非正文的事实字段；日志布局仍会执行最终白名单过滤。</p>
 */
public final class LogFieldNames {

    public static final String EVENT_CODE = "eventCode";
    public static final String TRACE_ID = "traceId";
    public static final String SW_TRACE_ID = "swTraceId";
    public static final String SW_SPAN_ID = "swSpanId";
    public static final String REQUEST_ID = "requestId";
    public static final String OPERATION_ID = "operationId";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String ROUTE_TEMPLATE = "routeTemplate";
    public static final String STATUS_CODE = "statusCode";
    public static final String DURATION_MS = "durationMs";
    public static final String ERROR_CODE = "errorCode";
    public static final String BUSINESS_TYPE = "businessType";
    public static final String BUSINESS_ID = "businessId";
    public static final String DOMAIN_TASK_ID = "domainTaskId";
    public static final String ATTEMPT_NO = "attemptNo";
    public static final String EVENT_ID = "eventId";
    public static final String AI_CALL_ID = "aiCallId";
    public static final String MESSAGE_TOPIC = "messageTopic";
    public static final String CONSUMER_GROUP = "consumerGroup";
    public static final String RETRY_COUNT = "retryCount";
    public static final String SUPPRESSED_COUNT = "suppressedCount";
    public static final String TASK_STATE = "taskState";
    public static final String CLAIM_TYPE = "claimType";
    public static final String AI_PRIORITY = "aiPriority";
    public static final String AI_CALL_TYPE = "aiCallType";
    public static final String OUTCOME = "outcome";
    public static final String EXCEPTION_TYPE = "exceptionType";
    public static final String FAILURE_CATEGORY = "failureCategory";

    private LogFieldNames() {
    }
}
