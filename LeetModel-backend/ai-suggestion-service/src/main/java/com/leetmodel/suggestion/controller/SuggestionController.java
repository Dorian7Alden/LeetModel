package com.leetmodel.suggestion.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.suggestion.dto.SuggestionCreateRequest;
import com.leetmodel.suggestion.service.SuggestionService;
import com.leetmodel.suggestion.vo.SuggestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
@Tag(name = "论文改善建议")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @Operation(summary = "创建论文建议任务")
    @PostMapping
    public Result<SuggestionVO> create(@Valid @RequestBody SuggestionCreateRequest request) {
        return Result.ok(suggestionService.create(request, UserContext.getUserId()));
    }

    @Operation(summary = "查询论文建议任务")
    @GetMapping("/{taskId}")
    public Result<SuggestionVO> get(
            @PathVariable @Positive(message = "任务标识必须为正整数") Long taskId) {
        return Result.ok(suggestionService.get(taskId, UserContext.getUserId()));
    }

    @Operation(summary = "按提交查询论文建议历史")
    @GetMapping("/submissions/{submissionId}")
    public Result<List<SuggestionVO>> getBySubmission(
            @PathVariable @Positive(message = "提交标识必须为正整数") Long submissionId) {
        return Result.ok(suggestionService.listBySubmission(submissionId, UserContext.getUserId()));
    }

    @Operation(summary = "查询队伍论文建议历史")
    @GetMapping("/teams/{teamId}")
    public Result<List<SuggestionVO>> listTeam(
            @PathVariable @Positive(message = "队伍标识必须为正整数") Long teamId) {
        return Result.ok(suggestionService.listTeam(teamId, UserContext.getUserId()));
    }

    @Operation(summary = "重试失败的论文建议任务")
    @PostMapping("/{taskId}/retry")
    public Result<SuggestionVO> retry(
            @PathVariable @Positive(message = "任务标识必须为正整数") Long taskId) {
        return Result.ok(suggestionService.retry(taskId, UserContext.getUserId()));
    }
}
