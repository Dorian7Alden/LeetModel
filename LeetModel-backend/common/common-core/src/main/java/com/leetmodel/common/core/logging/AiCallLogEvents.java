package com.leetmodel.common.core.logging;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

/** AI 调用阶段日志；只记录调度与计量关联事实，不记录请求或响应正文。 */
public final class AiCallLogEvents {

    private AiCallLogEvents() {
    }

    public static void completed(Logger logger, String callId, String callType,
                                 String priority, long durationMs) {
        base(logger.atInfo(), LogEventCodes.AI_CALL_COMPLETED, callId, callType, priority)
                .addKeyValue(LogFieldNames.DURATION_MS, Math.max(0, durationMs))
                .addKeyValue(LogFieldNames.OUTCOME, "succeeded")
                .log("AI call completed");
    }

    public static void failed(Logger logger, String callId, String callType, String priority,
                              String errorCode, long durationMs, Throwable failure) {
        LoggingEventBuilder event = base(logger.atWarn(), LogEventCodes.AI_CALL_FAILED,
                callId, callType, priority)
                .addKeyValue(LogFieldNames.ERROR_CODE, errorCode)
                .addKeyValue(LogFieldNames.DURATION_MS, Math.max(0, durationMs))
                .addKeyValue(LogFieldNames.OUTCOME, "failed");
        if (failure != null) event.setCause(failure);
        event.log("AI call failed");
    }

    public static void resultUnknown(Logger logger, String callId, String callType,
                                     String priority, Object taskId, Integer attemptNo) {
        LoggingEventBuilder event = base(logger.atError(), LogEventCodes.AI_CALL_RESULT_UNKNOWN,
                callId, callType, priority)
                .addKeyValue(LogFieldNames.ERROR_CODE, "AI_UPSTREAM_RESULT_UNKNOWN")
                .addKeyValue(LogFieldNames.DOMAIN_TASK_ID, taskId)
                .addKeyValue(LogFieldNames.TASK_STATE, "UNKNOWN")
                .addKeyValue(LogFieldNames.OUTCOME, "upstream_result_unknown");
        if (attemptNo != null) event.addKeyValue(LogFieldNames.ATTEMPT_NO, attemptNo);
        event.log("AI upstream result is unknown");
    }

    private static LoggingEventBuilder base(LoggingEventBuilder event, String eventCode,
                                            String callId, String callType, String priority) {
        event.addKeyValue(LogFieldNames.EVENT_CODE, eventCode)
                .addKeyValue(LogFieldNames.AI_CALL_ID, callId)
                .addKeyValue(LogFieldNames.AI_CALL_TYPE, callType);
        if (priority != null) event.addKeyValue(LogFieldNames.AI_PRIORITY, priority);
        return event;
    }
}
