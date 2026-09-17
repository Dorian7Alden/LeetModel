package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 使用另一权重方案重新计算版本选择指数的请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationScoreRecalculateDTO {
    @NotNull(message = "权重方案标识不能为空")
    @Positive(message = "权重方案标识必须为正整数")
    private Long weightSchemeId;

    @NotNull(message = "操作者不能为空")
    @Positive(message = "操作者标识必须为正整数")
    private Long operatorId;
}
