package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建一次版本质量评价任务的跨服务请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationTaskCreateDTO {
    @NotNull(message = "数据集标识不能为空")
    @Positive(message = "数据集标识必须为正整数")
    private Long datasetId;

    @NotBlank(message = "评审版本不能为空")
    @Size(max = 40, message = "评审版本不能超过40个字符")
    private String workflowVersion;

    @NotNull(message = "重复次数不能为空")
    @Min(value = 1, message = "重复次数不能小于1")
    @Max(value = 3, message = "重复次数不能超过3")
    private Integer repeatCount;

    @NotBlank(message = "请求标识不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "请求标识格式不正确")
    private String clientRequestId;
}
