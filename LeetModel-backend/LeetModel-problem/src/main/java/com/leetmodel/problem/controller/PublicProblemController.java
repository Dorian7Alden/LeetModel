package com.leetmodel.problem.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目公开接口（无需认证，仅返回已发布题目）。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/public/problems")
@RequiredArgsConstructor
@Tag(name = "题目浏览")
public class PublicProblemController {

    private final ProblemService problemService;

    @GetMapping
    @Operation(summary = "分页浏览已发布题目")
    public Result<PageResult<ProblemVO>> page(@Valid ProblemPageQuery query) {
        // 公开接口强制只查询已发布题目
        query.setStatus(1);
        return Result.ok(PageResult.from(problemService.pageProblems(query)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "浏览题目详情")
    public Result<ProblemVO> detail(@PathVariable Long id) {
        ProblemVO vo = problemService.getProblemDetail(id);
        return Result.ok(vo);
    }
}
