package com.leetmodel.submission.controller;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
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
}
