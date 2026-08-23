package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.security.context.UserContext;
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
 * 题目管理接口（需要 admin 角色）。
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "题目管理")
public class ProblemController {

    private final ProblemService problemService;

    @Operation(summary = "分页查询题目")
    @GetMapping
    public Result<PageResult<ProblemVO>> page(@Valid ProblemPageQuery query) {
        IPage<ProblemVO> page = problemService.pageProblems(query);
        return Result.ok(PageResult.from(page));
    }

    @Operation(summary = "查询题目详情")
    @GetMapping("/{id}")
    public Result<ProblemVO> detail(@PathVariable Long id) {
        ProblemVO vo = problemService.getProblemDetail(id);
        return Result.ok(vo);
    }

    @Operation(summary = "创建题目")
    @PostMapping
    public Result<ProblemVO> create(@Valid @RequestBody ProblemCreateRequest request) {
        Long creatorId = UserContext.getUserId();
        ProblemVO vo = problemService.createProblem(request, creatorId);
        return Result.ok(vo);
    }

    @Operation(summary = "更新题目")
    @PutMapping("/{id}")
    public Result<ProblemVO> update(@PathVariable Long id,
                                     @Valid @RequestBody ProblemUpdateRequest request) {
        ProblemVO vo = problemService.updateProblem(id, request);
        return Result.ok(vo);
    }

    @Operation(summary = "删除题目（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return Result.ok();
    }
}
