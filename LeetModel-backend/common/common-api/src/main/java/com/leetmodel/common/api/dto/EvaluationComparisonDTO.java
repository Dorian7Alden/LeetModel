package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 相同固定数据集和重复口径下的版本横向对比。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationComparisonDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;
    private String featureCode;
    private String datasetVersion;
    private Integer repeatCount;
    private Boolean comparable;
    private Boolean rankingApplied;
    private List<String> incompatibilityReasons;
    private List<EvaluationTaskSummaryDTO> versions;
}
