package com.leetmodel.suggestion.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.dto.SuggestionCreateRequest;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceProjector;
import com.leetmodel.suggestion.vo.SuggestionVO;
import com.leetmodel.suggestion.workflow.SuggestionV1Workflow;
import com.leetmodel.suggestion.workflow.v2.GroundedSuggestionV2Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionV2CreationTest {
    private static final Long SUBMISSION_ID = 101L;
    private static final Long TEAM_ID = 11L;
    private static final Long PROBLEM_ID = 51L;
    private static final Long REVIEW_TASK_ID = 5001L;
    private static final Long USER_ID = 7L;

    @Mock private SuggestionTaskMapper taskMapper;
    @Mock private SubmissionFeignClient submissionFeignClient;
    @Mock private ReviewFeignClient reviewFeignClient;
    @Mock private ProblemFeignClient problemFeignClient;
    @Mock private TeamFeignClient teamFeignClient;
    @Mock private KnowledgeRetrievalFeignClient knowledgeRetrievalFeignClient;
    @Mock private SuggestionV1Workflow v1Workflow;
    @Mock private GroundedSuggestionV2Workflow v2Workflow;
    @Mock private ReviewEvidenceProjector evidenceProjector;

    private SuggestionService service;

    @BeforeEach
    void setUp() {
        service = new SuggestionService(taskMapper, submissionFeignClient, reviewFeignClient,
                problemFeignClient, teamFeignClient, knowledgeRetrievalFeignClient, v1Workflow,
                v2Workflow, evidenceProjector, new ObjectMapper());
    }

    @Test
    void differentManualActionsCreateIndependentReportsForTheSameReviewedSubmission() {
        prepareFacts();
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(v2Workflow.currentPrompt()).thenReturn("prompt-v2");
        AtomicLong ids = new AtomicLong(9000L);
        doAnswer(invocation -> {
            SuggestionTask task = invocation.getArgument(0);
            task.setId(ids.incrementAndGet());
            return 1;
        }).when(taskMapper).insert(any(SuggestionTask.class));

        SuggestionVO first = service.create(request("manual_action_0001"), USER_ID);
        SuggestionVO second = service.create(request("manual_action_0002"), USER_ID);

        ArgumentCaptor<SuggestionTask> captor = ArgumentCaptor.forClass(SuggestionTask.class);
        verify(taskMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(first.getTaskId()).isNotEqualTo(second.getTaskId());
        assertThat(captor.getAllValues())
                .extracting(SuggestionTask::getSubmissionId)
                .containsOnly(SUBMISSION_ID);
        assertThat(captor.getAllValues())
                .extracting(SuggestionTask::getEligibilityReviewTaskId)
                .containsOnly(REVIEW_TASK_ID);
        assertThat(captor.getAllValues())
                .extracting(SuggestionTask::getClientRequestId)
                .containsExactly("manual_action_0001", "manual_action_0002");
        assertThat(captor.getAllValues())
                .extracting(SuggestionTask::getRequestedByUserId)
                .containsOnly(USER_ID);
        assertThat(captor.getAllValues())
                .extracting(SuggestionTask::getWorkflowVersion)
                .containsOnly(GroundedSuggestionV2Workflow.VERSION);
        assertThat(captor.getAllValues())
                .extracting(SuggestionTask::getRetrievalWorkflowVersion)
                .containsOnly("VECTOR_RAG_V1");
    }

    @Test
    void theSameClientRequestReturnsTheExistingReport() {
        prepareFacts();
        SuggestionTask existing = new SuggestionTask();
        existing.setId(9001L);
        existing.setSubmissionId(SUBMISSION_ID);
        existing.setTeamId(TEAM_ID);
        existing.setProblemId(PROBLEM_ID);
        existing.setClientRequestId("manual_action_0001");
        existing.setRequestedByUserId(USER_ID);
        existing.setReviewTaskId(REVIEW_TASK_ID);
        existing.setEligibilityReviewTaskId(REVIEW_TASK_ID);
        existing.setEvidenceReviewTaskId(REVIEW_TASK_ID);
        existing.setWorkflowVersion(GroundedSuggestionV2Workflow.VERSION);
        existing.setReviewWorkflowVersion("EVIDENCE_REVIEW_V2");
        existing.setResultSchemaVersion(GroundedSuggestionV2Workflow.RESULT_SCHEMA_VERSION);
        existing.setStatus("WAITING");
        existing.setRetryCount(0);
        existing.setAttemptNo(1);
        existing.setCreateTime(LocalDateTime.now());
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        SuggestionVO result = service.create(request("manual_action_0001"), USER_ID);

        assertThat(result.getTaskId()).isEqualTo(9001L);
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
    }

    @Test
    void reusingAClientRequestForDifferentPayloadIsRejected() {
        prepareFacts();
        SuggestionTask existing = new SuggestionTask();
        existing.setId(9001L);
        existing.setSubmissionId(999L);
        existing.setEligibilityReviewTaskId(REVIEW_TASK_ID);
        existing.setWorkflowVersion(GroundedSuggestionV2Workflow.VERSION);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> service.create(request("manual_action_0001"), USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已用于不同");
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
    }

    @Test
    void experimentalDirectoryRetrievalCannotBeSelectedByTheFormalV2Endpoint() {
        prepareFacts();
        SuggestionCreateRequest request = request("manual_action_0001");
        request.setRetrievalWorkflowVersion("AI_DIRECTORY_V1");

        assertThatThrownBy(() -> service.create(request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许 VECTOR_RAG_V1");
        verify(taskMapper, never()).insert(any(SuggestionTask.class));
    }

    private void prepareFacts() {
        when(submissionFeignClient.getForReview(SUBMISSION_ID)).thenReturn(Result.ok(
                new SubmissionReviewDTO(SUBMISSION_ID, TEAM_ID, PROBLEM_ID, 2, "object")));
        when(teamFeignClient.getMemberIds(TEAM_ID)).thenReturn(Result.ok(List.of(USER_ID)));
        when(reviewFeignClient.getByTask(REVIEW_TASK_ID)).thenReturn(Result.ok(
                new ReviewSummaryDTO(REVIEW_TASK_ID, SUBMISSION_ID, TEAM_ID, PROBLEM_ID,
                        "COMPLETED", "EVIDENCE_REVIEW_V2", BigDecimal.valueOf(88),
                        "{\"overallAssessment\":\"评审\"}", "model-r", "call-r", null,
                        LocalDateTime.now())));
    }

    private SuggestionCreateRequest request(String clientRequestId) {
        SuggestionCreateRequest request = new SuggestionCreateRequest();
        request.setSubmissionId(SUBMISSION_ID);
        request.setReviewTaskId(REVIEW_TASK_ID);
        request.setClientRequestId(clientRequestId);
        return request;
    }
}
