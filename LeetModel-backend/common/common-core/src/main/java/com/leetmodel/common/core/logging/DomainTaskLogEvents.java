package com.leetmodel.common.core.logging;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** 领域租约 Worker 的统一阶段日志，不记录任务输入、结果或租约凭据。 */
public final class DomainTaskLogEvents {

    private static final Set<String> SUCCESS_STATES = Set.of("SUCCEEDED", "COMPLETED", "CANCELLED");

    private DomainTaskLogEvents() {
    }

    public static void claimed(Logger logger, String businessType, Object taskId,
                               Integer attemptNo, boolean takeover) {
        LoggingEventBuilder event = base(logger.atInfo(), businessType, taskId, attemptNo)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DOMAIN_TASK_CLAIMED)
                .addKeyValue(LogFieldNames.TASK_STATE, "LEASED")
                .addKeyValue(LogFieldNames.CLAIM_TYPE, takeover ? "takeover" : "normal")
                .addKeyValue(LogFieldNames.OUTCOME, "claimed");
        event.log("Domain task claimed");
    }

    public static void finished(Logger logger, String businessType, Object taskId,
                                Integer attemptNo, String state, long elapsedNanos) {
        String normalized = normalize(state);
        boolean succeeded = SUCCESS_STATES.contains(normalized);
        LoggingEventBuilder event = base(succeeded ? logger.atInfo() : logger.atWarn(),
                businessType, taskId, attemptNo)
                .addKeyValue(LogFieldNames.EVENT_CODE, succeeded
                        ? LogEventCodes.DOMAIN_TASK_COMPLETED : LogEventCodes.DOMAIN_TASK_FAILED)
                .addKeyValue(LogFieldNames.TASK_STATE, normalized)
                .addKeyValue(LogFieldNames.DURATION_MS,
                        TimeUnit.NANOSECONDS.toMillis(Math.max(0, elapsedNanos)))
                .addKeyValue(LogFieldNames.OUTCOME, succeeded ? "completed" : "failed");
        event.log(succeeded ? "Domain task attempt completed" : "Domain task attempt failed");
    }

    public static void executorRejected(Logger logger, String businessType, Object taskId) {
        base(logger.atWarn(), businessType, taskId, null)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DOMAIN_TASK_EXECUTOR_REJECTED)
                .addKeyValue(LogFieldNames.OUTCOME, "executor_rejected")
                .log("Domain task executor rejected attempt");
    }

    private static LoggingEventBuilder base(LoggingEventBuilder event, String businessType,
                                            Object taskId, Integer attemptNo) {
        event.addKeyValue(LogFieldNames.BUSINESS_TYPE, businessType)
                .addKeyValue(LogFieldNames.DOMAIN_TASK_ID, taskId);
        if (attemptNo != null) event.addKeyValue(LogFieldNames.ATTEMPT_NO, attemptNo);
        return event;
    }

    private static String normalize(String state) {
        return state == null || state.isBlank() ? "UNKNOWN" : state.trim().toUpperCase(Locale.ROOT);
    }
}
