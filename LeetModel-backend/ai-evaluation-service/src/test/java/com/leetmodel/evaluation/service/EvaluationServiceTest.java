package com.leetmodel.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationSampleCreateDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.AiWorkflowVersionDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
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
import com.leetmodel.evaluation.config.EvaluationScaleProperties;
import com.leetmodel.evaluation.runner.EvaluationRunnerRegistry;
import com.leetmodel.evaluation.runner.AssistantEvaluationRunner;
import com.leetmodel.evaluation.runner.ReviewEvaluationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock AssistantFeignClient assistantFeignClient;
    @Mock AiGatewayFeignClient aiGatewayFeignClient;
    @Mock EvaluationPersistenceService persistenceService;
    @Mock EvaluationMetricsCalculator metricsCalculator;
    @Mock EvaluationWeightSchemeService weightSchemeService;
    @Mock EvaluationScoreResultService scoreResultService;
    @Mock EvaluationCompletionPersistenceService completionPersistenceService;

    private EvaluationService service;

    @BeforeEach
    void setUp() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var runner = new ReviewEvaluationRunner(reviewFeignClient,
                new EvaluationSamplePayloadService(mapper), new EvaluationMetricRegistry(), mapper);
        var assistantRunner = new AssistantEvaluationRunner(assistantFeignClient,
                new EvaluationSamplePayloadService(mapper), new EvaluationMetricRegistry(), mapper);
        var registry = new EvaluationRunnerRegistry(List.of(runner, assistantRunner));
        var estimateService = new EvaluationEstimateService(
                datasetMapper, registry, new EvaluationScaleProperties());
        service = new EvaluationService(datasetMapper, sampleMapper, taskMapper, runMapper,
                submissionFeignClient, aiGatewayFeignClient, registry, estimateService, persistenceService,
                metricsCalculator, new EvaluationMetricRegistry(), weightSchemeService,
                scoreResultService, completionPersistenceService, mapper);
        org.mockito.Mockito.lenient().when(weightSchemeService.requireActiveForTask(any(), any(), any()))
                .thenReturn(weightScheme());
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
    void assistantDatasetStoresVersionedQuestionWithoutSubmissionOrConversationReference() {
        var payload = new EvaluationSamplePayloadDTO("QUESTION", "ASSISTANT_QUESTION_V1",
                "{\"question\":\"如何提交论文？\",\"tags\":[\"提交\"]}");

        var result = service.createDataset(new EvaluationDatasetCreateDTO(
                "客服固定集", null, 1L,
                List.of(new EvaluationSampleCreateDTO(null, "客服样本", payload)),
                "ASSISTANT", "ASSISTANT_DATASET_V1"));

        ArgumentCaptor<EvaluationDataset> datasetCaptor = ArgumentCaptor.forClass(EvaluationDataset.class);
        ArgumentCaptor<List<EvaluationSample>> sampleCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).createDataset(datasetCaptor.capture(), sampleCaptor.capture());
        assertThat(datasetCaptor.getValue().getFeatureCode()).isEqualTo("ASSISTANT");
        assertThat(datasetCaptor.getValue().getDatasetVersion()).isEqualTo("ASSISTANT_DATASET_V1");
        assertThat(sampleCaptor.getValue().get(0).getSubmissionId()).isNull();
        assertThat(sampleCaptor.getValue().get(0).getPayloadJson()).contains("如何提交论文");
        assertThat(result.getSampleCount()).isEqualTo(1);
        verify(submissionFeignClient, never()).getForReview(anyLong());
    }

    @Test
    void taskCreationValidatesEnabledVersionAndCreatesEveryRepeatSlot() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        List<EvaluationSample> samples = List.of(sample(101L, 31L), sample(102L, 32L));
        when(sampleMapper.selectList(any())).thenReturn(samples);
        when(reviewFeignClient.getFeatureDefinition()).thenReturn(Result.ok(feature("ENABLED")));

        var result = service.createTask(new EvaluationTaskCreateDTO(
                10L, "BASIC_REVIEW_V1", 3, "request_001", null, null, 701L));

        ArgumentCaptor<EvaluationTask> taskCaptor = ArgumentCaptor.forClass(EvaluationTask.class);
        ArgumentCaptor<List<EvaluationRunAttempt>> runsCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).createTask(taskCaptor.capture(), runsCaptor.capture());
        assertThat(taskCaptor.getValue().getTotalSlots()).isEqualTo(6);
        assertThat(taskCaptor.getValue().getDatasetVersion()).isEqualTo("REVIEW_DATASET_V1");
        assertThat(taskCaptor.getValue().getMetricSetVersion()).isEqualTo("METRIC_SET_V2");
        assertThat(taskCaptor.getValue().getWeightSchemeVersion()).isEqualTo("REVIEW_BALANCED_V1");
        assertThat(taskCaptor.getValue().getWeightSchemeSnapshotJson()).contains("REVIEW_BALANCED_V1");
        assertThat(taskCaptor.getValue().getMetricDefinitionSnapshotJson())
                .contains("varianceDenominator", "POPULATION_N", "missingValuePolicy");
        assertThat(runsCaptor.getValue()).hasSize(6)
                .allMatch(run -> "WAITING".equals(run.getStatus()) && run.getAttemptNo() == 1);
        assertThat(result.getRuns()).hasSize(6);
    }

    @Test
    void taskCreationSucceedsWithoutWeightSchemeInPureObservationMode() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        List<EvaluationSample> samples = List.of(sample(101L, 31L), sample(102L, 32L));
        when(sampleMapper.selectList(any())).thenReturn(samples);
        when(reviewFeignClient.getFeatureDefinition()).thenReturn(Result.ok(feature("ENABLED")));

        var result = service.createTask(new EvaluationTaskCreateDTO(
                10L, "BASIC_REVIEW_V1", 2, "request_no_scheme", null, null, null));

        ArgumentCaptor<EvaluationTask> taskCaptor = ArgumentCaptor.forClass(EvaluationTask.class);
        ArgumentCaptor<List<EvaluationRunAttempt>> runsCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).createTask(taskCaptor.capture(), runsCaptor.capture());
        assertThat(taskCaptor.getValue().getTotalSlots()).isEqualTo(4);
        assertThat(taskCaptor.getValue().getWeightSchemeId()).isNull();
        assertThat(taskCaptor.getValue().getWeightSchemeVersion()).isNull();
        assertThat(taskCaptor.getValue().getWeightSchemeSnapshotJson()).isNull();
        assertThat(result.getWeightSchemeId()).isNull();
        assertThat(result.getVersionSelectionIndex()).isNull();
    }

    @Test
    void reusedClientRequestMustDescribeSameTask() {
        EvaluationTask existing = task(20L, "WAITING", 2);
        existing.setDatasetId(10L);
        existing.setClientRequestId("request_001");
        when(taskMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.createTask(new EvaluationTaskCreateDTO(
                11L, "BASIC_REVIEW_V1", 2, "request_001", null, null, 701L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41107);
        verify(datasetMapper, never()).selectById(anyLong());
    }

    @Test
    void taskRejectsDisabledOrMissingVersion() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(101L, 31L)));
        when(reviewFeignClient.getFeatureDefinition()).thenReturn(Result.ok(feature("DISABLED")));

        assertThatThrownBy(() -> service.createTask(new EvaluationTaskCreateDTO(
                10L, "BASIC_REVIEW_V1", 1, "request_002", null, null, 701L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41104);
        verify(persistenceService, never()).createTask(any(), any());
    }

    @Test
    void assistantRagTaskLocksModelAndIndexVersions() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        EvaluationDataset dataset = dataset(10L);
        dataset.setFeatureCode("ASSISTANT");
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(201L, null)));
        when(assistantFeignClient.getFeatureDefinition()).thenReturn(Result.ok(assistantFeature()));

        service.createTask(new EvaluationTaskCreateDTO(10L, "ASSISTANT_RAG_V1", 2,
                "assistant_request_1", "MODEL_CFG_ASSISTANT_TEXT_0001", "rag-v1-abc", 701L));

        ArgumentCaptor<EvaluationTask> taskCaptor = ArgumentCaptor.forClass(EvaluationTask.class);
        verify(persistenceService).createTask(taskCaptor.capture(), any());
        assertThat(taskCaptor.getValue().getFeatureCode()).isEqualTo("ASSISTANT");
        assertThat(taskCaptor.getValue().getModelExecutionConfigVersion())
                .isEqualTo("MODEL_CFG_ASSISTANT_TEXT_0001");
        assertThat(taskCaptor.getValue().getRagIndexVersion()).isEqualTo("rag-v1-abc");
    }

    @Test
    void assistantRagTaskRejectsMissingIndexVersion() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        EvaluationDataset dataset = dataset(10L);
        dataset.setFeatureCode("ASSISTANT");
        when(datasetMapper.selectById(10L)).thenReturn(dataset);
        when(sampleMapper.selectList(any())).thenReturn(List.of(sample(201L, null)));
        when(assistantFeignClient.getFeatureDefinition()).thenReturn(Result.ok(assistantFeature()));

        assertThatThrownBy(() -> service.createTask(new EvaluationTaskCreateDTO(
                10L, "ASSISTANT_RAG_V1", 1, "assistant_request_2", null, null, 701L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41104);
        verify(persistenceService, never()).createTask(any(), any());
    }

    @Test
    void workerCompletesSuccessfulSlotWithTraceAndMetrics() {
        EvaluationRunAttempt waiting = run(301L, 20L, 101L, "RUNNING", null);
        waiting.setLeaseToken("token-301");
        EvaluationTask task = task(20L, "WAITING", 1);
        EvaluationSample sample = sample(101L, 31L);
        when(runMapper.selectById(301L)).thenReturn(waiting);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(sampleMapper.selectById(101L)).thenReturn(sample);
        when(reviewFeignClient.runExperimentV2(any())).thenReturn(Result.ok(genericResult(
                "SUCCEEDED", null, "{\"score\":88}", "{\"score\":88.00}",
                "model-a", "call-1", 50_000L, null)));
        EvaluationRunAttempt succeeded = run(301L, 20L, 101L, "SUCCEEDED", null);
        succeeded.setScore(new BigDecimal("88.00"));
        succeeded.setDurationMs(50_000L);
        when(runMapper.selectList(any())).thenReturn(List.of(succeeded));
        when(metricsCalculator.calculate(any(), any(), any())).thenReturn(metrics());

        service.executeClaimed(301L, "token-301");

        verify(runMapper).succeed(org.mockito.ArgumentMatchers.eq(301L),
                org.mockito.ArgumentMatchers.eq("token-301"),
                org.mockito.ArgumentMatchers.argThat(score ->
                        score.compareTo(new BigDecimal("88.00")) == 0),
                org.mockito.ArgumentMatchers.eq("{\"score\":88}"),
                org.mockito.ArgumentMatchers.eq("{\"REVIEW_SCORE\":88.0}"),
                org.mockito.ArgumentMatchers.eq("model-a"),
                org.mockito.ArgumentMatchers.eq("MODEL_CFG_REVIEW_MULTIMODAL_0001"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("call-1"),
                org.mockito.ArgumentMatchers.eq(50_000L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(completionPersistenceService).complete(any(),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(0),
                any(), any(), any(), any());
    }

    @Test
    void workerThatLosesClaimDoesNotCallReviewService() {
        EvaluationRunAttempt claimed = run(301L, 20L, 101L, "RUNNING", null);
        claimed.setLeaseToken("new-owner-token");
        when(runMapper.selectById(301L)).thenReturn(claimed);

        service.executeClaimed(301L, "stale-token");

        verify(reviewFeignClient, never()).runExperimentV2(any());
        verify(taskMapper, never()).markRunning(anyLong(), any());
    }

    @Test
    void assistantWorkerPersistsDigestCallAndLockedRagVersion() {
        EvaluationRunAttempt waiting = run(401L, 30L, 201L, "RUNNING", null);
        waiting.setLeaseToken("token-401");
        waiting.setExperimentRunId("assistant-eval:30:201:1");
        EvaluationTask task = task(30L, "WAITING", 1);
        task.setFeatureCode("ASSISTANT");
        task.setWorkflowVersion("ASSISTANT_RAG_V1");
        task.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TEXT_0001");
        task.setRagIndexVersion("rag-v1-abc");
        EvaluationSample sample = sample(201L, null);
        sample.setSampleType("QUESTION");
        sample.setPayloadSchemaVersion("ASSISTANT_QUESTION_V1");
        sample.setPayloadJson("{\"question\":\"如何提交论文？\"}");
        when(runMapper.selectById(401L)).thenReturn(waiting);
        when(taskMapper.selectById(30L)).thenReturn(task);
        when(sampleMapper.selectById(201L)).thenReturn(sample);
        when(assistantFeignClient.runExperiment(any())).thenReturn(Result.ok(
                new AiExperimentResultDTO("assistant-eval:30:201:1", "ASSISTANT",
                        "ASSISTANT_RAG_V1", "MODEL_CFG_ASSISTANT_TEXT_0001", "rag-v1-abc",
                        "SUCCEEDED", null, "ASSISTANT_REPLY_V1", "{\"answer\":\"敏感回答\"}",
                        "ASSISTANT_RUN_METRICS_V1", "{}", "model", "call-a", 20L, null)));
        EvaluationRunAttempt succeeded = run(401L, 30L, 201L, "SUCCEEDED", null);
        succeeded.setDurationMs(20L);
        when(runMapper.selectList(any())).thenReturn(List.of(succeeded));
        when(metricsCalculator.calculate(any(), any(), any())).thenReturn(metrics());

        service.executeClaimed(401L, "token-401");

        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);
        verify(runMapper).succeed(org.mockito.ArgumentMatchers.eq(401L),
                org.mockito.ArgumentMatchers.eq("token-401"),
                org.mockito.ArgumentMatchers.isNull(), summary.capture(),
                org.mockito.ArgumentMatchers.eq("{\"STRUCTURE_VALID_RATE\":100}"),
                org.mockito.ArgumentMatchers.eq("model"),
                org.mockito.ArgumentMatchers.eq("MODEL_CFG_ASSISTANT_TEXT_0001"),
                org.mockito.ArgumentMatchers.eq("rag-v1-abc"),
                org.mockito.ArgumentMatchers.eq("call-a"), org.mockito.ArgumentMatchers.eq(20L), any());
        assertThat(summary.getValue()).contains("answerSha256").doesNotContain("敏感回答");
    }

    @Test
    void workerEnvironmentFailureBlocksMetricsAndKeepsRetryPath() {
        EvaluationRunAttempt waiting = run(301L, 20L, 101L, "RUNNING", null);
        waiting.setLeaseToken("token-301");
        EvaluationTask task = task(20L, "WAITING", 1);
        when(runMapper.selectById(301L)).thenReturn(waiting);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(sampleMapper.selectById(101L)).thenReturn(sample(101L, 31L));
        when(reviewFeignClient.runExperimentV2(any())).thenReturn(null);
        EvaluationRunAttempt failed = run(301L, 20L, 101L, "FAILED", "ENVIRONMENT");
        when(runMapper.selectList(any())).thenReturn(List.of(failed));

        service.executeClaimed(301L, "token-301");

        verify(runMapper).fail(org.mockito.ArgumentMatchers.eq(301L),
                org.mockito.ArgumentMatchers.eq("token-301"),
                org.mockito.ArgumentMatchers.eq("ENVIRONMENT"),
                org.mockito.ArgumentMatchers.isNull(), anyLong(), any(), any());
        verify(taskMapper).fail(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1), any(), any());
        verify(metricsCalculator, never()).calculate(any(), any(), any());
    }

    @Test
    void restartedWorkerNeverDispatchesAlreadySuccessfulSlot() {
        when(runMapper.selectById(301L)).thenReturn(run(301L, 20L, 101L, "SUCCEEDED", null));

        service.executeClaimed(301L, "old-token");

        verify(reviewFeignClient, never()).runExperimentV2(any());
        verify(assistantFeignClient, never()).runExperiment(any());
        verify(taskMapper, never()).markRunning(anyLong(), any());
    }

    @Test
    void outputFailureIsAComparableVersionResultInsteadOfEnvironmentFailure() {
        EvaluationRunAttempt waiting = run(301L, 20L, 101L, "RUNNING", null);
        waiting.setLeaseToken("token-301");
        EvaluationTask task = task(20L, "WAITING", 1);
        when(runMapper.selectById(301L)).thenReturn(waiting);
        when(taskMapper.selectById(20L)).thenReturn(task);
        when(sampleMapper.selectById(101L)).thenReturn(sample(101L, 31L));
        when(reviewFeignClient.runExperimentV2(any())).thenReturn(Result.ok(genericResult(
                "FAILED", "OUTPUT", null, null, null, null, 100L,
                "评审版本未产生符合契约的结果")));
        EvaluationRunAttempt failed = run(301L, 20L, 101L, "FAILED", "OUTPUT");
        failed.setDurationMs(100L);
        when(runMapper.selectList(any())).thenReturn(List.of(failed));
        when(metricsCalculator.calculate(any(), any(), any())).thenReturn(metrics());

        service.executeClaimed(301L, "token-301");

        verify(completionPersistenceService).complete(any(),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(1),
                any(), any(), any(), any());
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
    void recoveryMarksInterruptedAttemptUnknownWithoutBlindRetry() {
        EvaluationRunAttempt stale = run(301L, 20L, 101L, "RUNNING", null);
        when(runMapper.selectExpired(any())).thenReturn(List.of(stale));
        when(persistenceService.recoverExpired(any(), any())).thenReturn(true);
        EvaluationTask task = task(20L, "RUNNING", 1);
        when(taskMapper.selectById(20L)).thenReturn(task);
        EvaluationRunAttempt unknown = run(301L, 20L, 101L, "UNKNOWN", "UNKNOWN");
        when(runMapper.selectList(any())).thenReturn(List.of(unknown));

        service.recoverStaleRuns();

        verify(persistenceService).recoverExpired(org.mockito.ArgumentMatchers.eq(stale), any());
        verify(taskMapper).fail(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.contains("禁止自动或人工盲目重试"), any());
        verify(persistenceService, never()).retry(any(), any());
    }

    @Test
    void pauseStopsNewDispatchAndRecordsOperator() {
        EvaluationTask running = task(20L, "RUNNING", 2);
        EvaluationTask paused = task(20L, "PAUSED", 2);
        paused.setLastOperatedBy(7L);
        paused.setLastOperation("PAUSE");
        when(taskMapper.selectById(20L)).thenReturn(running, paused);
        when(taskMapper.pause(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(1);

        var result = service.pause(20L, 7L);

        assertThat(result.getStatus()).isEqualTo("PAUSED");
        assertThat(result.getLastOperatedBy()).isEqualTo(7L);
        verify(runMapper, never()).claim(anyLong(), any(), any(), any(), any());
    }

    @Test
    void resumeLeavesSuccessfulSlotsAndContinuesRemainingWaitingSlots() {
        EvaluationTask paused = task(20L, "PAUSED", 2);
        EvaluationTask waiting = task(20L, "WAITING", 2);
        when(taskMapper.selectById(20L)).thenReturn(paused, waiting, waiting, waiting);
        when(taskMapper.resume(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(1);
        EvaluationRunAttempt success = run(301L, 20L, 101L, "SUCCEEDED", null);
        EvaluationRunAttempt remaining = run(302L, 20L, 101L, "WAITING", null);
        remaining.setRepetitionNo(2);
        when(runMapper.selectList(any())).thenReturn(List.of(success, remaining));

        var result = service.resume(20L, 7L);

        verify(taskMapper).updateProgress(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("RUNNING"), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.isNull(), any());
        verify(persistenceService, never()).retry(any(), any());
        assertThat(result.getRuns()).hasSize(2);
    }

    @Test
    void cancelKeepsHistoryCancelsWaitingSlotsAndRequestsGatewayCancellation() {
        EvaluationTask running = task(20L, "RUNNING", 2);
        EvaluationTask cancelled = task(20L, "CANCELLED", 2);
        when(taskMapper.selectById(20L)).thenReturn(running, cancelled);
        when(taskMapper.cancel(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(1);
        AiQueueTaskDTO queue = new AiQueueTaskDTO();
        queue.setTaskId("queue-1");
        queue.setState("QUEUED");
        when(aiGatewayFeignClient.listQueueTasks(any())).thenReturn(Result.ok(List.of(queue)));

        var result = service.cancel(20L, 7L);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(runMapper).cancelWaiting(org.mockito.ArgumentMatchers.eq(20L), any());
        verify(aiGatewayFeignClient).cancelQueueTask("queue-1");
        verify(runMapper, never()).delete(any());
    }

    @Test
    void comparisonUsesLatestCompletedResultPerVersionAndRanksByOverallScore() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        EvaluationTask v1Latest = task(21L, "COMPLETED", 2);
        v1Latest.setOverallScore(new BigDecimal("82.00"));
        EvaluationTask v2 = task(22L, "COMPLETED", 2);
        v2.setWorkflowVersion("BASIC_REVIEW_V2");
        v2.setMetricDefinitionSnapshotJson("{\"parameters\":{},\"metricSetVersion\":\"METRIC_SET_V2\"}");
        v2.setOverallScore(new BigDecimal("91.00"));
        EvaluationTask v1Old = task(20L, "COMPLETED", 2);
        v1Old.setOverallScore(new BigDecimal("70.00"));
        when(taskMapper.selectList(any())).thenReturn(List.of(v1Latest, v2, v1Old));

        var comparison = service.compare(10L, 2);

        assertThat(comparison.getVersions()).extracting("taskId")
                .containsExactly(22L, 21L);
        assertThat(comparison.getVersions()).extracting("workflowVersion")
                .containsExactly("BASIC_REVIEW_V2", "BASIC_REVIEW_V1");
        assertThat(comparison.getComparable()).isTrue();
        assertThat(comparison.getRankingApplied()).isTrue();
        assertThat(comparison.getIncompatibilityReasons()).isEmpty();
    }

    @Test
    void comparisonKeepsSideBySideOrderButRejectsRankingAcrossMetricSnapshots() {
        when(datasetMapper.selectById(10L)).thenReturn(dataset(10L));
        EvaluationTask current = task(21L, "COMPLETED", 2);
        current.setOverallScore(new BigDecimal("82.00"));
        EvaluationTask legacy = task(22L, "COMPLETED", 2);
        legacy.setWorkflowVersion("BASIC_REVIEW_V2");
        legacy.setMetricSetVersion("METRIC_SET_V1");
        legacy.setMetricDefinitionSnapshotJson("{\"metricSetVersion\":\"METRIC_SET_V1\"}");
        legacy.setOverallScore(new BigDecimal("99.00"));
        when(taskMapper.selectList(any())).thenReturn(List.of(current, legacy));

        var comparison = service.compare(10L, 2);

        assertThat(comparison.getComparable()).isFalse();
        assertThat(comparison.getRankingApplied()).isFalse();
        assertThat(comparison.getIncompatibilityReasons())
                .contains("metricSetVersion 不一致或缺失", "指标定义或参数快照不一致或缺失");
        assertThat(comparison.getVersions()).extracting("taskId").containsExactly(21L, 22L);
    }

    @Test
    void historicalTaskWithoutRawMetricsRemainsReadableAsLegacySnapshot() {
        EvaluationTask legacy = task(19L, "COMPLETED", 1);
        legacy.setMetricSetVersion("LEGACY_REVIEW_METRICS_V1");
        legacy.setMetricDefinitionSnapshotJson(
                "{\"metricSetVersion\":\"LEGACY_REVIEW_METRICS_V1\",\"legacyOverallScore\":true}");
        legacy.setRawMetricsJson(null);
        legacy.setOverallScore(new BigDecimal("76.50"));
        when(taskMapper.selectById(19L)).thenReturn(legacy);
        when(runMapper.selectList(any())).thenReturn(List.of());
        when(sampleMapper.selectList(any())).thenReturn(List.of());

        var detail = service.getTask(19L);

        assertThat(detail.getMetricSetVersion()).isEqualTo("LEGACY_REVIEW_METRICS_V1");
        assertThat(detail.getOverallScore()).isEqualByComparingTo("76.50");
        assertThat(detail.getRawMetrics()).isNull();
        assertThat(detail.getRuns()).isEmpty();
    }

    private EvaluationDataset dataset(Long id) {
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setId(id);
        dataset.setStatus("LOCKED");
        dataset.setFeatureCode("REVIEW");
        dataset.setDatasetVersion("REVIEW_DATASET_V1");
        dataset.setSampleSchemaVersion("REVIEW_SUBMISSION_V1");
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
        task.setDatasetVersion("REVIEW_DATASET_V1");
        task.setFeatureCode("REVIEW");
        task.setWorkflowVersion("BASIC_REVIEW_V1");
        task.setModelExecutionConfigVersion("MODEL_CFG_REVIEW_MULTIMODAL_0001");
        task.setMetricSetVersion("METRIC_SET_V2");
        task.setMetricDefinitionSnapshotJson("{\"metricSetVersion\":\"METRIC_SET_V2\",\"parameters\":{}}");
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

    private AiFeatureDefinitionDTO feature(String status) {
        return new AiFeatureDefinitionDTO("REVIEW", "论文评审", "ai-review-service",
                List.of("REVIEW_SUBMISSION_V1"), List.of("REVIEW_SCORE"),
                List.of(new AiWorkflowVersionDTO("BASIC_REVIEW_V1", "V1", status,
                        "REVIEW_SUBMISSION_V1", "REVIEW_OUTPUT_V1", "兼容")));
    }

    private AiFeatureDefinitionDTO assistantFeature() {
        return new AiFeatureDefinitionDTO("ASSISTANT", "AI客服", "ai-assistant-service",
                List.of("ASSISTANT_QUESTION_V1"), List.of("STRUCTURE_VALID_RATE"),
                List.of(new AiWorkflowVersionDTO("ASSISTANT_NO_RAG_V1", "无RAG", "ENABLED",
                                "ASSISTANT_QUESTION_V1", "ASSISTANT_REPLY_V1", "兼容"),
                        new AiWorkflowVersionDTO("ASSISTANT_RAG_V1", "RAG", "ENABLED",
                                "ASSISTANT_QUESTION_V1", "ASSISTANT_REPLY_V1", "锁定索引")));
    }

    private AiExperimentResultDTO genericResult(String status, String failureType,
                                                String outputJson, String metricsJson,
                                                String model, String callId, Long duration,
                                                String error) {
        return new AiExperimentResultDTO("review-eval:20:101:1", "REVIEW",
                "BASIC_REVIEW_V1", "MODEL_CFG_REVIEW_MULTIMODAL_0001", null,
                status, failureType, "SCORE_V1", outputJson,
                "REVIEW_RUN_METRICS_V1", metricsJson, model, callId, duration, error);
    }

    private EvaluationMetricsCalculator.Metrics metrics() {
        return new EvaluationMetricsCalculator.Metrics(
                new BigDecimal("100.00"), null, new BigDecimal("100.00"),
                new BigDecimal("100.00"), new BigDecimal("100.00"), 50_000L,
                new EvaluationRawMetricsDTO());
    }

    private EvaluationWeightSchemeDTO weightScheme() {
        return new EvaluationWeightSchemeDTO(
                701L, "REVIEW_BALANCED", "REVIEW_BALANCED_V1", "均衡方案", "测试目标",
                "REVIEW", "METRIC_SET_V2", "ACTIVE", 9L, LocalDateTime.now(),
                null, null, List.of());
    }
}
