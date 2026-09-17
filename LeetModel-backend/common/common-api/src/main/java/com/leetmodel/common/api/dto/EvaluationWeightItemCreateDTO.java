package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 创建权重方案时的一条指标、归一化和权重快照。 */
@Data
@NoArgsConstructor
public class EvaluationWeightItemCreateDTO {

    @NotBlank(message = "指标编码不能为空")
    @Size(max = 64, message = "指标编码不能超过64个字符")
    private String metricCode;

    @NotBlank(message = "指标口径版本不能为空")
    @Size(max = 64, message = "指标口径版本不能超过64个字符")
    private String metricVersion;

    @NotBlank(message = "指标单位不能为空")
    @Size(max = 32, message = "指标单位不能超过32个字符")
    private String unit;

    @NotBlank(message = "归一化配置版本不能为空")
    @Size(max = 64, message = "归一化配置版本不能超过64个字符")
    private String normalizationVersion;

    @NotBlank(message = "归一化方法不能为空")
    @Size(max = 32, message = "归一化方法不能超过32个字符")
    private String normalizationMethod;

    @NotBlank(message = "截断策略不能为空")
    @Size(max = 32, message = "截断策略不能超过32个字符")
    private String clippingPolicy;

    @NotBlank(message = "缺失策略不能为空")
    @Size(max = 32, message = "缺失策略不能超过32个字符")
    private String missingPolicy;

    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private BigDecimal targetLowerBound;
    private BigDecimal targetUpperBound;

    @NotBlank(message = "边界来源不能为空")
    @Size(max = 32, message = "边界来源不能超过32个字符")
    private String boundarySource;

    @Size(max = 200, message = "边界引用不能超过200个字符")
    private String boundaryReference;

    @NotNull(message = "指标权重不能为空")
    @DecimalMin(value = "0.0001", message = "指标权重必须大于0")
    @DecimalMax(value = "100.0000", message = "指标权重不能超过100")
    @Digits(integer = 3, fraction = 4, message = "指标权重最多保留4位小数")
    private BigDecimal weightPercent;
}
