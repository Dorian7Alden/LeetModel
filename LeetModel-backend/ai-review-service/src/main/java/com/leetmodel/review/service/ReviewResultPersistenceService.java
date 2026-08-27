package com.leetmodel.review.service;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewV1Result;
import com.leetmodel.review.mapper.ReviewTaskMapper;
import com.leetmodel.review.mapper.ReviewV1ResultMapper;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class ReviewResultPersistenceService {
    private final ReviewTaskMapper taskMapper; private final ReviewV1ResultMapper resultMapper;
    public ReviewResultPersistenceService(ReviewTaskMapper taskMapper, ReviewV1ResultMapper resultMapper) {
        this.taskMapper = taskMapper; this.resultMapper = resultMapper;
    }
    @Transactional
    public void completeV1(ReviewTask task, SubmissionReviewDTO submission, ReviewWorkflowResult workflowResult) {
        ReviewV1Result result = new ReviewV1Result();
        result.setTaskId(task.getId()); result.setSubmissionId(submission.getId());
        result.setTeamId(submission.getTeamId()); result.setProblemId(submission.getProblemId());
        result.setWorkflowVersion(task.getWorkflowVersion()); result.setScore(workflowResult.score());
        result.setResultJson(workflowResult.resultJson()); result.setModelName(workflowResult.modelName());
        result.setAiCallId(workflowResult.aiCallId()); resultMapper.insert(result);
        task.setStatus("COMPLETED"); task.setFinishedAt(LocalDateTime.now()); task.setErrorMessage(null);
        taskMapper.updateById(task);
    }
}
