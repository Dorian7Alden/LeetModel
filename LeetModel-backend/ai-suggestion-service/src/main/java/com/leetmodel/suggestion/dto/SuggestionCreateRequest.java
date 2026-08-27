package com.leetmodel.suggestion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SuggestionCreateRequest {
    @NotNull(message = "提交标识不能为空")
    @Positive(message = "提交标识必须为正整数")
    private Long submissionId;
}
