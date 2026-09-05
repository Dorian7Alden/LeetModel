package com.leetmodel.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClientException;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.enums.SuggestionErrorCode;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.vo.SuggestionVO;
import com.leetmodel.suggestion.workflow.SuggestionV1Workflow;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    private static final Long SUBMISSION_ID = 101L;
    private static final Long TEAM_ID = 11L;
    private static final Long PROBLEM_ID = 51L;
    private static final Long USER_ID = 7L;

    @Mock
    private SuggestionTaskMapper taskMapper;
    @Mock
    private SubmissionFeignClient submissionFeignClient;
    @Mock
    private ReviewFeignClient reviewFeignClient;
    @Mock
    private ProblemFeignClient problemFeignClient;
    @Mock
    private TeamFeignClient teamFeignClient;
    @Mock
    private SuggestionV1Workflow workflow;

    private SuggestionService service;

    @BeforeEach
    void setUp() {
        service = new SuggestionService(
                taskMapper, submissionFeignClient, reviewFeignClient, problemFeignClient,
                teamFeignClient, workflow, new ObjectMapper());
    }

    @Test
    void createRequiresMembershipFinalSubmissionAndCompletedReview() {
        prepareValidCreationFacts();
        when(workflow.currentPrompt()).thenReturn("prompt-v1");
        doAnswer(invocation -> {
            SuggestionTask task = invocation.getArgument(0);
            task.setId(9001L);
            return 1;
        }).when(taskMapper).insert(any(SuggestionTask.class));

        SuggestionVO result = service.create(SUBMISSION_ID, USER_ID);

        ArgumentCaptor<SuggestionTask> captor = ArgumentCaptor.forClass(SuggestionTask.class);
        verify(taskMapper).insert(captor.capture());
        SuggestionTask saved = captor.getValue();
        assertThat(saved.getSubmissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(saved.getReviewTaskId()).isEqualTo(5001L);
        assertThat(saved.getStatus()).isEqualTo("WAITING");
        assertThat(saved.getPromptSnapshot()).isEqualTo("prompt-v1");
        assertThat(result.getTaskId()).isEqualTo(9001L);
    }

    @Test
    void createStopsBeforeLeakingFinalOrReviewStateToNonMember() {
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(99L)));

        assertThatThrownBy(() -> service.create(SUBMISSION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40801);
        verify(submissionFeignClient, never()).listFinalSubmissions(anyLong());
        verify(reviewFeignClient, never()).getBySubmission(anyLong());
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
    }

    @Test
    void createAllowsAnyCompletedReviewVersionNotJustFinal() {
        prepareSubmissionAndPermission();
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        when(workflow.currentPrompt()).thenReturn("prompt-v1");
        doAnswer(invocation -> {
            SuggestionTask task = invocation.getArgument(0);
            task.setId(9001L);
            return 1;
        }).when(taskMapper).insert(any(SuggestionTask.class));

        SuggestionVO result = service.create(SUBMISSION_ID, USER_ID);

        assertThat(result.getTaskId()).isEqualTo(9001L);
        verify(taskMapper).insert(any(SuggestionTask.class));
    }

    @Test
    void createRejectsReviewThatIsNotCompleted() {
        prepareSubmissionAndPermission();
        ReviewSummaryDTO review = review();
        review.setStatus("RUNNING");
        review.setResultJson(null);
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review));

        assertThatThrownBy(() -> service.create(SUBMISSION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40803);
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
    }

    @Test
    void createIsIdempotentForSameSubmissionAndWorkflow() {
        prepareValidCreationFacts();
        SuggestionTask existing = task("COMPLETED");
        when(taskMapper.selectOne(org.mockito.ArgumentMatchers.<Wrapper<SuggestionTask>>any()))
                .thenReturn(existing);

        SuggestionVO result = service.create(SUBMISSION_ID, USER_ID);

        assertThat(result.getTaskId()).isEqualTo(existing.getId());
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
    }

    @Test
    void executeClaimedCompletesTaskWithFencingAndTraceableAiResult() throws Exception {
        SuggestionTask task = task("LEASED");
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markRunning(anyLong(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(taskMapper.complete(anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        ProblemContextDTO problem = new ProblemContextDTO(PROBLEM_ID, "调度题", "题面", 60, 1);
        when(problemFeignClient.getProblemContext(PROBLEM_ID)).thenReturn(Result.ok(problem));
        when(workflow.execute(any(), any(), any(), any()))
                .thenReturn(new SuggestionWorkflowResult(
                        "{\"summary\":\"优先补验证\",\"items\":[]}", "model-a", "call-1"));

        service.executeClaimed(task.getId(), "owner-a", "token-a");

        verify(taskMapper).complete(org.mockito.ArgumentMatchers.eq(task.getId()),
                org.mockito.ArgumentMatchers.eq("token-a"),
                org.mockito.ArgumentMatchers.contains("优先补验证"),
                org.mockito.ArgumentMatchers.eq("model-a"),
                org.mockito.ArgumentMatchers.eq("call-1"), any(LocalDateTime.class));
        assertThat(task.getAiIdempotencyKey()).isEqualTo("suggestion:task:9001:attempt:1");
    }

    @Test
    void executeClaimedPersistsFailureReasonAndRetryCreatesNewAttempt() throws Exception {
        SuggestionTask task = task("LEASED");
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markRunning(anyLong(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(taskMapper.markTerminalFailure(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        when(problemFeignClient.getProblemContext(PROBLEM_ID))
                .thenReturn(Result.ok(new ProblemContextDTO(PROBLEM_ID, "调度题", "题面", 60, 1)));
        when(workflow.execute(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("AI gateway timeout"));

        service.executeClaimed(task.getId(), "owner-a", "token-a");

        verify(taskMapper).markTerminalFailure(task.getId(), "token-a", "FAILED",
                "WORKFLOW_FAILED", "AI gateway timeout");
        task.setStatus("FAILED");
        task.setRetryCount(0);
        task.setAttemptNo(1);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(USER_ID)));
        when(taskMapper.resetForRetry(anyLong(), any(LocalDateTime.class), anyString())).thenReturn(1);
        SuggestionVO retried = service.retry(task.getId(), USER_ID);
        assertThat(retried.getStatus()).isEqualTo("WAITING");
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getErrorMessage()).isNull();
    }

    @Test
    void executeClaimedDoesNothingWhenLeaseWasLost() {
        SuggestionTask task = task("LEASED");
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markRunning(anyLong(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0);

        service.executeClaimed(task.getId(), "owner-a", "stale-token");

        verify(submissionFeignClient, never()).getForReview(anyLong());
        verify(taskMapper, never()).complete(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(LocalDateTime.class));
    }

    @Test
    void aiIdempotencyKeyIsStableWithinAttempt() {
        assertThat(SuggestionService.aiIdempotencyKey(9001L, 2))
                .isEqualTo("suggestion:task:9001:attempt:2");
    }

    @Test
    void unknownAiOutcomeBecomesVisibleTerminalStateWithoutAutomaticRetry() throws Exception {
        SuggestionTask task = task("LEASED");
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markRunning(anyLong(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        when(problemFeignClient.getProblemContext(PROBLEM_ID))
                .thenReturn(Result.ok(new ProblemContextDTO(PROBLEM_ID, "调度题", "题面", 60, 1)));
        when(workflow.execute(any(), any(), any(), any()))
                .thenThrow(new AiClientException(51213, "unknown"));

        service.executeClaimed(task.getId(), "owner-a", "token-a");

        verify(taskMapper).markTerminalFailure(task.getId(), "token-a", "UNKNOWN",
                "AI_UNKNOWN", "AI 上游结果未知，禁止自动重试");
        verify(taskMapper, never()).scheduleRetry(anyLong(), anyString(), any(), anyString(),
                anyString(), anyString());
    }

    @Test
    void onlyTransientDependencyFailureCreatesAutomaticNextAttempt() {
        SuggestionTask task = task("LEASED");
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markRunning(anyLong(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID))
                .thenThrow(new IllegalStateException("submission unavailable"));

        service.executeClaimed(task.getId(), "owner-a", "token-a");

        verify(taskMapper).scheduleRetry(eq(task.getId()), eq("token-a"), any(LocalDateTime.class),
                eq("DEPENDENCY_TRANSIENT"), eq("论文建议依赖服务暂不可用"),
                eq("suggestion:task:9001:attempt:2"));
        verify(taskMapper, never()).markTerminalFailure(anyLong(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void stableSourceFailureDoesNotConsumeAutomaticRetryBudget() {
        SuggestionTask task = task("LEASED");
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markRunning(anyLong(), anyString(), anyString(), anyString(),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID))
                .thenThrow(new BusinessException(SuggestionErrorCode.SOURCE_DATA_INVALID));

        service.executeClaimed(task.getId(), "owner-a", "token-a");

        verify(taskMapper).markTerminalFailure(task.getId(), "token-a", "FAILED",
                "WORKFLOW_FAILED", "论文建议源数据不完整或互相矛盾");
        verify(taskMapper, never()).scheduleRetry(anyLong(), anyString(), any(), anyString(),
                anyString(), anyString());
    }

    @Test
    void featureDefinitionDeclaresSuggestionCapabilities() {
        var feature = service.getFeatureDefinition();
        assertThat(feature.getFeatureCode()).isEqualTo("SUGGESTION");
        assertThat(feature.getWorkflowVersions()).extracting("workflowVersion")
                .contains("IMPROVEMENT_V1", "GROUNDED_SUGGESTION_V2");
    }

    @Test
    void runExperimentExecutesTransientSuggestionWithoutDatabasePersistence() throws Exception {
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        ProblemContextDTO problem = new ProblemContextDTO(PROBLEM_ID, "调度题", "题面", 60, 1);
        when(problemFeignClient.getProblemContext(PROBLEM_ID)).thenReturn(Result.ok(problem));
        when(workflow.execute(any(), any(), any(), any()))
                .thenReturn(new SuggestionWorkflowResult(
                        "{\"summary\":\"优先补验证\",\"items\":[]}", "model-suggestion", "call-sugg-1"));

        var request = new com.leetmodel.common.api.dto.AiExperimentRequestDTO(
                "sugg-eval:1:2:1", "SUGGESTION",
                new com.leetmodel.common.api.dto.AiExperimentSampleDTO(
                        "SUBMISSION_REFERENCE", "SUGGESTION_SUBMISSION_V1", "{\"submissionId\":101}"),
                "IMPROVEMENT_V1", "MODEL_CFG_SUGGESTION_TEXT_0001", null, "P3",
                "eval-task-1", "slot-1", 1, "idem-1");

        var outcome = service.runExperiment(request);

        assertThat(outcome.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(outcome.getFeatureCode()).isEqualTo("SUGGESTION");
        assertThat(outcome.getWorkflowVersion()).isEqualTo("IMPROVEMENT_V1");
        assertThat(outcome.getAiCallId()).isEqualTo("call-sugg-1");
        assertThat(outcome.getModelName()).isEqualTo("model-suggestion");
        assertThat(outcome.getOutputJson()).contains("优先补验证");
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
        verify(taskMapper, never()).complete(anyLong(), anyString(), anyString(), anyString(),
                anyString(), any(LocalDateTime.class));
    }

    private void prepareValidCreationFacts() {
        prepareSubmissionAndPermission();
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
    }

    private void prepareSubmissionAndPermission() {
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(USER_ID)));
    }

    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(SUBMISSION_ID, TEAM_ID, PROBLEM_ID, 2, "object");
    }

    private ReviewSummaryDTO review() {
        return new ReviewSummaryDTO(
                5001L, SUBMISSION_ID, TEAM_ID, PROBLEM_ID, "COMPLETED", "BASIC_REVIEW_V1",
                BigDecimal.valueOf(88), "{\"summary\":\"评审\"}", "model-r", "call-r",
                null, LocalDateTime.now());
    }

    private SuggestionTask task(String status) {
        SuggestionTask task = new SuggestionTask();
        task.setId(9001L);
        task.setSubmissionId(SUBMISSION_ID);
        task.setTeamId(TEAM_ID);
        task.setProblemId(PROBLEM_ID);
        task.setReviewTaskId(5001L);
        task.setWorkflowVersion(SuggestionV1Workflow.VERSION);
        task.setReviewWorkflowVersion("BASIC_REVIEW_V1");
        task.setPromptSnapshot("prompt-v1");
        task.setStatus(status);
        task.setRetryCount(0);
        task.setAttemptNo(1);
        task.setMaxAttempts(3);
        task.setTraceId("trace-test");
        task.setCreateTime(LocalDateTime.now());
        return task;
    }
}
