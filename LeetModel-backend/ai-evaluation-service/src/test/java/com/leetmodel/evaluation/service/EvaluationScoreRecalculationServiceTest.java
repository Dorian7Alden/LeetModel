package com.leetmodel.evaluation.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.EvaluationScoreRecalculateDTO;
import com.leetmodel.common.api.dto.EvaluationScoreResultDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.evaluation.entity.EvaluationScoreResult;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationScoreRecalculationServiceTest {

    private final EvaluationTaskMapper taskMapper = mock(EvaluationTaskMapper.class);
    private final EvaluationWeightSchemeService weightSchemeService = mock(EvaluationWeightSchemeService.class);
    private final EvaluationScoreResultService scoreResultService = mock(EvaluationScoreResultService.class);
    private final EvaluationScoreResultPersistenceService persistenceService =
            mock(EvaluationScoreResultPersistenceService.class);
    private final EvaluationScoreRecalculationService service = new EvaluationScoreRecalculationService(
            taskMapper, weightSchemeService, scoreResultService, persistenceService,
            JsonMapper.builder().findAndAddModules().build());

    @Test
    void recalculationReferencesExactSameRawSnapshotAndRecordsOperator() {
        EvaluationTask task = comparableTask();
        EvaluationWeightSchemeDTO scheme = scheme();
        EvaluationScoreResult result = new EvaluationScoreResult();
        result.setTaskId(20L);
        result.setCalculatedBy(9L);
        var bundle = new EvaluationScoreResultService.ScoreBundle(result, List.of());
        EvaluationScoreResultDTO response = new EvaluationScoreResultDTO();
        response.setScoreResultVersion("SCORE_RESULT_V2");
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(weightSchemeService.requireActiveForTask(702L, "REVIEW", "METRIC_SET_V2"))
                .thenReturn(scheme);
        when(scoreResultService.calculateRecalculation(eq(task), any(),
                eq(task.getRawMetricsJson()), eq(scheme), eq(9L))).thenReturn(bundle);
        when(scoreResultService.toDto(bundle)).thenReturn(response);

        var actual = service.recalculate(20L, new EvaluationScoreRecalculateDTO(702L, 9L));

        assertThat(actual.getScoreResultVersion()).isEqualTo("SCORE_RESULT_V2");
        verify(scoreResultService).calculateRecalculation(eq(task), any(EvaluationRawMetricsDTO.class),
                eq("{\"metricSetVersion\":\"METRIC_SET_V2\",\"runSuccessRate\":80}"),
                eq(scheme), eq(9L));
        verify(persistenceService).append(bundle);
    }

    @Test
    void legacyOrIncompleteComparisonSnapshotCannotBeRecalculated() {
        EvaluationTask legacy = comparableTask();
        legacy.setDatasetVersion(null);
        when(taskMapper.selectById(20L)).thenReturn(legacy);

        assertThatThrownBy(() -> service.recalculate(
                20L, new EvaluationScoreRecalculateDTO(702L, 9L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41114);

        verify(weightSchemeService, never()).requireActiveForTask(any(), any(), any());
        verify(persistenceService, never()).append(any());
    }

    @Test
    void originalSchemeAndMismatchedRawMetricSetCannotCreateRedundantOrIncomparableResult() {
        EvaluationTask task = comparableTask();
        when(taskMapper.selectById(20L)).thenReturn(task);

        assertThatThrownBy(() -> service.recalculate(
                20L, new EvaluationScoreRecalculateDTO(701L, 9L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41114);

        task.setRawMetricsJson("{\"metricSetVersion\":\"METRIC_SET_V1\"}");
        when(weightSchemeService.requireActiveForTask(702L, "REVIEW", "METRIC_SET_V2"))
                .thenReturn(scheme());
        assertThatThrownBy(() -> service.recalculate(
                20L, new EvaluationScoreRecalculateDTO(702L, 9L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41114);

        verify(persistenceService, never()).append(any());
    }

    private EvaluationTask comparableTask() {
        EvaluationTask task = new EvaluationTask();
        task.setId(20L);
        task.setDatasetId(10L);
        task.setStatus("COMPLETED");
        task.setFeatureCode("REVIEW");
        task.setDatasetVersion("REVIEW_DATASET_V1");
        task.setWorkflowVersion("BASIC_REVIEW_V1");
        task.setMetricSetVersion("METRIC_SET_V2");
        task.setMetricDefinitionSnapshotJson("{\"metricSetVersion\":\"METRIC_SET_V2\"}");
        task.setWorkflowSnapshotJson("{\"workflowVersion\":\"BASIC_REVIEW_V1\"}");
        task.setModelExecutionConfigVersion("MODEL_CFG_REVIEW_MULTIMODAL_0001");
        task.setWeightSchemeId(701L);
        task.setWeightSchemeVersion("REVIEW_BALANCED_V1");
        task.setWeightSchemeSnapshotJson("{\"schemeVersion\":\"REVIEW_BALANCED_V1\"}");
        task.setRawMetricsJson("{\"metricSetVersion\":\"METRIC_SET_V2\",\"runSuccessRate\":80}");
        return task;
    }

    private EvaluationWeightSchemeDTO scheme() {
        return new EvaluationWeightSchemeDTO(
                702L, "REVIEW_STABLE", "REVIEW_STABLE_V1", "稳定优先", "稳定优先目标",
                "REVIEW", "METRIC_SET_V2", "ACTIVE", 9L, null, null, null, List.of());
    }
}
