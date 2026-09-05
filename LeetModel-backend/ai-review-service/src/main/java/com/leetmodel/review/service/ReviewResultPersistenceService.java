package com.leetmodel.review.service;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.ReviewCompletedPayload;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewV1Result;
import com.leetmodel.review.entity.ReviewV2Result;
import com.leetmodel.review.entity.ReviewV3Result;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewV2ResultMapper;
import com.leetmodel.review.mapper.ReviewV3ResultMapper;
import com.leetmodel.review.messaging.ReviewCompletedMessageContract;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import com.leetmodel.review.workflow.v2.EvidenceReviewV2Workflow;
import com.leetmodel.review.workflow.v3.DeepEvidenceReviewV3Workflow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Service
public class ReviewResultPersistenceService {
    private final ReviewTaskMapper taskMapper;
    private final ReviewV1ResultMapper resultMapper;
    private final ReviewV2ResultMapper v2ResultMapper;
    private final ReviewV3ResultMapper v3ResultMapper;
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageOutbox messageOutbox;
    @Autowired
    public ReviewResultPersistenceService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper,
                                          ReviewV2ResultMapper v2ResultMapper,
                                          @Autowired(required = false) ReviewV3ResultMapper v3ResultMapper,
                                          MessageEnvelopeFactory envelopeFactory,
                                          MessageOutbox messageOutbox) {
        this.taskMapper = taskMapper; this.resultMapper = resultMapper; this.v2ResultMapper = v2ResultMapper;
        this.v3ResultMapper = v3ResultMapper;
        this.envelopeFactory = envelopeFactory; this.messageOutbox = messageOutbox;
    }
    public ReviewResultPersistenceService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper,
                                          ReviewV2ResultMapper v2ResultMapper,
                                          MessageEnvelopeFactory envelopeFactory,
                                          MessageOutbox messageOutbox) {
        this(taskMapper, resultMapper, v2ResultMapper, null, envelopeFactory, messageOutbox);
    }
    /**
     * 在同一事务中写版本化结果，并用 fencing token 推进任务终态。
     *
     * @param task 评审任务
     * @param submission 提交快照
     * @param workflowResult 工作流结果
     * @param leaseToken 当前执行租约 token
     */
    @Transactional
    public void complete(ReviewTask task, SubmissionReviewDTO submission,
                         ReviewWorkflowResult workflowResult, String leaseToken) {
        if (DeepEvidenceReviewV3Workflow.VERSION_CODE.equals(task.getWorkflowVersion())) {
            completeV3(task, submission, workflowResult);
        } else if (EvidenceReviewV2Workflow.VERSION_CODE.equals(task.getWorkflowVersion())) {
            completeV2(task, submission, workflowResult);
        } else {
            completeV1(task, submission, workflowResult);
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        if (taskMapper.markCompleted(task.getId(), leaseToken, finishedAt) == 0) {
            throw new IllegalStateException("评审任务租约已丢失，拒绝提交结果");
        }
        ReviewCompletedPayload payload = new ReviewCompletedPayload(
                task.getId(), submission.getId(), submission.getTeamId(), submission.getProblemId(),
                task.getWorkflowVersion(), finishedAt);
        messageOutbox.enqueue(
                ReviewCompletedMessageContract.TOPIC,
                ReviewCompletedMessageContract.EVENT_TYPE,
                envelopeFactory.create(
                        ReviewCompletedMessageContract.EVENT_TYPE,
                        "review-task",
                        task.getId().toString(),
                        ReviewCompletedMessageContract.idempotencyKey(task.getId()),
                        task.getTraceId(),
                        payload));
        task.setStatus("COMPLETED"); task.setFinishedAt(finishedAt); task.setErrorMessage(null);
    }

    private void completeV1(ReviewTask task, SubmissionReviewDTO submission, ReviewWorkflowResult workflowResult) {
        ReviewV1Result result = new ReviewV1Result();
        result.setTaskId(task.getId()); result.setSubmissionId(submission.getId());
        result.setTeamId(submission.getTeamId()); result.setProblemId(submission.getProblemId());
        result.setWorkflowVersion(task.getWorkflowVersion()); result.setScore(workflowResult.score());
        result.setResultJson(workflowResult.resultJson()); result.setModelName(workflowResult.modelName());
        result.setAiCallId(workflowResult.aiCallId()); resultMapper.insert(result);
    }

    private void completeV2(ReviewTask task, SubmissionReviewDTO submission, ReviewWorkflowResult workflowResult) {
        if (workflowResult.parseArtifactId() == null) {
            throw new IllegalArgumentException("V2 评审结果缺少解析产物引用");
        }
        ReviewV2Result result = new ReviewV2Result();
        result.setTaskId(task.getId()); result.setSubmissionId(submission.getId());
        result.setTeamId(submission.getTeamId()); result.setProblemId(submission.getProblemId());
        result.setParseArtifactId(workflowResult.parseArtifactId());
        result.setWorkflowVersion(task.getWorkflowVersion());
        result.setResultSchemaVersion(EvidenceReviewV2Workflow.RESULT_SCHEMA_VERSION);
        result.setScoringRuleVersion(EvidenceReviewV2Workflow.SCORING_RULE_VERSION);
        result.setScore(workflowResult.score()); result.setResultJson(workflowResult.resultJson());
        result.setModelName(workflowResult.modelName()); result.setAiCallId(workflowResult.aiCallId());
        v2ResultMapper.insert(result);
    }

    private void completeV3(ReviewTask task, SubmissionReviewDTO submission, ReviewWorkflowResult workflowResult) {
        if (workflowResult.parseArtifactId() == null) {
            throw new IllegalArgumentException("V3 评审结果缺少解析产物引用");
        }
        if (v3ResultMapper == null) {
            throw new IllegalStateException("v3ResultMapper 未配置");
        }
        ReviewV3Result result = new ReviewV3Result();
        result.setTaskId(task.getId()); result.setSubmissionId(submission.getId());
        result.setTeamId(submission.getTeamId()); result.setProblemId(submission.getProblemId());
        result.setParseArtifactId(workflowResult.parseArtifactId());
        result.setWorkflowVersion(task.getWorkflowVersion());
        result.setResultSchemaVersion(DeepEvidenceReviewV3Workflow.RESULT_SCHEMA_VERSION);
        result.setScoringRuleVersion(DeepEvidenceReviewV3Workflow.SCORING_RULE_VERSION);
        result.setScore(workflowResult.score());
        result.setPhase1Score(BigDecimal.ZERO);
        result.setPhase2Score(workflowResult.score());
        result.setResultJson(workflowResult.resultJson());
        result.setModelName(workflowResult.modelName());
        result.setAiCallId(workflowResult.aiCallId());
        v3ResultMapper.insert(result);
    }
}
