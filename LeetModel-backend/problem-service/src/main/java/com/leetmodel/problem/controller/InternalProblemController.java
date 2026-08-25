package com.leetmodel.problem.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
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

    @Operation(summary = "获取题目数量")
    @GetMapping("/count")
    public Result<Long> getProblemCount() {
        long count = problemService.count();
        return Result.ok(count);
    }

    @Operation(summary = "获取练习题目摘要")
    @GetMapping("/{problemId}/practice")
    public Result<ProblemPracticeDTO> getPracticeProblem(@PathVariable Long problemId) {
        ProblemVO problem = problemService.getPublishedProblemDetail(problemId);
        return Result.ok(new ProblemPracticeDTO(problem.getId(), problem.getTitle(),
                problem.getDurationMinutes(), problem.getStatus()));
    }

    @Operation(summary = "批量获取练习题目摘要")
    @GetMapping("/practice-summaries")
    public Result<List<ProblemPracticeDTO>> getPracticeProblems(
            @RequestParam(required = false) List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) return Result.ok(List.of());
        List<Problem> problems = problemService.list(new LambdaQueryWrapper<Problem>()
                .in(Problem::getId, problemIds.stream().distinct().toList())
                .eq(Problem::getStatus, 1));
        List<ProblemPracticeDTO> summaries = problems.stream()
                .map(problem -> new ProblemPracticeDTO(problem.getId(), problem.getTitle(),
                        problem.getDurationMinutes(), problem.getStatus()))
                .toList();
        return Result.ok(summaries);
    }
}
