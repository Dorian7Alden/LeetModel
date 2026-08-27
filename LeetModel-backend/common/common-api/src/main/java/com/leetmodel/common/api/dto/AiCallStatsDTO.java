package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 调用运行摘要；MVP 不虚构尚未获得的价格成本。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCallStatsDTO {
    private Long totalCount;
    private Long successCount;
    private Long failureCount;
    private Long totalTokens;
    private Long averageDurationMs;
}
