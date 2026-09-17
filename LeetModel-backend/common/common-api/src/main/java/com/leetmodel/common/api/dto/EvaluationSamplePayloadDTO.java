package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 评价数据集中的版本化样本载荷。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSamplePayloadDTO {
    @NotBlank(message = "样本类型不能为空")
    @Size(max = 40, message = "样本类型不能超过40个字符")
    private String sampleType;

    @NotBlank(message = "样本载荷版本不能为空")
    @Size(max = 40, message = "样本载荷版本不能超过40个字符")
    private String payloadSchemaVersion;

    @NotBlank(message = "样本载荷不能为空")
    @Size(max = 16000, message = "样本载荷不能超过16000个字符")
    private String payloadJson;
}
