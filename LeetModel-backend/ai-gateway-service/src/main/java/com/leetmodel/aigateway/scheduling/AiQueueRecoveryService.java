package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.entity.AiCallAttempt;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.observability.AiGatewayMetrics;
import com.leetmodel.common.core.logging.AiCallLogEvents;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.ExecutionSpanOperation;
import com.leetmodel.common.core.telemetry.SkyWalkingExecutionSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 恢复过期租约；只有能证明尚未发送的任务才允许重新排队。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.scheduling", name = "enabled", havingValue = "true")
public class AiQueueRecoveryService {
    private static final String UNKNOWN_RESULT = "AI_UPSTREAM_RESULT_UNKNOWN";
    private final AiCallTaskMapper taskMapper;
    private final AiCallAttemptMapper attemptMapper;
    private final AiTaskWaitRegistry waitRegistry;
    private final AiGatewayMetrics metrics;

    public AiQueueRecoveryService(AiCallTaskMapper taskMapper, AiCallAttemptMapper attemptMapper,
                                  AiTaskWaitRegistry waitRegistry, AiGatewayMetrics metrics) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.waitRegistry = waitRegistry;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${ai.scheduling.recovery-delay-ms:5000}")
    @Transactional
    public int recoverOnce() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<AiCallTask> expired = taskMapper.selectExpiredLeases(now, 100);
        int recovered = 0;
        for (AiCallTask task : expired) recovered += recover(task, now);
        return recovered;
    }

    private int recover(AiCallTask task, LocalDateTime now) {
        AiCallAttempt attempt = attemptMapper.selectLatest(task.getTaskId());
        CorrelationSnapshot correlation = CorrelationSnapshot.EMPTY
                .withTraceId(task.getTraceId())
                .withDomainTask(task.getTaskId(), attempt == null ? null : attempt.getAttemptNo())
                .withAiCallId(task.getCallId());
        try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                ExecutionSpanOperation.AI_RECOVERY, correlation)
                .aiCallType(task.getCallType()).aiPriority(task.getEffectivePriority())) {
            if (attempt == null) {
                int updated = "LEASED".equals(task.getState())
                        ? taskMapper.releaseExpiredLeaseWithoutAttempt(task.getTaskId(), now) : 0;
                if (updated == 1) metrics.recovered("released_without_attempt");
                span.outcome(updated == 1 ? "released_without_attempt" : "state_conflict");
                return updated;
            }
            if ("PREPARED".equals(attempt.getState())) {
                if (attemptMapper.transition(attempt.getAttemptId(), "PREPARED", "FAILED",
                        "AI_LEASE_EXPIRED_BEFORE_DISPATCH", now) != 1) {
                    span.outcome("state_conflict");
                    return 0;
                }
                int updated = taskMapper.requeueExpiredBeforeDispatch(task.getTaskId(), task.getVersion(), now);
                if (updated != 1) throw new IllegalStateException("AI 恢复状态冲突");
                metrics.recovered("requeued_before_dispatch");
                span.outcome("requeued_before_dispatch");
                return updated;
            }
            if ("RUNNING".equals(task.getState())
                    && ("DISPATCHING".equals(attempt.getState()) || "ACKNOWLEDGED".equals(attempt.getState()))) {
                if (attemptMapper.transition(attempt.getAttemptId(), attempt.getState(), "UNKNOWN",
                        UNKNOWN_RESULT, now) != 1) {
                    span.outcome("state_conflict");
                    return 0;
                }
                int updated = taskMapper.failExpiredRunningUnknown(task.getTaskId(), task.getVersion(), now);
                if (updated != 1) throw new IllegalStateException("AI 恢复状态冲突");
                if (updated == 1) {
                    metrics.recovered("upstream_result_unknown");
                    AiCallTask terminal = taskMapper.selectByTaskId(task.getTaskId());
                    metrics.terminal(terminal, "upstream_result_unknown");
                    AiCallLogEvents.resultUnknown(log, terminal.getCallId(), terminal.getCallType(),
                            terminal.getEffectivePriority(), terminal.getTaskId(), attempt.getAttemptNo());
                    waitRegistry.complete(terminal);
                }
                span.outcome("upstream_result_unknown").error("result_unknown");
                return updated;
            }
            span.outcome("not_applicable");
            return 0;
        } catch (RuntimeException exception) {
            // 事务仍按原语义回滚；异常正文不会进入 Span。
            throw exception;
        }
    }
}
