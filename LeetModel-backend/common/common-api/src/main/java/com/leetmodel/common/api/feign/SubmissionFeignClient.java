package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "submission-service")
public interface SubmissionFeignClient {
    @GetMapping("/internal/submissions/{submissionId}")
    Result<SubmissionReviewDTO> getForReview(@PathVariable Long submissionId);
}
