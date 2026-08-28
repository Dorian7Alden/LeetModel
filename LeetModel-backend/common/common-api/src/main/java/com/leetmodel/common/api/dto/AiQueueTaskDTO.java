package com.leetmodel.common.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 不包含 Prompt、回答、向量、幂等键或请求哈希的队列监控元数据。 */
@Data
public class AiQueueTaskDTO {
    private String taskId;
    private String callId;
    private String callerService;
    private String callType;
    private String featureCode;
    private String operationCode;
    private String effectivePriority;
    private String state;
    private Integer attemptCount;
    private Boolean cancelRequested;
    private String errorCode;
    private String deadLetterReason;
    private Long waitMs;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
