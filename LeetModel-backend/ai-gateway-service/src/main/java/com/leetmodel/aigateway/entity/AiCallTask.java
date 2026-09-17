package com.leetmodel.aigateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_call_task")
public class AiCallTask extends BaseEntity {
    private String taskId;
    private String callId;
    private String traceId;
    private String callerService;
    private String idempotencyKey;
    private String callType;
    private String featureCode;
    private String operationCode;
    private String declaredPriority;
    private String effectivePriority;
    private String state;
    private String modelExecutionConfigVersion;
    private String evaluationTaskId;
    private String modelExecutionConfigSnapshot;
    private String requestHash;
    private String requestPayload;
    private String resultPayload;
    private LocalDateTime deadline;
    private Long maxQueueWaitMs;
    private String leaseOwner;
    private LocalDateTime leaseExpiry;
    private Integer attemptCount;
    private Long version;
    private Boolean cancelRequested;
    private String errorCode;
    private String deadLetterReason;
    private LocalDateTime queuedAt;
    private LocalDateTime leasedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
