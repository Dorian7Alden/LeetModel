package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 固定评价数据集中的一个提交引用。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSampleCreateDTO {
    @NotNull(message = "样本提交标识不能为空")
    @Positive(message = "样本提交标识必须为正整数")
    private Long submissionId;

    @Size(max = 200, message = "样本说明不能超过200个字符")
    private String note;
}
