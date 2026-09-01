package com.leetmodel.ranking.service;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.ranking.entity.RankingRebuildTask;
import com.leetmodel.ranking.mapper.RankingRebuildTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 执行单个已领取的排行重建任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingRebuildWorker {
    private static final long[] RETRY_SECONDS = {10, 60, 300, 900, 3600};

    private final RankingRebuildTaskMapper taskMapper;
    private final RankingService rankingService;

    public void execute(Long taskId, String token) {
        RankingRebuildTask task = taskMapper.selectById(taskId);
        if (task == null || !"RUNNING".equals(task.getStatus())
                || !token.equals(task.getLeaseToken()) || task.getRunningRevision() == null) {
            return;
        }
        int attemptNo = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        CorrelationSnapshot snapshot = CorrelationSnapshot.EMPTY
                .withTraceId(task.getTraceId())
                .withDomainTask(task.getId().toString(), attemptNo);
        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            rankingService.rebuildClaimed(
                    task.getProblemId(), task.getId(), token, task.getRunningRevision());
        } catch (RuntimeException exception) {
            int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
            long delay = RETRY_SECONDS[Math.min(retry, RETRY_SECONDS.length - 1)];
            LocalDateTime now = LocalDateTime.now();
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            String failureType = exception.getClass().getSimpleName();
            taskMapper.scheduleRetry(taskId, token, now.plusSeconds(delay), truncate(message), now);
            log.warn("排行重建失败并进入退避: problemId={}, revision={}, delaySeconds={}, type={}",
                    task.getProblemId(), task.getRunningRevision(), delay, failureType);
        }
    }

    private String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
