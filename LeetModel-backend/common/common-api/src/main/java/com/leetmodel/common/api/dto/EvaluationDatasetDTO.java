package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 固定评价数据集及其稳定样本引用。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDatasetDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;
    private String name;
    private String description;
    private String status;
    private Integer sampleCount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    private LocalDateTime createTime;
    private List<EvaluationSampleDTO> samples;
}
