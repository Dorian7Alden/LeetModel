package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.evaluation.entity.EvaluationScoreResult;
import com.leetmodel.evaluation.entity.EvaluationScoreResultItem;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationCompletionPersistenceServiceTest {

    private final EvaluationTaskMapper taskMapper = mock(EvaluationTaskMapper.class);
    private final EvaluationScoreResultMapper resultMapper = mock(EvaluationScoreResultMapper.class);
    private final EvaluationScoreResultItemMapper itemMapper = mock(EvaluationScoreResultItemMapper.class);
    private final EvaluationCompletionPersistenceService service =
            new EvaluationCompletionPersistenceService(taskMapper, resultMapper, itemMapper);

    @Test
    void taskRawMetricsAndScoreResultAreSavedAsOneCompletionUnit() {
        EvaluationTask task = new EvaluationTask();
        task.setId(20L);
        EvaluationScoreResult result = new EvaluationScoreResult();
        EvaluationScoreResultItem item = new EvaluationScoreResultItem();
        when(taskMapper.complete(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        doAnswer(invocation -> {
            invocation.<EvaluationScoreResult>getArgument(0).setId(901L);
            return 1;
        }).when(resultMapper).insert(result);

        boolean completed = service.complete(task, 2, 0, metrics(), "{\"raw\":true}",
                new EvaluationScoreResultService.ScoreBundle(result, List.of(item)), LocalDateTime.now());

        assertThat(completed).isTrue();
        assertThat(item.getScoreResultId()).isEqualTo(901L);
        verify(itemMapper).insert(item);
    }

    @Test
    void concurrentSecondCompletionCannotCreateAnotherScoreVersion() {
        EvaluationTask task = new EvaluationTask();
        task.setId(20L);
        when(taskMapper.complete(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        boolean completed = service.complete(task, 2, 0, metrics(), "{}",
                new EvaluationScoreResultService.ScoreBundle(
                        new EvaluationScoreResult(), List.of(new EvaluationScoreResultItem())),
                LocalDateTime.now());

        assertThat(completed).isFalse();
        verify(resultMapper, never()).insert(any(EvaluationScoreResult.class));
        verify(itemMapper, never()).insert(any(EvaluationScoreResultItem.class));
    }

    private EvaluationMetricsCalculator.Metrics metrics() {
        return new EvaluationMetricsCalculator.Metrics(
                new BigDecimal("100"), null, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100"), 100L, new EvaluationRawMetricsDTO());
    }
}
