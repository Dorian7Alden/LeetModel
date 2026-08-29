package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可由评价平台发现的 AI 业务功能及其 owner 版本目录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiFeatureDefinitionDTO {
    private String featureCode;
    private String name;
    private String ownerService;
    private List<String> supportedDatasetTypes;
    private List<String> supportedMetricCodes;
    private List<AiWorkflowVersionDTO> workflowVersions;
}
