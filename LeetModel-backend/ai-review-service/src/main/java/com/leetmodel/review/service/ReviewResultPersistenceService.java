package com.leetmodel.review.service;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewV1Result;
import com.leetmodel.review.entity.ReviewV2Result;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.mapper.ReviewV2ResultMapper;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import com.leetmodel.review.workflow.v2.EvidenceReviewV2Workflow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class ReviewResultPersistenceService {
    private final ReviewTaskMapper taskMapper;
    private final ReviewV1ResultMapper resultMapper;
    private final ReviewV2ResultMapper v2ResultMapper;
    public ReviewResultPersistenceService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper,
                                          ReviewV2ResultMapper v2ResultMapper) {
        this.taskMapper = taskMapper; this.resultMapper = resultMapper; this.v2ResultMapper = v2ResultMapper;
    }
    @Transactional
    public void complete(ReviewTask task, SubmissionReviewDTO submission, ReviewWorkflowResult workflowResult) {
        if (EvidenceReviewV2Workflow.VERSION_CODE.equals(task.getWorkflowVersion())) {
            completeV2(task, submission, workflowResult);
        } else {
            completeV1(task, submission, workflowResult);
        }
        task.setStatus("COMPLETED"); task.setFinishedAt(LocalDateTime.now()); task.setErrorMessage(null);
        taskMapper.updateById(task);
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
}
