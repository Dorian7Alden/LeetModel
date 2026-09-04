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

    /**
     * 创建新的论文 AI 改进建议分析任务。
     *
     * @param request 包含队伍 ID、提交 ID 与提示词版本的请求对象，不能为 null
     * @return 建议任务视图对象
     */
    @Operation(summary = "创建论文建议任务")
    @PostMapping
    public Result<SuggestionVO> create(@Valid @RequestBody SuggestionCreateRequest request) {
        return Result.ok(suggestionService.create(request, UserContext.getUserId()));
    }

    /**
     * 查询指定建议任务的执行状态、进度与生成建议报告。
     *
     * @param taskId 目标任务 ID，不能为 null
     * @return 建议任务视图对象
     */
    @Operation(summary = "查询论文建议任务")
    @GetMapping("/{taskId}")
    public Result<SuggestionVO> get(
            @PathVariable @Positive(message = "任务标识必须为正整数") Long taskId) {
        return Result.ok(suggestionService.get(taskId, UserContext.getUserId()));
    }

    /**
     * 查询指定提交记录关联的所有 AI 建议生成历史。
     *
     * @param submissionId 目标提交记录 ID，不能为 null
     * @return 建议任务视图对象列表
     */
    @Operation(summary = "按提交查询论文建议历史")
    @GetMapping("/submissions/{submissionId}")
    public Result<List<SuggestionVO>> getBySubmission(
            @PathVariable @Positive(message = "提交标识必须为正整数") Long submissionId) {
        return Result.ok(suggestionService.listBySubmission(submissionId, UserContext.getUserId()));
    }

    /**
     * 查询队伍名下全部论文建议任务记录列表。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @return 建议任务视图对象列表
     */
    @Operation(summary = "查询队伍论文建议历史")
    @GetMapping("/teams/{teamId}")
    public Result<List<SuggestionVO>> listTeam(
            @PathVariable @Positive(message = "队伍标识必须为正整数") Long teamId) {
        return Result.ok(suggestionService.listTeam(teamId, UserContext.getUserId()));
    }

    /**
     * 触发对失败建议任务的手动重试。
     *
     * @param taskId 目标任务 ID，不能为 null
     * @return 重试后的建议任务视图对象
     */
    @Operation(summary = "重试失败的论文建议任务")
    @PostMapping("/{taskId}/retry")
    public Result<SuggestionVO> retry(
            @PathVariable @Positive(message = "任务标识必须为正整数") Long taskId) {
        return Result.ok(suggestionService.retry(taskId, UserContext.getUserId()));
    }
}
