package com.leetmodel.submission.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.submission.service.SubmissionService;
import com.leetmodel.submission.vo.SubmissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Tag(name = "论文提交")
public class SubmissionController {
    private final SubmissionService submissionService;

    @Operation(summary = "提交 PDF 论文")
    @PostMapping
    public Result<SubmissionVO> submit(@RequestParam Long teamId, @RequestParam MultipartFile file) {
        return Result.ok(submissionService.submit(teamId, file, UserContext.getUserId()));
    }

    @Operation(summary = "查询队伍提交历史")
    @GetMapping("/teams/{teamId}")
    public Result<List<SubmissionVO>> history(@PathVariable Long teamId) {
        return Result.ok(submissionService.history(teamId, UserContext.getUserId()));
    }

    @Operation(summary = "锁定最终提交版本")
    @PostMapping("/teams/{teamId}/finalize")
    public Result<SubmissionVO> finalizeSubmission(@PathVariable Long teamId) {
        return Result.ok(submissionService.lockFinal(teamId, UserContext.getUserId()));
    }
}
