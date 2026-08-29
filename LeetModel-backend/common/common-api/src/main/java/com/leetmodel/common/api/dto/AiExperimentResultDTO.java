package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 通用隔离实验结果摘要；业务输出与指标均为版本化 JSON。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiExperimentResultDTO {
    private String experimentRunId;
    private String featureCode;
    private String workflowVersion;
    private String modelExecutionConfigVersion;
    private String ragIndexVersion;
    private String status;
    private String failureType;
    private String outputSchema;
    private String outputJson;
    private String metricSchema;
    private String metricsJson;
    private String modelName;
    private String aiCallId;
    private Long durationMs;
    private String errorMessage;
}
