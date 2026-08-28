package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.entity.AiCallAttempt;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 恢复过期租约；只有能证明尚未发送的任务才允许重新排队。 */
@Component
@ConditionalOnProperty(prefix = "ai.scheduling", name = "enabled", havingValue = "true")
public class AiQueueRecoveryService {
    private static final String UNKNOWN_RESULT = "AI_UPSTREAM_RESULT_UNKNOWN";
    private final AiCallTaskMapper taskMapper;
    private final AiCallAttemptMapper attemptMapper;
    private final AiTaskWaitRegistry waitRegistry;

    public AiQueueRecoveryService(AiCallTaskMapper taskMapper, AiCallAttemptMapper attemptMapper,
                                  AiTaskWaitRegistry waitRegistry) {
        this.taskMapper = taskMapper;
        this.attemptMapper = attemptMapper;
        this.waitRegistry = waitRegistry;
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
        if (attempt == null) {
            return "LEASED".equals(task.getState())
                    ? taskMapper.releaseExpiredLeaseWithoutAttempt(task.getTaskId(), now) : 0;
        }
        if ("PREPARED".equals(attempt.getState())) {
            if (attemptMapper.transition(attempt.getAttemptId(), "PREPARED", "FAILED",
                    "AI_LEASE_EXPIRED_BEFORE_DISPATCH", now) != 1) return 0;
            int updated = taskMapper.requeueExpiredBeforeDispatch(task.getTaskId(), task.getVersion(), now);
            if (updated != 1) throw new IllegalStateException("AI 恢复状态冲突");
            return updated;
        }
        if ("RUNNING".equals(task.getState())
                && ("DISPATCHING".equals(attempt.getState()) || "ACKNOWLEDGED".equals(attempt.getState()))) {
            if (attemptMapper.transition(attempt.getAttemptId(), attempt.getState(), "UNKNOWN",
                    UNKNOWN_RESULT, now) != 1) return 0;
            int updated = taskMapper.failExpiredRunningUnknown(task.getTaskId(), task.getVersion(), now);
            if (updated != 1) throw new IllegalStateException("AI 恢复状态冲突");
            if (updated == 1) waitRegistry.complete(taskMapper.selectByTaskId(task.getTaskId()));
            return updated;
        }
        return 0;
    }
}
