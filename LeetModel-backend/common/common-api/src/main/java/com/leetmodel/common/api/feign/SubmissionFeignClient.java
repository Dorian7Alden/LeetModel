package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.api.dto.AdminSubmissionPageQuery;
import com.leetmodel.common.api.dto.AdminSubmissionStatsDTO;
import com.leetmodel.common.api.vo.SubmissionAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/internal/submissions/stats/problems")
    Result<List<ProblemSubmissionStatsDTO>> getProblemSubmissionStats();

    @GetMapping("/internal/submissions/recent")
    Result<List<SubmissionSnapshotDTO>> listRecent(@RequestParam("limit") Integer limit);

    @GetMapping("/internal/submissions/{submissionId}/preview")
    Result<SubmissionPreviewDTO> getPreview(@PathVariable Long submissionId);

    @GetMapping("/internal/submissions/{submissionId}/snapshot")
    Result<SubmissionSnapshotDTO> getSubmissionSnapshot(@PathVariable Long submissionId);

    @GetMapping("/internal/submissions/teams/{teamId}/latest")
    Result<SubmissionSnapshotDTO> getLatestTeamSubmission(@PathVariable Long teamId);

    @GetMapping("/internal/submissions/admin/page")
    Result<PageResult<SubmissionAdminVO>> pageAdminSubmissions(@SpringQueryMap AdminSubmissionPageQuery query);

    @GetMapping("/internal/submissions/admin/stats")
    Result<AdminSubmissionStatsDTO> getAdminSubmissionStats();

    @GetMapping("/internal/submissions/{submissionId}/admin/detail")
    Result<SubmissionAdminVO> getAdminSubmissionDetail(@PathVariable("submissionId") Long submissionId);

    @PutMapping("/internal/submissions/{submissionId}/admin/set-final")
    Result<SubmissionAdminVO> setFinalVersion(@PathVariable("submissionId") Long submissionId);

    @DeleteMapping("/internal/submissions/{submissionId}/admin")
    Result<Void> adminInvalidateSubmission(@PathVariable("submissionId") Long submissionId);

    @PostMapping("/internal/submissions/{submissionId}/admin/re-dispatch")
    Result<Void> adminRedispatchReview(@PathVariable("submissionId") Long submissionId);
}
