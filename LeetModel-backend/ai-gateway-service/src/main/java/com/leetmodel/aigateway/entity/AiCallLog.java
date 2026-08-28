package com.leetmodel.aigateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_call_log")
public class AiCallLog extends BaseEntity {
    private String callId;
    private String callType;
    private String scene;
    private String modality;
    private String callerService;
    private String featureCode;
    private String operationCode;
    private String businessTaskId;
    private String workflowVersion;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String evaluationTaskId;
    private String ragIndexVersion;
    private String priority;
    private String idempotencyKey;
    private LocalDateTime deadline;
    private String provider;
    private String model;
    private String providerResponseId;
    private String newApiRequestId;
    private String status;
    private Long inputTokens;
    private Long outputTokens;
    private Long promptTokens;
    private Long completionTokens;
    private Long reasoningTokens;
    private Long cacheHitTokens;
    private Long cacheCreationTokens;
    private Long cacheMissTokens;
    private Long totalTokens;
    private Boolean usageComplete;
    private String usageCompleteness;
    private Integer inputCount;
    private Integer vectorDimension;
    private Long queueMs;
    private Long executionMs;
    private Long totalMs;
    private Long durationMs;
    private BigDecimal costAmount;
    private String costCurrency;
    private String costSource;
    private String priceSnapshotVersion;
    private String costCompleteness;
    private String costEnrichmentStatus;
    private Integer costEnrichmentAttempts;
    private LocalDateTime costNextRetryAt;
    private LocalDateTime costLastAttemptAt;
    private Integer errorCode;
    private String errorMessage;
}
