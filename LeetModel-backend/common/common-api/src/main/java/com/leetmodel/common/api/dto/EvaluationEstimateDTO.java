package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** 创建评价批次前返回的规模、调用量和费用完整性说明。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationEstimateDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;
    private String datasetVersion;
    private String featureCode;
    private Integer sampleCount;
    private Integer versionCount;
    private Integer repeatCount;
    private Long totalSlots;
    private Long estimatedCallCount;
    private String priority;
    private BigDecimal estimatedCostAmount;
    private String costCurrency;
    private String costCompleteness;
    private String costExplanation;
    private Boolean withinLimits;
    private List<String> violations;
}
