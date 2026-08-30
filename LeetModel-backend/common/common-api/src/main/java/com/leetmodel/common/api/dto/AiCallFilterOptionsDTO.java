package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** AI 调用日志结构化筛选字段的可选值。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCallFilterOptionsDTO {
    private List<String> featureCodes;
    private List<String> operationCodes;
    private List<String> evaluationTaskIds;
    private List<String> providers;
    private List<String> models;
    private List<String> statuses;
}
