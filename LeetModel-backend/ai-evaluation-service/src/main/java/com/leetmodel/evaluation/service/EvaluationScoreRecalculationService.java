package com.leetmodel.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.EvaluationScoreRecalculateDTO;
import com.leetmodel.common.api.dto.EvaluationScoreResultDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.enums.EvaluationErrorCode;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 基于同一原始指标快照追加版本选择指数结果版本。 */
@Service
@RequiredArgsConstructor
public class EvaluationScoreRecalculationService {

    private final EvaluationTaskMapper taskMapper;
    private final EvaluationWeightSchemeService weightSchemeService;
    private final EvaluationScoreResultService scoreResultService;
    private final EvaluationScoreResultPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    /**
     * 使用另一权重方案追加重算结果。
     * @param taskId 评价任务标识
     * @param request 方案与操作者
     * @return 新结果版本
     */
    public EvaluationScoreResultDTO recalculate(Long taskId, EvaluationScoreRecalculateDTO request) {
        // 只接受具备完整新口径快照的已完成任务
        EvaluationTask task = taskMapper.selectById(taskId);
        BusinessException.throwIf(task == null, EvaluationErrorCode.TASK_NOT_FOUND);
        BusinessException.throwIf(!isComparableForRecalculation(task),
                EvaluationErrorCode.SCORE_RECALCULATION_NOT_ALLOWED);
        BusinessException.throwIf(task.getWeightSchemeId() != null
                        && request.getWeightSchemeId().equals(task.getWeightSchemeId()),
                EvaluationErrorCode.SCORE_RECALCULATION_NOT_ALLOWED);

        // 新方案必须仍可用于同功能和同指标集的新决策
        EvaluationWeightSchemeDTO scheme = weightSchemeService.requireActiveForTask(
                request.getWeightSchemeId(), task.getFeatureCode(), task.getMetricSetVersion());
        EvaluationRawMetricsDTO rawMetrics = readRawMetrics(task.getRawMetricsJson());
        BusinessException.throwIf(!task.getMetricSetVersion().equals(rawMetrics.getMetricSetVersion()),
                EvaluationErrorCode.SCORE_RECALCULATION_NOT_ALLOWED);

        // 使用原始 JSON 原文计算并追加，不更新任务或任何旧结果
        EvaluationScoreResultService.ScoreBundle bundle = scoreResultService.calculateRecalculation(
                task, rawMetrics, task.getRawMetricsJson(), scheme, request.getOperatorId());
        persistenceService.append(bundle);
        return scoreResultService.toDto(bundle);
    }

    /**
     * 校验任务具备同口径重算所需全部快照。
     * @param task 评价任务
     * @return 是否允许重算
     */
    private boolean isComparableForRecalculation(EvaluationTask task) {
        boolean baseValid = "COMPLETED".equals(task.getStatus())
                && task.getDatasetId() != null
                && hasText(task.getFeatureCode())
                && hasText(task.getDatasetVersion())
                && hasText(task.getWorkflowVersion())
                && hasText(task.getMetricSetVersion())
                && validSnapshotValue(task.getMetricDefinitionSnapshotJson(),
                "metricSetVersion", task.getMetricSetVersion())
                && validJson(task.getWorkflowSnapshotJson())
                && hasText(task.getModelExecutionConfigVersion())
                && hasText(task.getRawMetricsJson());
        if (!baseValid) return false;
        if (task.getWeightSchemeId() == null) return true;
        return hasText(task.getWeightSchemeVersion())
                && validSnapshotValue(task.getWeightSchemeSnapshotJson(),
                "schemeVersion", task.getWeightSchemeVersion());
    }

    /**
     * 读取任务锁定的原始指标快照。
     * @param rawMetricsJson 原始指标 JSON
     * @return 原始指标对象
     */
    private EvaluationRawMetricsDTO readRawMetrics(String rawMetricsJson) {
        try {
            return objectMapper.readValue(rawMetricsJson, EvaluationRawMetricsDTO.class);
        } catch (Exception exception) {
            throw new BusinessException(EvaluationErrorCode.SCORE_RECALCULATION_NOT_ALLOWED);
        }
    }

    /**
     * 判断文本快照是否存在。
     * @param value 文本
     * @return 是否非空
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 判断 JSON 快照可解析。
     * @param value JSON 文本
     * @return 是否有效
     */
    private boolean validJson(String value) {
        if (!hasText(value)) return false;
        try {
            return objectMapper.readTree(value).isObject();
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 判断 JSON 快照包含预期版本值。
     * @param value JSON 文本
     * @param field 版本字段
     * @param expected 预期值
     * @return 是否有效且匹配
     */
    private boolean validSnapshotValue(String value, String field, String expected) {
        if (!validJson(value)) return false;
        try {
            return expected.equals(objectMapper.readTree(value).path(field).asText(null));
        } catch (Exception exception) {
            return false;
        }
    }
}
