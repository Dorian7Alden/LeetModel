package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/** 评价任务详情，包含各样本槽位的最新运行尝试。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EvaluationTaskDTO extends EvaluationTaskSummaryDTO {
    private Integer retryCount;
    private List<EvaluationRunDTO> runs;
    private EvaluationRawMetricsDTO rawMetrics;
    private List<EvaluationScoreResultDTO> scoreResults;
}
