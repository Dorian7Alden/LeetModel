package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 版本化隔离实验样本；payload 由 feature owner 按 schema 校验。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiExperimentSampleDTO {
    @NotBlank @Size(max = 64)
    private String sampleType;
    @NotBlank @Size(max = 64)
    private String schemaVersion;
    @NotBlank @Size(max = 65535)
    private String payloadJson;
}
