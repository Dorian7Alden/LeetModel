package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationSample;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationSampleMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
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
            runMapper.insert(run);
        }
    }

    @Transactional
    public boolean retry(EvaluationTask task, List<EvaluationRunAttempt> retries) {
        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.resetForRetry(task.getId(), now) == 0) return false;
        for (EvaluationRunAttempt retry : retries) {
            retry.setTaskId(task.getId());
            runMapper.insert(retry);
        }
        return true;
    }

    @Transactional
    public boolean recoverStale(EvaluationRunAttempt stale, LocalDateTime cutoff) {
        LocalDateTime now = LocalDateTime.now();
        if (runMapper.failStale(stale.getId(), cutoff, now) == 0) return false;
        EvaluationRunAttempt retry = new EvaluationRunAttempt();
        retry.setTaskId(stale.getTaskId());
        retry.setSampleId(stale.getSampleId());
        retry.setRepetitionNo(stale.getRepetitionNo());
        retry.setAttemptNo(stale.getAttemptNo() + 1);
        retry.setStatus("WAITING");
        retry.setCreateTime(now);
        retry.setUpdateTime(now);
        runMapper.insert(retry);
        return true;
    }
}
