package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.scheduling.AiTaskWaitRegistry;
import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.messaging.internal.OperationAuditGovernanceProducer;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** 队列元数据查询和受控取消；不暴露或读取调用正文。 */
@Service
public class AiQueueOperationsService {
    private static final Set<String> ACTIVE = Set.of("QUEUED", "LEASED", "RUNNING");
    private final AiCallTaskMapper mapper;
    private final AiTaskWaitRegistry waitRegistry;
    private final OperationAuditGovernanceProducer audit;

    @Autowired
    public AiQueueOperationsService(AiCallTaskMapper mapper, AiTaskWaitRegistry waitRegistry,
                                    ObjectProvider<OperationAuditGovernanceProducer> audit) {
        this.mapper = mapper;
        this.waitRegistry = waitRegistry;
        this.audit = audit == null ? null : audit.getIfAvailable();
    }

    public AiQueueOperationsService(AiCallTaskMapper mapper, AiTaskWaitRegistry waitRegistry) {
        this(mapper, waitRegistry, null);
    }

    public List<AiQueueTaskDTO> list(AiQueueQueryDTO query) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long minimumWait = query.getMinWaitMs() == null ? 0L : query.getMinWaitMs();
        int limit = query.getLimit() == null ? 50 : query.getLimit();
        return mapper.selectForMonitoring(query.getState(), query.getPriority(), query.getCallerService(),
                        query.getEvaluationTaskId())
                .stream().map(task -> toDto(task, now))
                .filter(task -> task.getWaitMs() >= minimumWait)
                .limit(limit).toList();
    }

    public AiQueueTaskDTO cancel(String taskId) {
        if (!StringUtils.hasText(taskId) || taskId.length() > 64) {
            throw new BusinessException(AiGatewayErrorCode.AI_TASK_NOT_FOUND);
        }
        AiCallTask task = mapper.selectByTaskId(taskId);
        if (task == null) throw new BusinessException(AiGatewayErrorCode.AI_TASK_NOT_FOUND);
        if (!ACTIVE.contains(task.getState())) {
            throw new BusinessException(AiGatewayErrorCode.AI_TASK_NOT_CANCELLABLE);
        }
        if (audit != null) audit.assertReady("AI_QUEUE.CANCEL");
        if (mapper.requestCancel(taskId, LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new BusinessException(AiGatewayErrorCode.AI_TASK_NOT_CANCELLABLE);
        }
        AiCallTask cancelled = mapper.selectByTaskId(taskId);
        waitRegistry.complete(cancelled);
        if (audit != null) audit.emit("AI_QUEUE.CANCEL", "AI_CALL_TASK", taskId,
                java.util.Map.of("taskState", cancelled.getState(), "cancelRequested", "true"));
        return toDto(cancelled, LocalDateTime.now(ZoneOffset.UTC));
    }

    private AiQueueTaskDTO toDto(AiCallTask task, LocalDateTime now) {
        AiQueueTaskDTO dto = new AiQueueTaskDTO();
        dto.setTaskId(task.getTaskId());
        dto.setCallId(task.getCallId());
        dto.setCallerService(task.getCallerService());
        dto.setCallType(task.getCallType());
        dto.setFeatureCode(task.getFeatureCode());
        dto.setOperationCode(task.getOperationCode());
        dto.setEvaluationTaskId(task.getEvaluationTaskId());
        dto.setEffectivePriority(task.getEffectivePriority());
        dto.setState(task.getState());
        dto.setAttemptCount(task.getAttemptCount());
        dto.setCancelRequested(task.getCancelRequested());
        dto.setErrorCode(task.getErrorCode());
        dto.setDeadLetterReason(task.getDeadLetterReason());
        dto.setQueuedAt(task.getQueuedAt());
        dto.setStartedAt(task.getStartedAt());
        dto.setFinishedAt(task.getFinishedAt());
        LocalDateTime waitEnd = task.getStartedAt() != null ? task.getStartedAt()
                : task.getFinishedAt() != null ? task.getFinishedAt() : now;
        dto.setWaitMs(Math.max(0L, Duration.between(task.getQueuedAt(), waitEnd).toMillis()));
        return dto;
    }
}
