package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationSampleMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import com.leetmodel.evaluation.observability.EvaluationDispatchMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationPersistenceServiceTest {
    private final EvaluationDatasetMapper datasetMapper = mock(EvaluationDatasetMapper.class);
    private final EvaluationSampleMapper sampleMapper = mock(EvaluationSampleMapper.class);
    private final EvaluationTaskMapper taskMapper = mock(EvaluationTaskMapper.class);
    private final EvaluationRunAttemptMapper runMapper = mock(EvaluationRunAttemptMapper.class);
    private final EvaluationDispatchMetrics metrics = mock(EvaluationDispatchMetrics.class);
    private final EvaluationPersistenceService service = new EvaluationPersistenceService(
            datasetMapper, sampleMapper, taskMapper, runMapper, metrics);

    @Test
    void createsStableSlotContextAndAttemptScopedIdempotencyKey() {
        EvaluationTask task = new EvaluationTask();
        task.setId(20L);
        task.setFeatureCode("ASSISTANT");
        task.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TEXT_0001");
        EvaluationRunAttempt run = new EvaluationRunAttempt();
        run.setSampleId(101L);
        run.setRepetitionNo(2);
        run.setAttemptNo(1);

        service.createTask(task, List.of(run));

        ArgumentCaptor<EvaluationRunAttempt> captor = ArgumentCaptor.forClass(EvaluationRunAttempt.class);
        verify(runMapper).insert(captor.capture());
        assertThat(captor.getValue().getSlotKey()).isEqualTo("20:101:2");
        assertThat(captor.getValue().getExperimentRunId()).isEqualTo("assistant-eval:20:101:2");
        assertThat(captor.getValue().getIdempotencyKey())
                .isEqualTo("evaluation:20:20:101:2:attempt:1");
    }

    @Test
    void staleRunningAttemptBecomesUnknownAndNeverCreatesAnotherAttempt() {
        EvaluationRunAttempt stale = new EvaluationRunAttempt();
        stale.setId(301L);
        LocalDateTime now = LocalDateTime.now();
        when(runMapper.markExpiredUnknown(301L, now)).thenReturn(1);

        assertThat(service.recoverExpired(stale, now)).isTrue();

        verify(runMapper, never()).insert(any(EvaluationRunAttempt.class));
        verify(metrics).recoveredUnknown();
    }
}
