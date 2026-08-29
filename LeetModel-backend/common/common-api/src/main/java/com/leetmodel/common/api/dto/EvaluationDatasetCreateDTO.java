package com.leetmodel.common.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 创建不可变固定评价数据集的跨服务请求。 */
@Data
@NoArgsConstructor
public class EvaluationDatasetCreateDTO {
    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 100, message = "数据集名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "数据集说明不能超过500个字符")
    private String description;

    @NotNull(message = "创建人标识不能为空")
    @Positive(message = "创建人标识必须为正整数")
    private Long createdBy;

    @NotEmpty(message = "评价数据集至少包含一个样本")
    @Size(max = 100, message = "评价数据集最多包含100个样本")
    private List<@Valid EvaluationSampleCreateDTO> samples;

    @Size(max = 32, message = "功能编码不能超过32个字符")
    private String featureCode;

    @Size(max = 64, message = "数据集版本不能超过64个字符")
    private String datasetVersion;

    public EvaluationDatasetCreateDTO(String name, String description, Long createdBy,
                                      List<EvaluationSampleCreateDTO> samples) {
        this(name, description, createdBy, samples, null, null);
    }

    public EvaluationDatasetCreateDTO(String name, String description, Long createdBy,
                                      List<EvaluationSampleCreateDTO> samples,
                                      String featureCode, String datasetVersion) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.samples = samples;
        this.featureCode = featureCode;
        this.datasetVersion = datasetVersion;
    }
}
