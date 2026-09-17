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

    /**
     * 记录领域租约任务被当前 Worker 成功抢占或领取的结构化事件。
     *
     * @param logger       目标日志记录器
     * @param businessType 业务类型标识，如 review、suggestion、ranking
     * @param taskId       领域任务唯一主键标识
     * @param attemptNo    当前执行物理尝试序号，允许为 null
     * @param takeover     true 表示自故障实例接管租约，false 为正常领取
     */
    public static void claimed(
            Logger logger,
            String businessType,
            Object taskId,
            Integer attemptNo,
            boolean takeover
    ) {
        LoggingEventBuilder event = base(logger.atInfo(), businessType, taskId, attemptNo)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DOMAIN_TASK_CLAIMED)
                .addKeyValue(LogFieldNames.TASK_STATE, "LEASED")
                .addKeyValue(LogFieldNames.CLAIM_TYPE, takeover ? "takeover" : "normal")
                .addKeyValue(LogFieldNames.OUTCOME, "claimed");
        event.log("Domain task claimed");
    }

    /**
     * 记录领域任务单次执行尝试结束（成功或失败）的结构化事件。
     *
     * @param logger       目标日志记录器
     * @param businessType 业务类型标识
     * @param taskId       领域任务唯一主键标识
     * @param attemptNo    当前执行物理尝试序号，允许为 null
     * @param state        任务结束状态（如 SUCCEEDED、FAILED）
     * @param elapsedNanos 任务单次执行总耗时纳秒数
     */
    public static void finished(
            Logger logger,
            String businessType,
            Object taskId,
            Integer attemptNo,
            String state,
            long elapsedNanos
    ) {
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

    /**
     * 记录后台线程池满载拒绝执行领域任务的警告事件。
     *
     * @param logger       目标日志记录器
     * @param businessType 业务类型标识
     * @param taskId       领域任务唯一主键标识
     */
    public static void executorRejected(
            Logger logger,
            String businessType,
            Object taskId
    ) {
        base(logger.atWarn(), businessType, taskId, null)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DOMAIN_TASK_EXECUTOR_REJECTED)
                .addKeyValue(LogFieldNames.OUTCOME, "executor_rejected")
                .log("Domain task executor rejected attempt");
    }

    /**
     * 注入业务类型、任务 ID 与尝试序号的基础日志构建器。
     *
     * @param event        SLF4J 事件构建器
     * @param businessType 业务类型标识
     * @param taskId       领域任务唯一主键标识
     * @param attemptNo    物理尝试序号，允许为 null
     * @return 注入公共键值对后的事件构建器
     */
    private static LoggingEventBuilder base(LoggingEventBuilder event, String businessType,
                                            Object taskId, Integer attemptNo) {
        event.addKeyValue(LogFieldNames.BUSINESS_TYPE, businessType)
                .addKeyValue(LogFieldNames.DOMAIN_TASK_ID, taskId);
        if (attemptNo != null) event.addKeyValue(LogFieldNames.ATTEMPT_NO, attemptNo);
        return event;
    }

    /**
     * 规范化任务执行状态字符串，默认转为大写。
     *
     * @param state 原始状态文本
     * @return 规范化后的状态文本；为空时返回 UNKNOWN
     */
    private static String normalize(String state) {
        return state == null || state.isBlank() ? "UNKNOWN" : state.trim().toUpperCase(Locale.ROOT);
    }
}
