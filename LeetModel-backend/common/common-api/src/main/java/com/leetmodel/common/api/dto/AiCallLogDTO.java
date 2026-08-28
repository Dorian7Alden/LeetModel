package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 不包含请求正文、回答正文、密钥和渠道配置的 AI 调用事实。 */
@Data
public class AiCallLogDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String callId;
    private String modality;
    private String callerService;
    private String featureCode;
    private String operationCode;
    private String businessTaskId;
    private String workflowVersion;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String evaluationTaskId;
    private String priority;
    private String provider;
    private String model;
    private String providerResponseId;
    private String newApiRequestId;
    private String status;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheHitTokens;
    private Long cacheCreationTokens;
    private Long cacheMissTokens;
    private Long totalTokens;
    private String usageCompleteness;
    private Long queueMs;
    private Long executionMs;
    private Long totalMs;
    private BigDecimal costAmount;
    private String costCurrency;
    private String costSource;
    private String priceSnapshotVersion;
    private String costCompleteness;
    private Integer errorCode;
    private String errorMessage;
    private LocalDateTime createTime;

    @Deprecated private String scene;
    @Deprecated private Long promptTokens;
    @Deprecated private Long completionTokens;
    @Deprecated private Boolean usageComplete;
    @Deprecated private Long durationMs;
}
