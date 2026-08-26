package com.leetmodel.review.controller;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/reviews") @RequiredArgsConstructor
public class InternalReviewController {
    private final ReviewService reviewService;
    @Operation(summary="创建基础评审任务") @PostMapping("/tasks")
    public Result<Long> create(@RequestParam Long submissionId, @RequestParam Long teamId, @RequestParam Long problemId) {
        return Result.ok(reviewService.createTask(submissionId, teamId, problemId));
    }
}
