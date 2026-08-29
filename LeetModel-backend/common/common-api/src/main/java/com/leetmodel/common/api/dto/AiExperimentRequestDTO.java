package com.leetmodel.common.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 由评价平台发给业务 owner 的通用隔离实验执行请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiExperimentRequestDTO {
    @NotBlank @Size(max = 64)
    private String experimentRunId;
    @NotBlank @Size(max = 64)
    private String featureCode;
    @NotNull @Valid
    private AiExperimentSampleDTO sample;
    @NotBlank @Size(max = 64)
    private String workflowVersion;
    @NotBlank @Size(max = 100)
    private String modelExecutionConfigVersion;
    @Size(max = 100)
    private String ragIndexVersion;
    @NotBlank @Pattern(regexp = "P[0-4]")
    private String priority;

    /** 评价任务、逻辑槽位与物理尝试上下文；旧调用方可不传。 */
    @Size(max = 128)
    private String evaluationTaskId;
    @Size(max = 128)
    private String slotKey;
    @Positive
    private Integer attemptNo;
    @Size(max = 128)
    private String idempotencyKey;

    public AiExperimentRequestDTO(String experimentRunId, String featureCode,
                                  AiExperimentSampleDTO sample, String workflowVersion,
                                  String modelExecutionConfigVersion, String ragIndexVersion,
                                  String priority) {
        this(experimentRunId, featureCode, sample, workflowVersion,
                modelExecutionConfigVersion, ragIndexVersion, priority,
                null, null, null, null);
    }
}
