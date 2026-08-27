package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 已锁定的评价样本事实。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSampleDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sampleId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private Integer sortOrder;
    private String note;
}
