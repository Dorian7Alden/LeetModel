package com.leetmodel.common.ai.logging;

import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * AI 调用阶段结构化日志门面。
 *
 * <p>用于记录大模型调度的关键生命周期事实（完成、失败、上游状态未知）；
 * 仅输出调用标识与性能度量，严禁记录 Prompt 或生成文本正文。</p>
 */
public final class AiCallLogEvents {

    private AiCallLogEvents() { }

    /**
     * 记录 AI 原子调用成功完成的结构化日志。
     *
     * @param logger     目标日志记录器
     * @param callId     AI 单次调用唯一标识
     * @param callType   调用类型（如 CHAT、EMBEDDING）
     * @param priority   调度优先级（如 P0、P1）
     * @param durationMs 执行耗时（毫秒）
     */
    public static void completed(
            Logger logger,
            String callId,
            String callType,
            String priority,
            long durationMs
    ) {
        base(logger.atInfo(), LogEventCodes.AI_CALL_COMPLETED, callId, callType, priority)
                .addKeyValue(LogFieldNames.DURATION_MS, Math.max(0, durationMs))
                .addKeyValue(LogFieldNames.OUTCOME, "succeeded")
                .log("AI call completed");
    }

    /**
     * 记录 AI 原子调用失败的结构化警告日志。
     *
     * @param logger     目标日志记录器
     * @param callId     AI 单次调用唯一标识
     * @param callType   调用类型
     * @param priority   调度优先级
     * @param errorCode  业务或网关错误状态码
     * @param durationMs 执行耗时（毫秒）
     * @param failure    捕获的底层失败异常，允许为 null
     */
    public static void failed(
            Logger logger,
            String callId,
            String callType,
            String priority,
            String errorCode,
            long durationMs,
            Throwable failure
    ) {
        LoggingEventBuilder event = base(logger.atWarn(), LogEventCodes.AI_CALL_FAILED,
                callId, callType, priority)
                .addKeyValue(LogFieldNames.ERROR_CODE, errorCode)
                .addKeyValue(LogFieldNames.DURATION_MS, Math.max(0, durationMs))
                .addKeyValue(LogFieldNames.OUTCOME, "failed");
        if (failure != null) event.setCause(failure);
        event.log("AI call failed");
    }

    /**
     * 记录 AI 上游供应商调用结果未知的严重错误日志（如超时）。
     *
     * @param logger    目标日志记录器
     * @param callId    AI 单次调用唯一标识
     * @param callType  调用类型
     * @param priority  调度优先级
     * @param taskId    关联的领域任务 ID
     * @param attemptNo 当前物理重试序号，允许为 null
     */
    public static void resultUnknown(
            Logger logger,
            String callId,
            String callType,
            String priority,
            Object taskId,
            Integer attemptNo
    ) {
        LoggingEventBuilder event = base(logger.atError(), LogEventCodes.AI_CALL_RESULT_UNKNOWN,
                callId, callType, priority)
                .addKeyValue(LogFieldNames.ERROR_CODE, "AI_UPSTREAM_RESULT_UNKNOWN")
                .addKeyValue(LogFieldNames.DOMAIN_TASK_ID, taskId)
                .addKeyValue(LogFieldNames.TASK_STATE, "UNKNOWN")
                .addKeyValue(LogFieldNames.OUTCOME, "upstream_result_unknown");
        if (attemptNo != null) event.addKeyValue(LogFieldNames.ATTEMPT_NO, attemptNo);
        event.log("AI upstream result is unknown");
    }

    /**
     * 注入 AI 调用通用键值对的基础日志构建器。
     *
     * @param event     SLF4J 事件构建器
     * @param eventCode 稳定事件编码
     * @param callId    AI 调用标识
     * @param callType  调用类型
     * @param priority  调度优先级，允许为 null
     * @return 注入公共键值对后的构建器
     */
    private static LoggingEventBuilder base(
            LoggingEventBuilder event,
            String eventCode,
            String callId,
            String callType,
            String priority
    ) {
        event.addKeyValue(LogFieldNames.EVENT_CODE, eventCode)
                .addKeyValue(LogFieldNames.AI_CALL_ID, callId)
                .addKeyValue(LogFieldNames.AI_CALL_TYPE, callType);
        if (priority != null) event.addKeyValue(LogFieldNames.AI_PRIORITY, priority);
        return event;
    }
}
