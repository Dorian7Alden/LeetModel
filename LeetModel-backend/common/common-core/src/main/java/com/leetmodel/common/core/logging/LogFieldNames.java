package com.leetmodel.common.core.logging;

/**
 * 统一结构化日志字段名。
 *
 * <p>调用方只能从这里选择低基数、非正文的事实字段；日志布局仍会执行最终白名单过滤。</p>
 */
public final class LogFieldNames {

    public static final String EVENT_CODE       = "eventCode";       // 稳定业务或系统事件码
    public static final String TRACE_ID         = "traceId";         // 全链路唯一请求追踪 ID
    public static final String SW_TRACE_ID      = "swTraceId";       // SkyWalking 链路追踪 ID
    public static final String SW_SPAN_ID       = "swSpanId";        // SkyWalking Span 标识
    public static final String REQUEST_ID       = "requestId";       // 请求唯一标识
    public static final String OPERATION_ID     = "operationId";     // 运维治理或重放操作 ID
    public static final String HTTP_METHOD      = "httpMethod";      // HTTP 请求动词（GET/POST等）
    public static final String ROUTE_TEMPLATE   = "routeTemplate";   // 匹配的路由路径模板
    public static final String STATUS_CODE      = "statusCode";      // HTTP 状态码
    public static final String DURATION_MS      = "durationMs";      // 执行耗时（毫秒）
    public static final String ERROR_CODE       = "errorCode";       // 业务错误状态码
    public static final String BUSINESS_TYPE    = "businessType";    // 业务所属领域类型
    public static final String BUSINESS_ID      = "businessId";      // 业务实体主键标识
    public static final String DOMAIN_TASK_ID   = "domainTaskId";    // 后台领域任务 ID
    public static final String ATTEMPT_NO       = "attemptNo";       // 任务物理尝试序号
    public static final String EVENT_ID         = "eventId";         // 消息队列事件 ID
    public static final String AI_CALL_ID       = "aiCallId";        // AI 单次调用标识
    public static final String MESSAGE_TOPIC    = "messageTopic";    // 消息队列主题
    public static final String CONSUMER_GROUP   = "consumerGroup";   // 消息消费组名称
    public static final String RETRY_COUNT      = "retryCount";      // 重试累计次数
    public static final String SUPPRESSED_COUNT = "suppressedCount"; // 日志限频期间被抑制次数
    public static final String TASK_STATE       = "taskState";       // 任务当前生命周期状态
    public static final String CLAIM_TYPE       = "claimType";       // 租约抢占类型（normal/takeover）
    public static final String AI_PRIORITY      = "aiPriority";      // AI 调度优先级（P0-P4）
    public static final String AI_CALL_TYPE     = "aiCallType";      // AI 调用类型（CHAT/EMBEDDING）
    public static final String OUTCOME          = "outcome";         // 最终结果分类
    public static final String EXCEPTION_TYPE   = "exceptionType";   // 异常类全限定名
    public static final String FAILURE_CATEGORY = "failureCategory"; // 失败类别标签

    private LogFieldNames() {
    }
}
