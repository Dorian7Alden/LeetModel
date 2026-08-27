package com.leetmodel.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationSampleCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.ReviewExperimentResultDTO;
import com.leetmodel.common.api.dto.ReviewVersionDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationSample;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationSampleMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class EvaluationServiceTest {

    @Mock EvaluationDatasetMapper datasetMapper;
    @Mock EvaluationSampleMapper sampleMapper;
    @Mock EvaluationTaskMapper taskMapper;
    @Mock EvaluationRunAttemptMapper runMapper;
    @Mock SubmissionFeignClient submissionFeignClient;
    @Mock ReviewFeignClient reviewFeignClient;
    @Mock EvaluationPersistenceService persistenceService;
    @Mock EvaluationMetricsCalculator metricsCalculator;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(datasetMapper, sampleMapper, taskMapper, runMapper,
                submissionFeignClient, reviewFeignClient, persistenceService, metricsCalculator);
        ReflectionTestUtils.setField(service, "staleMinutes", 15L);
    }

    @Test
    void datasetRejectsDuplicateSubmissionBeforeCallingDependency() {
        var request = new EvaluationDatasetCreateDTO("固定集", null, 1L,
                List.of(new EvaluationSampleCreateDTO(31L, null),
                        new EvaluationSampleCreateDTO(31L, "重复")));

        assertThatThrownBy(() -> service.createDataset(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41102);
        verify(submissionFeignClient, never()).getForReview(anyLong());
    }

    @Test
    void datasetLocksResolvedSubmissionFactsWithoutOwningPdf() {
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission(31L, 41L, 51L)));
        when(submissionFeignClient.getForReview(32L)).thenReturn(Result.ok(submission(32L, 42L, 52L)));

        var result = service.createDataset(new EvaluationDatasetCreateDTO(
                "  MVP 固定集  ", " 两道题 ", 1L,
                List.of(new EvaluationSampleCreateDTO(31L, "样本 A"),
                        new EvaluationSampleCreateDTO(32L, null))));

        ArgumentCaptor<EvaluationDataset> datasetCaptor = ArgumentCaptor.forClass(EvaluationDataset.class);
        ArgumentCaptor<List<EvaluationSample>> samplesCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).createDataset(datasetCaptor.capture(), samplesCaptor.capture());
        assertThat(datasetCaptor.getValue().getStatus()).isEqualTo("LOCKED");
        assertThat(datasetCaptor.getValue().getName()).isEqualTo("MVP 固定集");
        assertThat(samplesCaptor.getValue()).extracting(EvaluationSample::getProblemId)
                .containsExactly(51L, 52L);
        assertThat(result.getSampleCount()).isEqualTo(2);
    }

    @Test
    void datasetStopsWhenSubmissionFactsAreUnavailable() {
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);

        assertThatThrownBy(() -> service.createDataset(new EvaluationDatasetCreateDTO(
                "固定集", null, 1L, List.of(new EvaluationSampleCreateDTO(31L, null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(51101);
        verify(persistenceService, never()).createDataset(any(), any());
    }

    @Test
    void taskCreationValidatesEnabledVersionAndCreatesEveryRepeatSlot() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        List<EvaluationSample> samples = List.of(sample(101L, 31L), sample(102L, 32L));
        when(sampleMapper.selectList(any())).thenReturn(samples);
        when(reviewFeignClient.listVersions()).thenReturn(Result.ok(List.of(version("ENABLED"))));

        var result = service.createTask(new EvaluationTaskCreateDTO(
                10L, "BASIC_REVIEW_V1", 3, "request_001"));

        ArgumentCaptor<EvaluationTask> taskCaptor = ArgumentCaptor.forClass(EvaluationTask.class);
        ArgumentCaptor<List<EvaluationRunAttempt>> runsCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).createTask(taskCaptor.capture(), runsCaptor.capture());
        assertThat(taskCaptor.getValue().getTotalSlots()).isEqualTo(6);
        assertThat(runsCaptor.getValue()).hasSize(6)
                .allMatch(run -> "WAITING".equals(run.getStatus()) && run.getAttemptNo() == 1);
        assertThat(result.getRuns()).hasSize(6);
    }

    @Test
    void reusedClientRequestMustDescribeSameTask() {
        EvaluationTask existing = task(20L, "WAITING", 2);
        existing.setDatasetId(10L);
        existing.setClientRequestId("request_001");
        when(taskMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.createTask(new EvaluationTaskCreateDTO(
                11L, "BASIC_REVIEW_V1", 2, "request_001")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41107);
        verify(datasetMapper, never()).selectById(anyLong());
    }

    @Test
    void taskRejectsDisabledOrMissingVersion() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(101L, 31L)));
        when(reviewFeignClient.listVersions()).thenReturn(Result.ok(List.of(version("DISABLED"))));

        assertThatThrownBy(() -> service.createTask(new EvaluationTaskCreateDTO(
                10L, "BASIC_REVIEW_V1", 1, "request_002")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41104);
        verify(persistenceService, never()).createTask(any(), any());
    }

    @Test
    void workerCompletesSuccessfulSlotWithTraceAndMetrics() {
        EvaluationRunAttempt waiting = run(301L, 20L, 101L, "WAITING", null);
        EvaluationTask task = task(20L, "WAITING", 1);
        EvaluationSample sample = sample(101L, 31L);
        when(runMapper.selectNextWaiting()).thenReturn(waiting);
        when(runMapper.claim(anyLong(), any())).thenReturn(1);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(sampleMapper.selectById(101L)).thenReturn(sample);
        when(reviewFeignClient.runExperiment(any())).thenReturn(Result.ok(new ReviewExperimentResultDTO(
                31L, 51L, "BASIC_REVIEW_V1", "SUCCEEDED", null,
                new BigDecimal("88.00"), "{\"score\":88}", "model-a", "call-1",
                50_000L, null)));
        EvaluationRunAttempt succeeded = run(301L, 20L, 101L, "SUCCEEDED", null);
        succeeded.setScore(new BigDecimal("88.00"));
        succeeded.setDurationMs(50_000L);
        when(runMapper.selectList(any())).thenReturn(List.of(succeeded));
        when(metricsCalculator.calculate(any(), any())).thenReturn(metrics());

        service.processNext();

        verify(runMapper).succeed(org.mockito.ArgumentMatchers.eq(301L),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("88.00")),
                org.mockito.ArgumentMatchers.eq("{\"score\":88}"),
                org.mockito.ArgumentMatchers.eq("model-a"), org.mockito.ArgumentMatchers.eq("call-1"),
                org.mockito.ArgumentMatchers.eq(50_000L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(taskMapper).complete(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(0),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void workerThatLosesClaimDoesNotCallReviewService() {
        when(runMapper.selectNextWaiting()).thenReturn(run(301L, 20L, 101L, "WAITING", null));
        when(runMapper.claim(anyLong(), any())).thenReturn(0);

        service.processNext();

        verify(reviewFeignClient, never()).runExperiment(any());
        verify(taskMapper, never()).markRunning(anyLong(), any());
    }

    @Test
    void workerEnvironmentFailureBlocksMetricsAndKeepsRetryPath() {
        EvaluationRunAttempt waiting = run(301L, 20L, 101L, "WAITING", null);
        EvaluationTask task = task(20L, "WAITING", 1);
        when(runMapper.selectNextWaiting()).thenReturn(waiting);
        when(runMapper.claim(anyLong(), any())).thenReturn(1);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(sampleMapper.selectById(101L)).thenReturn(sample(101L, 31L));
        when(reviewFeignClient.runExperiment(any())).thenReturn(null);
        EvaluationRunAttempt failed = run(301L, 20L, 101L, "FAILED", "ENVIRONMENT");
        when(runMapper.selectList(any())).thenReturn(List.of(failed));

        service.processNext();

        verify(runMapper).fail(org.mockito.ArgumentMatchers.eq(301L),
                org.mockito.ArgumentMatchers.eq("ENVIRONMENT"), anyLong(), any(), any());
        verify(taskMapper).fail(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1), any(), any());
        verify(metricsCalculator, never()).calculate(any(), any());
    }

    @Test
    void outputFailureIsAComparableVersionResultInsteadOfEnvironmentFailure() {
        EvaluationRunAttempt waiting = run(301L, 20L, 101L, "WAITING", null);
        EvaluationTask task = task(20L, "WAITING", 1);
        when(runMapper.selectNextWaiting()).thenReturn(waiting);
        when(runMapper.claim(anyLong(), any())).thenReturn(1);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(sampleMapper.selectById(101L)).thenReturn(sample(101L, 31L));
        when(reviewFeignClient.runExperiment(any())).thenReturn(Result.ok(new ReviewExperimentResultDTO(
                31L, null, "BASIC_REVIEW_V1", "FAILED", "OUTPUT",
                null, null, null, null, 100L, "评审版本未产生符合契约的结果")));
        EvaluationRunAttempt failed = run(301L, 20L, 101L, "FAILED", "OUTPUT");
        failed.setDurationMs(100L);
        when(runMapper.selectList(any())).thenReturn(List.of(failed));
        when(metricsCalculator.calculate(any(), any())).thenReturn(metrics());

        service.processNext();

        verify(taskMapper).complete(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(1),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryCreatesNewAttemptOnlyForLatestEnvironmentFailures() {
        EvaluationTask failedTask = task(20L, "FAILED", 2);
        EvaluationTask waitingTask = task(20L, "WAITING", 2);
        when(taskMapper.selectById(20L)).thenReturn(failedTask, waitingTask);
        EvaluationRunAttempt success = run(301L, 20L, 101L, "SUCCEEDED", null);
        EvaluationRunAttempt environment = run(302L, 20L, 101L, "FAILED", "ENVIRONMENT");
        environment.setRepetitionNo(2);
        when(runMapper.selectList(any())).thenReturn(List.of(success, environment));
        when(persistenceService.retry(any(), any())).thenReturn(true);
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(101L, 31L)));

        var result = service.retry(20L);

        ArgumentCaptor<List<EvaluationRunAttempt>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).retry(any(), captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(retry -> {
            assertThat(retry.getRepetitionNo()).isEqualTo(2);
            assertThat(retry.getAttemptNo()).isEqualTo(2);
            assertThat(retry.getStatus()).isEqualTo("WAITING");
        });
        verify(taskMapper).updateProgress(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("WAITING"), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.isNull(), any());
        assertThat(result.getStatus()).isEqualTo("WAITING");
    }

    @Test
    void recoveryPreservesInterruptedAttemptAndQueuesNextAttempt() {
        EvaluationRunAttempt stale = run(301L, 20L, 101L, "RUNNING", null);
        when(runMapper.selectStale(any())).thenReturn(List.of(stale));
        when(persistenceService.recoverStale(any(), any())).thenReturn(true);
        EvaluationTask task = task(20L, "RUNNING", 1);
        when(taskMapper.selectById(20L)).thenReturn(task);
        EvaluationRunAttempt retry = run(302L, 20L, 101L, "WAITING", null);
        retry.setAttemptNo(2);
        when(runMapper.selectList(any())).thenReturn(List.of(stale, retry));

        service.recoverStaleRuns();

        verify(persistenceService).recoverStale(org.mockito.ArgumentMatchers.eq(stale), any());
        verify(taskMapper).updateProgress(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("RUNNING"), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void comparisonUsesLatestCompletedResultPerVersionAndRanksByOverallScore() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        EvaluationTask v1Latest = task(21L, "COMPLETED", 2);
        v1Latest.setOverallScore(new BigDecimal("82.00"));
        EvaluationTask v2 = task(22L, "COMPLETED", 2);
        v2.setWorkflowVersion("BASIC_REVIEW_V2");
        v2.setOverallScore(new BigDecimal("91.00"));
        EvaluationTask v1Old = task(20L, "COMPLETED", 2);
        v1Old.setOverallScore(new BigDecimal("70.00"));
        when(taskMapper.selectList(any())).thenReturn(List.of(v1Latest, v2, v1Old));

        var comparison = service.compare(10L, 2);

        assertThat(comparison.getVersions()).extracting("taskId")
                .containsExactly(22L, 21L);
        assertThat(comparison.getVersions()).extracting("workflowVersion")
                .containsExactly("BASIC_REVIEW_V2", "BASIC_REVIEW_V1");
    }

    private EvaluationDataset dataset(Long id) {
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setId(id);
        dataset.setStatus("LOCKED");
        dataset.setSampleCount(2);
        return dataset;
    }

    private EvaluationSample sample(Long id, Long submissionId) {
        EvaluationSample sample = new EvaluationSample();
        sample.setId(id);
        sample.setDatasetId(10L);
        sample.setSubmissionId(submissionId);
        sample.setTeamId(41L);
        sample.setProblemId(51L);
        sample.setSortOrder(1);
        return sample;
    }

    private EvaluationTask task(Long id, String status, int totalSlots) {
        EvaluationTask task = new EvaluationTask();
        task.setId(id);
        task.setDatasetId(10L);
        task.setWorkflowVersion("BASIC_REVIEW_V1");
        task.setRepeatCount(totalSlots);
        task.setStatus(status);
        task.setTotalSlots(totalSlots);
        task.setTerminalSlots(0);
        task.setFailedSlots(0);
        task.setEnvironmentFailures(0);
        task.setRetryCount(0);
        task.setCreateTime(LocalDateTime.now());
        return task;
    }

    private EvaluationRunAttempt run(Long id, Long taskId, Long sampleId,
                                     String status, String failureType) {
        EvaluationRunAttempt run = new EvaluationRunAttempt();
        run.setId(id);
        run.setTaskId(taskId);
        run.setSampleId(sampleId);
        run.setRepetitionNo(1);
        run.setAttemptNo(1);
        run.setStatus(status);
        run.setFailureType(failureType);
        return run;
    }

    private SubmissionReviewDTO submission(Long id, Long teamId, Long problemId) {
        return new SubmissionReviewDTO(id, teamId, problemId, 1,
                "submissions/" + teamId + "/paper.pdf");
    }

    private ReviewVersionDTO version(String status) {
        return new ReviewVersionDTO(1L, "BASIC_REVIEW_V1", "V1", "说明", "流程", status);
    }

    private EvaluationMetricsCalculator.Metrics metrics() {
        return new EvaluationMetricsCalculator.Metrics(
                new BigDecimal("100.00"), null, new BigDecimal("100.00"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), 50_000L);
    }
}
