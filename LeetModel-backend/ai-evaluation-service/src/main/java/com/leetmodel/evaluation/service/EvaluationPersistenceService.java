package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationSample;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationSampleMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import com.leetmodel.evaluation.observability.EvaluationDispatchMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationPersistenceService {

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationSampleMapper sampleMapper;
    private final EvaluationTaskMapper taskMapper;
    private final EvaluationRunAttemptMapper runMapper;
    private final EvaluationDispatchMetrics metrics;

    @Transactional
    public void createDataset(EvaluationDataset dataset, List<EvaluationSample> samples) {
        datasetMapper.insert(dataset);
        for (EvaluationSample sample : samples) {
            sample.setDatasetId(dataset.getId());
            sampleMapper.insert(sample);
        }
    }

    @Transactional
    public void createTask(EvaluationTask task, List<EvaluationRunAttempt> runs) {
        taskMapper.insert(task);
        for (EvaluationRunAttempt run : runs) {
            run.setTaskId(task.getId());
            fillRunIdentity(run, task);
            runMapper.insert(run);
        }
    }

    @Transactional
    public boolean retry(EvaluationTask task, List<EvaluationRunAttempt> retries) {
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.resetForRetry(task.getId(), now) == 0) return false;
        for (EvaluationRunAttempt retry : retries) {
            retry.setTaskId(task.getId());
            fillRunIdentity(retry, task);
            runMapper.insert(retry);
        }
        return true;
    }

    @Transactional
    public boolean recoverExpired(EvaluationRunAttempt stale, LocalDateTime now) {
        boolean recovered = runMapper.markExpiredUnknown(stale.getId(), now) == 1;
        if (recovered) metrics.recoveredUnknown();
        return recovered;
    }

    private void fillRunIdentity(EvaluationRunAttempt run, EvaluationTask task) {
        String slotKey = task.getId() + ":" + run.getSampleId() + ":" + run.getRepetitionNo();
        run.setSlotKey(slotKey);
        run.setExperimentRunId(task.getFeatureCode().toLowerCase() + "-eval:" + slotKey);
        run.setIdempotencyKey("evaluation:" + task.getId() + ":" + slotKey
                + ":attempt:" + run.getAttemptNo());
        run.setModelExecutionConfigVersion(task.getModelExecutionConfigVersion());
        run.setRagIndexVersion(task.getRagIndexVersion());
    }
}
