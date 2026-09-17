package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按供应商、模型和调用类型聚合的 AI 调用事实。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiModelCallStatsDTO {
    private String provider;
    private String model;
    private String callType;
    private Long totalCount;
    private Long successCount;
    private Long failureCount;
    private Long averageTotalMs;
}
