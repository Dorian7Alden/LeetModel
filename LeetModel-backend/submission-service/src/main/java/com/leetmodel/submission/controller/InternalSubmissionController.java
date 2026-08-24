package com.leetmodel.submission.controller;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/submissions") @RequiredArgsConstructor
public class InternalSubmissionController {
    private final SubmissionService submissionService;
    @Operation(summary = "获取评审提交摘要")
    @GetMapping("/{submissionId}")
    public Result<SubmissionReviewDTO> get(@PathVariable Long submissionId) {
        return Result.ok(submissionService.getForReview(submissionId));
    }
}
