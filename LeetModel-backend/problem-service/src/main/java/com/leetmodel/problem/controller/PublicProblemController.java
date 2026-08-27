package com.leetmodel.problem.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import com.leetmodel.problem.vo.ProblemFilterOptionsVO;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目公开接口（无需认证，仅返回已发布题目）。
 */
@RestController
@RequestMapping("/api/public/problems")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "题目浏览")
public class PublicProblemController {

    private final ProblemService problemService;
    private final ContestMapper contestMapper;
    private final TagMapper tagMapper;

    @Operation(summary = "查询公开题库筛选项")
    @GetMapping("/filter-options")
    public Result<ProblemFilterOptionsVO> filterOptions() {
        List<Contest> contests = contestMapper.selectList(
                new LambdaQueryWrapper<Contest>().orderByAsc(Contest::getId)
        );
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getType).orderByAsc(Tag::getName)
        );
        return Result.ok(new ProblemFilterOptionsVO(contests, tags));
    }

    @Operation(summary = "分页浏览已发布题目")
    @GetMapping
    public Result<PageResult<ProblemVO>> page(@Valid ProblemPageQuery query) {
        // 公开接口强制只查询已发布题目
        query.setStatus(1);
        return Result.ok(PageResult.from(problemService.pageProblems(query)));
    }

    @Operation(summary = "浏览题目详情")
    @GetMapping("/{id}")
    public Result<ProblemVO> detail(@PathVariable Long id) {
        ProblemVO vo = problemService.getPublishedProblemDetail(id);
        return Result.ok(vo);
    }

    @Operation(summary = "随机获取已发布题目")
    @GetMapping("/random")
    public Result<ProblemVO> random(@Valid ProblemPageQuery query) {
        return Result.ok(problemService.getRandomPublishedProblem(query));
    }
}
