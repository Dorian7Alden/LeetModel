package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理员控制评价任务时记录的操作者。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationTaskControlDTO {
    @NotNull(message = "操作者标识不能为空")
    @Positive(message = "操作者标识必须为正整数")
    private Long operatorId;
}
