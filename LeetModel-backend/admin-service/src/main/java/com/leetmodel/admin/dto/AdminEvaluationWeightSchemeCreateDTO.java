package com.leetmodel.admin.dto;

import com.leetmodel.common.api.dto.EvaluationWeightItemCreateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeCreateDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端创建权重方案的请求；创建人只从登录上下文注入。 */
@Data
public class AdminEvaluationWeightSchemeCreateDTO {

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

    @Valid
    @NotEmpty(message = "权重方案至少包含一个指标")
    @Size(max = 32, message = "权重方案不能超过32个指标")
    private List<EvaluationWeightItemCreateDTO> items;

    public EvaluationWeightSchemeCreateDTO toInternal(Long createdBy) {
        EvaluationWeightSchemeCreateDTO request = new EvaluationWeightSchemeCreateDTO();
        request.setSchemeCode(schemeCode);
        request.setSchemeVersion(schemeVersion);
        request.setName(name);
        request.setObjective(objective);
        request.setFeatureCode(featureCode);
        request.setMetricSetVersion(metricSetVersion);
        request.setCreatedBy(createdBy);
        request.setItems(items);
        return request;
    }
}
