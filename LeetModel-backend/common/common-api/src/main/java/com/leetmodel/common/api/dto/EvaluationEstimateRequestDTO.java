package com.leetmodel.common.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 创建评价批次前的规模与调用量预估请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationEstimateRequestDTO {
    @NotNull(message = "数据集标识不能为空")
    @Positive(message = "数据集标识必须为正整数")
    private Long datasetId;

    @NotEmpty(message = "至少选择一个候选版本")
    @Size(max = 20, message = "候选版本不能超过20个")
    private List<@Valid EvaluationCandidateDTO> candidates;

    @NotNull(message = "重复次数不能为空")
    @Min(value = 1, message = "重复次数不能小于1")
    @Max(value = 100, message = "重复次数不能超过100")
    private Integer repeatCount;
}
