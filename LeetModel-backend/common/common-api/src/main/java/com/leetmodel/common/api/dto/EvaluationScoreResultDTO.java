package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 一版不可变、可复算的版本选择指数结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationScoreResultDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scoreResultId;
    private String scoreResultVersion;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long weightSchemeId;
    private String weightSchemeVersion;
    private String metricSetVersion;
    private String status;
    private BigDecimal versionSelectionIndex;
    private String unavailableReason;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long calculatedBy;
    private LocalDateTime createTime;
    private List<EvaluationScoreItemDTO> items;
}
