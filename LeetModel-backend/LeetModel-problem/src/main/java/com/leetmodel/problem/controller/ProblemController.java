package com.leetmodel.problem.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目管理接口（需要 PROBLEM_VIEW / PROBLEM_MANAGE 权限）。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
@Tag(name = "题目管理")
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    @Operation(summary = "分页查询题目")
    public Result<PageResult<ProblemVO>> page(@Valid ProblemPageQuery query) {
        IPage<ProblemVO> page = problemService.pageProblems(query);
        return Result.ok(PageResult.from(page));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询题目详情")
    public Result<ProblemVO> detail(@PathVariable Long id) {
        ProblemVO vo = problemService.getProblemDetail(id);
        return Result.ok(vo);
    }

    @PostMapping
    @Operation(summary = "创建题目")
    public Result<ProblemVO> create(@Valid @RequestBody ProblemCreateRequest request) {
        // TODO: 从当前登录用户获取 creatorId
        Long creatorId = 1L;
        ProblemVO vo = problemService.createProblem(request, creatorId);
        return Result.ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新题目")
    public Result<ProblemVO> update(@PathVariable Long id,
                                     @Valid @RequestBody ProblemUpdateRequest request) {
        ProblemVO vo = problemService.updateProblem(id, request);
        return Result.ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除题目（逻辑删除）")
    public Result<Void> delete(@PathVariable Long id) {
        problemService.removeById(id);
        return Result.ok();
    }
}
