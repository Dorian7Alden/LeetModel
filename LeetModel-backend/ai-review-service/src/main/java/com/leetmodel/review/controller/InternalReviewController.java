package com.leetmodel.review.controller;

import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/reviews")
@RequiredArgsConstructor
public class InternalReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "创建基础评审任务")
    @PostMapping("/tasks")
    public Result<Long> create(@RequestParam Long submissionId, @RequestParam Long teamId, @RequestParam Long problemId) {
        return Result.ok(reviewService.createTask(submissionId, teamId, problemId));
    }

    @Operation(summary = "按提交查询评审摘要")
    @GetMapping("/submissions/{submissionId}")
    public Result<ReviewSummaryDTO> getBySubmission(@PathVariable Long submissionId) {
        return Result.ok(reviewService.getSummaryBySubmission(submissionId));
    }

    @Operation(summary = "查询已完成评审摘要")
    @GetMapping("/completed")
    public Result<List<ReviewSummaryDTO>> listCompleted(
            @RequestParam(required = false) Long problemId
    ) {
        return Result.ok(reviewService.listCompletedSummaries(problemId));
    }

    @Operation(summary = "获取评审任务数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(reviewService.count());
    }
}
