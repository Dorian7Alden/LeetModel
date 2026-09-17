package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 同一 REVIEW 样本有效重复结果的确定性描述统计。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSampleStatisticsDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sampleId;
    private Integer validCount;
    private Integer expectedCount;
    private String completeness;
    private BigDecimal mean;
    private BigDecimal variance;
    private BigDecimal standardDeviation;
    private BigDecimal range;
}
