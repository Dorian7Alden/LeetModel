package com.leetmodel.common.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 创建一份不可变版本化权重方案。 */
@Data
@NoArgsConstructor
public class EvaluationWeightSchemeCreateDTO {

    @NotBlank(message = "权重方案编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}", message = "权重方案编码格式不正确")
    private String schemeCode;

    @NotBlank(message = "权重方案版本不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_.-]{2,63}", message = "权重方案版本格式不正确")
    private String schemeVersion;

    @NotBlank(message = "权重方案名称不能为空")
    @Size(max = 100, message = "权重方案名称不能超过100个字符")
    private String name;

    @NotBlank(message = "评价目标不能为空")
    @Size(max = 500, message = "评价目标不能超过500个字符")
    private String objective;

    @NotBlank(message = "适用功能不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "适用功能格式不正确")
    private String featureCode;

    @NotBlank(message = "指标集版本不能为空")
    @Size(max = 64, message = "指标集版本不能超过64个字符")
    private String metricSetVersion;

    @NotNull(message = "创建人不能为空")
    @Positive(message = "创建人标识必须为正整数")
    private Long createdBy;

    @Valid
    @NotEmpty(message = "权重方案至少包含一个指标")
    @Size(max = 32, message = "权重方案不能超过32个指标")
    private List<EvaluationWeightItemCreateDTO> items;
}
