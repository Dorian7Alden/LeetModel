package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 客服可验证指标的聚合值及证据完整性。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMetricSummaryDTO {
    private String metricCode;
    private String status;
    private BigDecimal value;
    private Integer evaluatedCount;
    private Integer eligibleCount;
    private String evidence;
}
