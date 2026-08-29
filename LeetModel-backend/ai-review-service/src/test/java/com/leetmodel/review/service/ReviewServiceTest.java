package com.leetmodel.review.service;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentSampleDTO;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.entity.ReviewV1Result;
import com.leetmodel.review.entity.ReviewVersion;
import com.leetmodel.review.enums.ReviewErrorCode;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewVersionMapper;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowRegistry;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {
    @Mock ReviewTaskMapper taskMapper;
    @Mock ReviewV1ResultMapper resultMapper;
    @Mock ReviewVersionMapper versionMapper;
    @Mock SubmissionFeignClient submissionFeignClient;
    @Mock TeamFeignClient teamFeignClient;
    @Mock ReviewWorkflowRegistry workflowRegistry;
    @Mock ReviewTaskLogService logService;
    @Mock ReviewResultPersistenceService persistenceService;
    @Mock ReviewWorkflow workflow;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(taskMapper, resultMapper, versionMapper, submissionFeignClient,
                teamFeignClient, workflowRegistry, logService, persistenceService,
                JsonMapper.builder().findAndAddModules().build());
    }

    @Test
    void createPersistentWaitingTaskWithLockedVersionAndPrompt() {
        when(taskMapper.selectOne(any())).thenReturn(null);
        when(workflowRegistry.required(ReviewService.WORKFLOW_VERSION)).thenReturn(workflow);
        when(workflow.versionCode()).thenReturn(ReviewService.WORKFLOW_VERSION);
        when(workflow.versionId()).thenReturn(1L);
        when(workflow.currentPrompt()).thenReturn("prompt snapshot");
        when(taskMapper.insert(any(ReviewTask.class))).thenAnswer(invocation -> {
            ReviewTask task = invocation.getArgument(0); task.setId(9L); return 1;
        });

        assertEquals(9L, service.createTask(3L, 4L, 5L));
        verify(taskMapper).insert(argThat((ReviewTask task) -> "WAITING".equals(task.getStatus())
                && ReviewService.WORKFLOW_VERSION.equals(task.getWorkflowVersion())
                && "prompt snapshot".equals(task.getPromptSnapshot())
                && task.getTeamId() == 4L && task.getProblemId() == 5L
                && task.getAttemptNo() == 1));
    }

    @Test
    void returnExistingTaskForSameSubmissionAndVersion() {
        ReviewTask existing = new ReviewTask(); existing.setId(12L);
        when(taskMapper.selectOne(any())).thenReturn(existing);
        assertEquals(12L, service.createTask(3L, 4L, 5L));
    }

    @Test
    void onlyOneWorkerCanClaimWaitingTask() {
        ReviewTask task = task(20L, "WAITING");
        when(taskMapper.selectNextWaiting()).thenReturn(task);
        when(taskMapper.claim(any(), any())).thenReturn(0);

        service.processNext();

        verify(submissionFeignClient, never()).getForReview(any());
        verify(workflowRegistry, never()).required(any());
    }

    @Test
    void rejectTaskQueryWhenSubmissionDependencyFails() {
        ReviewTask task = task(21L, "WAITING");
        when(taskMapper.selectById(21L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getTask(21L, 10L));

        assertEquals(ReviewErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), error.getCode());
        verify(teamFeignClient, never()).getMemberIds(any());
    }

    @Test
    void rejectNonMemberFromTaskResult() {
        ReviewTask task = task(22L, "COMPLETED");
        when(taskMapper.selectById(22L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(41L)).thenReturn(Result.ok(java.util.List.of(11L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getTask(22L, 10L));

        assertEquals(ReviewErrorCode.NOT_TEAM_MEMBER.getCode(), error.getCode());
        verify(resultMapper, never()).selectOne(any());
    }

    @Test
    void resetFailedTaskForAuthorizedRetry() {
        ReviewTask task = task(23L, "FAILED");
        task.setRetryCount(1);
        task.setAttemptNo(2);
        task.setErrorMessage("provider unavailable");
        when(taskMapper.selectById(23L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(41L)).thenReturn(Result.ok(java.util.List.of(10L)));

        var result = service.retry(23L, 10L);

        assertEquals("WAITING", result.getStatus());
        assertEquals(2, result.getRetryCount());
        assertEquals(3, result.getAttemptNo());
        verify(taskMapper).resetForRetry(argThat(value -> value.getStartedAt() == null
                && value.getFinishedAt() == null && value.getErrorMessage() == null));
    }

    @Test
    void rejectRetryForCompletedTask() {
        ReviewTask task = task(24L, "COMPLETED");
        when(taskMapper.selectById(24L)).thenReturn(task);
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(41L)).thenReturn(Result.ok(java.util.List.of(10L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.retry(24L, 10L));

        assertEquals(ReviewErrorCode.TASK_NOT_FAILED.getCode(), error.getCode());
        verify(taskMapper, never()).resetForRetry(any());
    }

    @Test
    void markClaimedTaskFailedWhenDependencyFails() {
        ReviewTask task = task(25L, "WAITING");
        ReviewTaskLog runLog = new ReviewTaskLog();
        when(taskMapper.selectNextWaiting()).thenReturn(task);
        when(taskMapper.claim(any(), any())).thenReturn(1);
        when(logService.start(any(), any(), any(), any())).thenReturn(runLog);
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);

        service.processNext();

        verify(logService).fail(any(), any());
        verify(taskMapper).updateById(argThat((ReviewTask value) -> "FAILED".equals(value.getStatus())
                && value.getFinishedAt() != null && value.getErrorMessage() != null));
    }

    @Test
    void returnWaitingSummaryByStableSubmissionId() {
        ReviewTask task = task(26L, "WAITING");
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(resultMapper.selectOne(any())).thenReturn(null);

        var summary = service.getSummaryBySubmission(31L);

        assertEquals(26L, summary.getTaskId());
        assertEquals(31L, summary.getSubmissionId());
        assertEquals("WAITING", summary.getStatus());
        assertNull(summary.getScore());
    }

    @Test
    void rejectSummaryQueryWhenSubmissionHasNoReviewTask() {
        when(taskMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getSummaryBySubmission(99L));

        assertEquals(ReviewErrorCode.TASK_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void listCompletedSummariesSkipsOrphanResult() {
        ReviewV1Result valid = new ReviewV1Result();
        valid.setTaskId(27L);
        valid.setSubmissionId(31L);
        valid.setTeamId(41L);
        valid.setProblemId(51L);
        valid.setScore(new java.math.BigDecimal("88.50"));
        ReviewV1Result orphan = new ReviewV1Result();
        orphan.setTaskId(999L);
        when(resultMapper.selectList(any())).thenReturn(java.util.List.of(valid, orphan));
        when(taskMapper.selectById(27L)).thenReturn(task(27L, "COMPLETED"));
        when(taskMapper.selectById(999L)).thenReturn(null);

        var summaries = service.listCompletedSummaries(51L);

        assertEquals(1, summaries.size());
        assertEquals(new java.math.BigDecimal("88.50"), summaries.get(0).getScore());
    }

    @Test
    void listRecentSummariesKeepsWaitingAndFailedTasks() {
        ReviewTask waiting = task(28L, "WAITING");
        ReviewTask failed = task(29L, "FAILED");
        failed.setErrorMessage("AI 供应商暂不可用");
        when(taskMapper.selectList(any())).thenReturn(java.util.List.of(waiting, failed));
        when(resultMapper.selectOne(any())).thenReturn(null);

        var summaries = service.listRecentSummaries(20);

        assertEquals(2, summaries.size());
        assertEquals("WAITING", summaries.get(0).getStatus());
        assertNull(summaries.get(0).getScore());
        assertEquals("AI 供应商暂不可用", summaries.get(1).getErrorMessage());
    }

    @Test
    void runExperimentUsesTransientTaskWithoutCreatingFormalReview() throws Exception {
        when(workflowRegistry.required(ReviewService.WORKFLOW_VERSION)).thenReturn(workflow);
        when(workflow.versionCode()).thenReturn(ReviewService.WORKFLOW_VERSION);
        when(workflow.versionId()).thenReturn(1L);
        when(workflow.currentPrompt()).thenReturn("locked prompt");
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(workflow.execute(any(), any())).thenReturn(new ReviewWorkflowResult(
                new java.math.BigDecimal("86.50"), "{\"score\":86.5}", "model-a", "call-1"));

        var result = service.runExperiment(31L, ReviewService.WORKFLOW_VERSION);

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals(new java.math.BigDecimal("86.50"), result.getScore());
        assertEquals("call-1", result.getAiCallId());
        verify(workflow).execute(argThat(task -> task.getId() == null
                && "locked prompt".equals(task.getPromptSnapshot())
                && task.getAttemptNo() == 1), any());
        verify(taskMapper, never()).insert(any(ReviewTask.class));
        verify(resultMapper, never()).insert(any(ReviewV1Result.class));
    }

    @Test
    void genericExperimentLocksRunAndModelConfigWithoutFormalTask() throws Exception {
        ReviewVersion enabled = version(ReviewService.WORKFLOW_VERSION, "ENABLED", "SCORE_V1");
        when(versionMapper.selectOne(any())).thenReturn(enabled);
        when(workflowRegistry.required(ReviewService.WORKFLOW_VERSION)).thenReturn(workflow);
        when(workflow.versionId()).thenReturn(1L);
        when(workflow.versionCode()).thenReturn(ReviewService.WORKFLOW_VERSION);
        when(workflow.currentPrompt()).thenReturn("prompt");
        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(workflow.execute(any(), any())).thenReturn(new ReviewWorkflowResult(
                new java.math.BigDecimal("88.5"), "{\"score\":88.5}", "model", "call-1"));
        var request = new AiExperimentRequestDTO("review-slot-1", "REVIEW",
                new AiExperimentSampleDTO("SUBMISSION_REFERENCE", "REVIEW_SUBMISSION_V1",
                        "{\"submissionId\":31}"), ReviewService.WORKFLOW_VERSION,
                "MODEL_CFG_REVIEW_MULTIMODAL_0001", null, "P3",
                "20", "20:101:1", 1, "evaluation:20:20:101:1:attempt:1");

        var result = service.runExperiment(request);

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("review-slot-1", result.getExperimentRunId());
        assertEquals("MODEL_CFG_REVIEW_MULTIMODAL_0001", result.getModelExecutionConfigVersion());
        verify(workflow).execute(argThat(task -> task.getId() == null
                && "review-slot-1".equals(task.getExperimentRunId())
                && "20".equals(task.getEvaluationTaskId())
                && "evaluation:20:20:101:1:attempt:1".equals(task.getExperimentIdempotencyKey())
                && "MODEL_CFG_REVIEW_MULTIMODAL_0001".equals(
                task.getModelExecutionConfigVersion())), any());
        verify(taskMapper, never()).insert(any(ReviewTask.class));
    }

    @Test
    void runExperimentRejectsUnknownVersionBeforeReadingSubmission() {
        when(workflowRegistry.required("UNKNOWN"))
                .thenThrow(new IllegalArgumentException("未知评审版本: UNKNOWN"));

        var result = service.runExperiment(31L, "UNKNOWN");

        assertEquals("FAILED", result.getStatus());
        assertEquals("CONFIGURATION", result.getFailureType());
        assertEquals("评审版本不存在或不可执行", result.getErrorMessage());
        verify(submissionFeignClient, never()).getForReview(any());
    }

    @Test
    void runExperimentClassifiesDependencyAndOutputFailures() throws Exception {
        when(workflowRegistry.required(ReviewService.WORKFLOW_VERSION)).thenReturn(workflow);
        when(submissionFeignClient.getForReview(31L)).thenReturn(null);

        var dependency = service.runExperiment(31L, ReviewService.WORKFLOW_VERSION);

        assertEquals("ENVIRONMENT", dependency.getFailureType());
        assertEquals("实验评审依赖暂不可用", dependency.getErrorMessage());

        when(submissionFeignClient.getForReview(31L)).thenReturn(Result.ok(submission()));
        when(workflow.execute(any(), any()))
                .thenThrow(new IllegalArgumentException("模型输出不符合 BASIC_REVIEW_V1 JSON 契约"));

        var output = service.runExperiment(31L, ReviewService.WORKFLOW_VERSION);

        assertEquals("OUTPUT", output.getFailureType());
        assertEquals("评审版本未产生符合契约的结果", output.getErrorMessage());
    }

    @Test
    void listVersionsReturnsStableVersionFacts() {
        ReviewVersion version = new ReviewVersion();
        version.setId(1L);
        version.setVersionCode("BASIC_REVIEW_V1");
        version.setName("V1 基础 AI 评审");
        version.setDescription("一次多模态调用");
        version.setProcessSummary("PDF 按页渲染后评审");
        version.setStatus("ENABLED");
        when(versionMapper.selectList(any())).thenReturn(java.util.List.of(version));

        var versions = service.listVersions();

        assertEquals(1, versions.size());
        assertEquals("BASIC_REVIEW_V1", versions.get(0).getVersionCode());
        assertEquals("ENABLED", versions.get(0).getStatus());
    }

    @Test
    void featureDefinitionKeepsDisabledVersionsForHistoricalInterpretation() {
        ReviewVersion enabled = version("BASIC_REVIEW_V1", "ENABLED", "REVIEW_RESULT_V1");
        ReviewVersion disabled = version("BASIC_REVIEW_LEGACY", "DISABLED", "REVIEW_RESULT_V0");
        when(versionMapper.selectList(any())).thenReturn(java.util.List.of(enabled, disabled));

        var feature = service.getFeatureDefinition();

        assertEquals("REVIEW", feature.getFeatureCode());
        assertEquals("ai-review-service", feature.getOwnerService());
        assertEquals(2, feature.getWorkflowVersions().size());
        assertEquals("DISABLED", feature.getWorkflowVersions().get(1).getStatus());
        assertEquals("REVIEW_RESULT_V0", feature.getWorkflowVersions().get(1).getOutputSchema());
    }

    private ReviewVersion version(String code, String status, String outputSchema) {
        ReviewVersion version = new ReviewVersion();
        version.setVersionCode(code);
        version.setName(code);
        version.setStatus(status);
        version.setFinalContractVersion(outputSchema);
        return version;
    }

    private ReviewTask task(Long id, String status) {
        ReviewTask task = new ReviewTask();
        task.setId(id);
        task.setSubmissionId(31L);
        task.setTeamId(41L);
        task.setVersionId(1L);
        task.setWorkflowVersion(ReviewService.WORKFLOW_VERSION);
        task.setStatus(status);
        task.setRetryCount(0);
        task.setAttemptNo(1);
        return task;
    }

    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(31L, 41L, 51L, 1, "submissions/41/paper.pdf");
    }
}
