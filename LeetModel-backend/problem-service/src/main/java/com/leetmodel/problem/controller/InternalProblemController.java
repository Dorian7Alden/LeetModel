package com.leetmodel.problem.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.AssistantProblemQueryDTO;
import com.leetmodel.common.api.dto.AssistantProblemResultDTO;
import com.leetmodel.problem.vo.ProblemVO;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.service.ProblemService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 题目服务内部 Feign 接口实现。
 */
@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/problems")
@RequiredArgsConstructor
public class InternalProblemController {

    private final ProblemService problemService;

    /**
     * 统计系统当前题目总数（供 admin-service 概览使用）。
     *
     * @return 题目总数
     */
    @Operation(summary = "获取题目数量")
    @GetMapping("/count")
    public Result<Long> getProblemCount() {
        long count = problemService.count();
        return Result.ok(count);
    }

    /**
     * 获取指定题目的实训练习摘要信息（供 team-service 校验题目）。
     *
     * @param problemId 目标题目 ID，不能为 null
     * @return 题目练习摘要 DTO
     */
    @Operation(summary = "获取练习题目摘要")
    @GetMapping("/{problemId}/practice")
    public Result<ProblemPracticeDTO> getPracticeProblem(@PathVariable Long problemId) {
        ProblemVO problem = problemService.getPublishedProblemDetail(problemId);
        return Result.ok(new ProblemPracticeDTO(problem.getId(), problem.getCode(), problem.getTitle(),
                problem.getDurationMinutes(), problem.getStatus()));
    }

    /**
     * 获取题目的全量文本题面上下文（供 AI 评审、AI 建议跨服务消费）。
     *
     * @param problemId 目标题目 ID，不能为 null
     * @return 包含题面 Markdown 的题目上下文 DTO
     */
    @Operation(summary = "获取 AI 业务题目上下文")
    @GetMapping("/{problemId}/context")
    public Result<ProblemContextDTO> getProblemContext(@PathVariable Long problemId) {
        ProblemVO problem = problemService.getPublishedProblemDetail(problemId);
        return Result.ok(new ProblemContextDTO(
                problem.getId(), problem.getTitle(), problem.getContentMarkdown(),
                problem.getDurationMinutes(), problem.getStatus()));
    }

    /**
     * 批量查询多个题目的练习摘要信息。
     *
     * @param problemIds 目标题目 ID 列表，可为空
     * @return 题目练习摘要 DTO 列表
     */
    @Operation(summary = "批量获取练习题目摘要")
    @GetMapping("/practice-summaries")
    public Result<List<ProblemPracticeDTO>> getPracticeProblems(
            @RequestParam(required = false) List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) return Result.ok(List.of());
        List<Problem> problems = problemService.list(new LambdaQueryWrapper<Problem>()
                .in(Problem::getId, problemIds.stream().distinct().toList())
                .eq(Problem::getStatus, 1));
        List<ProblemPracticeDTO> summaries = problems.stream()
                .map(problem -> new ProblemPracticeDTO(problem.getId(), problem.getCode(), problem.getTitle(),
                        problem.getDurationMinutes(), problem.getStatus()))
                .toList();
        return Result.ok(summaries);
    }

    /**
     * 供管理端下拉选择已发布的题目简要选项。
     *
     * @param keyword 可选的标题关键字检索
     * @param limit   返回数量上限，默认 20，最大 50
     * @return 题目选项 DTO 列表
     */
    @Operation(summary = "查询已发布题目选项")
    @GetMapping("/options")
    public Result<List<ProblemOptionDTO>> getPublishedOptions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<Problem> query = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getStatus, 1)
                .like(keyword != null && !keyword.isBlank(), Problem::getTitle,
                        keyword == null ? null : keyword.trim())
                .orderByDesc(Problem::getYear)
                .orderByDesc(Problem::getId)
                .last("LIMIT " + safeLimit);
        List<ProblemOptionDTO> options = problemService.list(query).stream()
                .map(problem -> new ProblemOptionDTO(
                        problem.getId(),
                        problem.getCode(),
                        problem.getTitle(),
                        problem.getContestId(),
                        problem.getYear(),
                        problem.getStatementLanguage(),
                        problem.getDifficulty(),
                        problem.getDurationMinutes()
                ))
                .toList();
        return Result.ok(options);
    }

    /**
     * 供 AI 客服助手根据标题、年份或关键词执行题目语义事实匹配查询。
     *
     * @param request 包含客服检索条件的请求对象，不能为 null
     * @return AI 客服所需的题目事实结果 DTO
     */
    @Operation(summary = "查询 AI 客服题目事实")
    @PostMapping("/assistant-query")
    public Result<AssistantProblemResultDTO> queryForAssistant(
            @Valid @RequestBody AssistantProblemQueryDTO request) {
        return Result.ok(problemService.queryForAssistant(request));
    }

    /**
     * 将内部题目选项数量限制在安全范围内。
     * @param limit 请求数量
     * @return 1 到 50 的安全数量
     */
    static int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? 20 : limit, 50));
    }
}
