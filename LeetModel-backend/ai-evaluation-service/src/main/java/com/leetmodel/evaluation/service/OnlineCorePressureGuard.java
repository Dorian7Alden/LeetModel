package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.leetmodel.evaluation.config.EvaluationWorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** 在线 P0/P1 队列达到警告水位时，评价批任务停止新领取。 */
@Slf4j
@Component
public class OnlineCorePressureGuard {
    private final AiGatewayFeignClient aiGatewayFeignClient;
    private final EvaluationWorkerProperties properties;
    private volatile Snapshot snapshot = new Snapshot(true, "尚未取得在线队列水位", 0L);
    private volatile LogState logState = LogState.UNKNOWN;

    public OnlineCorePressureGuard(AiGatewayFeignClient aiGatewayFeignClient,
                                   EvaluationWorkerProperties properties) {
        this.aiGatewayFeignClient = aiGatewayFeignClient;
        this.properties = properties;
    }

    public boolean shouldPauseBatch() {
        long now = System.currentTimeMillis();
        Snapshot current = snapshot;
        if (now - current.checkedAtMs() < properties.getPressureCacheMs()) return current.paused();
        synchronized (this) {
            current = snapshot;
            if (now - current.checkedAtMs() < properties.getPressureCacheMs()) return current.paused();
            snapshot = load(now);
            return snapshot.paused();
        }
    }

    public boolean isPausedSnapshot() {
        return snapshot.paused();
    }

    public String reason() {
        return snapshot.reason();
    }

    private Snapshot load(long now) {
        try {
            List<AiQueueTaskDTO> p0 = queued("P0");
            List<AiQueueTaskDTO> p1 = queued("P1");
            int count = p0.size() + p1.size();
            long oldest = java.util.stream.Stream.concat(p0.stream(), p1.stream())
                    .map(AiQueueTaskDTO::getWaitMs).filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue).max().orElse(0L);
            boolean paused = count >= properties.getOnlineWarningCount()
                    || oldest >= properties.getOnlineWarningWaitMs();
            String reason = paused ? "在线 P0/P1 排队达到保护水位 count=" + count + ", waitMs=" + oldest : null;
            if (paused && logState != LogState.PAUSED) {
                log.atInfo()
                        .addKeyValue(LogFieldNames.EVENT_CODE,
                                LogEventCodes.CAPACITY_PROTECTION_ACTIVATED)
                        .addKeyValue(LogFieldNames.OUTCOME, "paused")
                        .log("Background evaluation paused by online queue pressure");
            } else if (!paused && (logState == LogState.PAUSED
                    || logState == LogState.DEPENDENCY_FAILURE)) {
                log.atInfo()
                        .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DEPENDENCY_CALL_RECOVERED)
                        .addKeyValue(LogFieldNames.OUTCOME, "ready")
                        .log("Online queue pressure check recovered");
            }
            logState = paused ? LogState.PAUSED : LogState.READY;
            return new Snapshot(paused, reason, now);
        } catch (RuntimeException exception) {
            String reason = "在线队列水位不可用，按保护策略暂停";
            if (logState != LogState.DEPENDENCY_FAILURE) {
                log.atWarn()
                        .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.DEPENDENCY_CALL_FAILED)
                        .addKeyValue(LogFieldNames.FAILURE_CATEGORY, "ONLINE_QUEUE_PRESSURE")
                        .addKeyValue(LogFieldNames.EXCEPTION_TYPE, exception.getClass().getName())
                        .log("Online queue pressure check unavailable; batch claims paused");
            }
            logState = LogState.DEPENDENCY_FAILURE;
            return new Snapshot(true, reason, now);
        }
    }

    private List<AiQueueTaskDTO> queued(String priority) {
        AiQueueQueryDTO query = new AiQueueQueryDTO();
        query.setState("QUEUED");
        query.setPriority(priority);
        query.setLimit(100);
        Result<List<AiQueueTaskDTO>> response = aiGatewayFeignClient.listQueueTasks(query);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("AI 队列查询失败");
        }
        return response.getData();
    }

    private record Snapshot(boolean paused, String reason, long checkedAtMs) {
    }

    private enum LogState {
        UNKNOWN,
        READY,
        PAUSED,
        DEPENDENCY_FAILURE
    }
}
