package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 固定评价数据集中的一个提交引用。 */
@Data
@NoArgsConstructor
public class EvaluationSampleCreateDTO {
    @Positive(message = "样本提交标识必须为正整数")
    private Long submissionId;

    @Size(max = 200, message = "样本说明不能超过200个字符")
    private String note;

    @Valid
    private EvaluationSamplePayloadDTO payload;

    public EvaluationSampleCreateDTO(Long submissionId, String note) {
        this.submissionId = submissionId;
        this.note = note;
    }

    public EvaluationSampleCreateDTO(Long submissionId, String note,
                                     EvaluationSamplePayloadDTO payload) {
        this.submissionId = submissionId;
        this.note = note;
        this.payload = payload;
    }
}
