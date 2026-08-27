package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "submission-service")
public interface SubmissionFeignClient {
    @GetMapping("/internal/submissions/{submissionId}")
    Result<SubmissionReviewDTO> getForReview(@PathVariable Long submissionId);

    @GetMapping("/internal/submissions/final")
    Result<List<SubmissionSnapshotDTO>> listFinalSubmissions(
            @RequestParam(required = false) Long problemId
    );

    @GetMapping("/internal/submissions/count")
    Result<Long> getSubmissionCount();

    @GetMapping("/internal/submissions/recent")
    Result<List<SubmissionSnapshotDTO>> listRecent(@RequestParam("limit") Integer limit);
}
