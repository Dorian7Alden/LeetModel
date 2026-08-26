package com.leetmodel.review.workflow;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.review.entity.ReviewTask;

public interface ReviewWorkflow {
    String versionCode();
    Long versionId();
    String currentPrompt();
    ReviewWorkflowResult execute(ReviewTask task, SubmissionReviewDTO submission) throws Exception;
}
