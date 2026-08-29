package com.leetmodel.admin.dto;

import com.leetmodel.common.api.dto.EvaluationScoreRecalculateDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 管理端权重重算请求；操作者只从登录上下文注入。 */
public record AdminEvaluationScoreRecalculateDTO(
        @NotNull(message = "权重方案标识不能为空")
        @Positive(message = "权重方案标识必须为正整数")
        Long weightSchemeId) {

    public EvaluationScoreRecalculateDTO toInternal(Long operatorId) {
        return new EvaluationScoreRecalculateDTO(weightSchemeId, operatorId);
    }
}
