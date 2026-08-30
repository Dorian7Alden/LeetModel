package com.leetmodel.submission.controller;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/internal/submissions")
@RequiredArgsConstructor
public class InternalSubmissionController {

    private final SubmissionService submissionService;

    @Operation(summary = "获取评审提交摘要")
    @GetMapping("/{submissionId}")
    public Result<SubmissionReviewDTO> get(@PathVariable Long submissionId) {
        return Result.ok(submissionService.getForReview(submissionId));
    }

    @Operation(summary = "查询最终提交快照")
    @GetMapping("/final")
    public Result<List<SubmissionSnapshotDTO>> listFinal(
            @RequestParam(required = false) Long problemId
    ) {
        return Result.ok(submissionService.listFinalSnapshots(problemId));
    }

    @Operation(summary = "获取提交数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(submissionService.count());
    }

    @Operation(summary = "按题目统计成功提交量")
    @GetMapping("/stats/problems")
    public Result<List<ProblemSubmissionStatsDTO>> problemStats() {
        return Result.ok(submissionService.listProblemStats());
    }

    @Operation(summary = "查询最近提交快照")
    @GetMapping("/recent")
    public Result<List<SubmissionSnapshotDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(submissionService.listRecentSnapshots(limit));
    }

    @Operation(summary = "获取提交 PDF 临时预览信息")
    @GetMapping("/{submissionId}/preview")
    public Result<SubmissionPreviewDTO> preview(@PathVariable Long submissionId) {
        return Result.ok(submissionService.getPreview(submissionId));
    }
}
