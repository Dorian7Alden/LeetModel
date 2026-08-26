package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 不包含请求正文、回答正文和密钥的 AI 调用审计摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCallLogDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
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
    private LocalDateTime createTime;
}
