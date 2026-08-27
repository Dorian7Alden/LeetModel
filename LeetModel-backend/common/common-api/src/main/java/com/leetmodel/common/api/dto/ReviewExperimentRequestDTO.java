package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 质量评价发起的一次隔离评审实验。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewExperimentRequestDTO {
    @NotNull(message = "提交标识不能为空")
    @Positive(message = "提交标识必须为正整数")
    private Long submissionId;

    @NotBlank(message = "评审版本不能为空")
    @Size(max = 40, message = "评审版本不能超过40个字符")
    private String workflowVersion;
}
