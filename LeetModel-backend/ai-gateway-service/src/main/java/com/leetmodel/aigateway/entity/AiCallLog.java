package com.leetmodel.aigateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_call_log")
public class AiCallLog extends BaseEntity {
    private String callId;
    private String scene;
    private String provider;
    private String model;
    private String status;
    private Long promptTokens;
    private Long completionTokens;
    private Long reasoningTokens;
    private Long totalTokens;
    private Boolean usageComplete;
    private Long durationMs;
    private Integer errorCode;
    private String errorMessage;
}
