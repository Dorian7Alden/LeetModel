package com.leetmodel.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    void createRejectsNonFinalSubmission() {
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(USER_ID)));
        when(submissionFeignClient.listFinalSubmissions(PROBLEM_ID)).thenReturn(Result.ok(List.of()));

        assertThatThrownBy(() -> service.create(SUBMISSION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40802);
        verify(reviewFeignClient, never()).getBySubmission(anyLong());
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
    void processNextCompletesClaimedTaskWithTraceableAiResult() throws Exception {
        SuggestionTask task = task("WAITING");
        when(taskMapper.selectNextWaiting(any(LocalDateTime.class))).thenReturn(task);
        when(taskMapper.claim(anyLong(), any(LocalDateTime.class))).thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        ProblemContextDTO problem = new ProblemContextDTO(PROBLEM_ID, "调度题", "题面", 60, 1);
        when(problemFeignClient.getProblemContext(PROBLEM_ID)).thenReturn(Result.ok(problem));
        when(workflow.execute(any(), any(), any(), any()))
                .thenReturn(new SuggestionWorkflowResult(
                        "{\"summary\":\"优先补验证\",\"items\":[]}", "model-a", "call-1"));

        service.processNext();

        ArgumentCaptor<SuggestionTask> captor = ArgumentCaptor.forClass(SuggestionTask.class);
        verify(taskMapper).updateById(captor.capture());
        SuggestionTask completed = captor.getValue();
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getModelName()).isEqualTo("model-a");
        assertThat(completed.getAiCallId()).isEqualTo("call-1");
        assertThat(completed.getFinishedAt()).isNotNull();
    }

    @Test
    void processNextPersistsFailureReasonAndRetryResetsTask() throws Exception {
        SuggestionTask task = task("WAITING");
        when(taskMapper.selectNextWaiting(any(LocalDateTime.class))).thenReturn(task);
        when(taskMapper.claim(anyLong(), any(LocalDateTime.class))).thenReturn(1);
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
        when(problemFeignClient.getProblemContext(PROBLEM_ID))
                .thenReturn(Result.ok(new ProblemContextDTO(PROBLEM_ID, "调度题", "题面", 60, 1)));
        when(workflow.execute(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("AI gateway timeout"));

        service.processNext();

        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorMessage()).isEqualTo("AI gateway timeout");
        task.setRetryCount(0);
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(USER_ID)));
        when(taskMapper.resetForRetry(anyLong(), any(LocalDateTime.class))).thenReturn(1);
        SuggestionVO retried = service.retry(task.getId(), USER_ID);
        assertThat(retried.getStatus()).isEqualTo("WAITING");
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getErrorMessage()).isNull();
    }

    @Test
    void processNextDoesNothingWhenAnotherWorkerWinsClaim() {
        SuggestionTask task = task("WAITING");
        when(taskMapper.selectNextWaiting(any(LocalDateTime.class))).thenReturn(task);
        when(taskMapper.claim(anyLong(), any(LocalDateTime.class))).thenReturn(0);

        service.processNext();

        verify(submissionFeignClient, never()).getForReview(anyLong());
        verify(taskMapper, never()).updateById(any(SuggestionTask.class));
    }

    @Test
    void recoversStaleRunningTasksWithBoundedCutoff() {
        service.recoverStaleTasks();

        verify(taskMapper).recoverStale(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    private void prepareValidCreationFacts() {
        prepareSubmissionAndPermission();
        when(reviewFeignClient.getBySubmission(SUBMISSION_ID)).thenReturn(Result.ok(review()));
    }

    private void prepareSubmissionAndPermission() {
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(submission()));
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(USER_ID)));
        SubmissionSnapshotDTO finalSubmission = new SubmissionSnapshotDTO(
                SUBMISSION_ID, TEAM_ID, PROBLEM_ID, USER_ID, 2, "paper.pdf", "object",
                "SUCCESS", true, LocalDateTime.now());
        when(submissionFeignClient.listFinalSubmissions(PROBLEM_ID))
                .thenReturn(Result.ok(List.of(finalSubmission)));
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
        task.setCreateTime(LocalDateTime.now());
        return task;
    }
}
