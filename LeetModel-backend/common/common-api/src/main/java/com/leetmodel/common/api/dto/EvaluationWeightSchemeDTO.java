package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 一份版本化权重方案及其不可变指标配置。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationWeightSchemeDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long schemeId;
    private String schemeCode;
    private String schemeVersion;
    private String name;
    private String objective;
    private String featureCode;
    private String metricSetVersion;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    private LocalDateTime createTime;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deactivatedBy;
    private LocalDateTime deactivatedAt;
    private List<EvaluationWeightItemDTO> items;
}
