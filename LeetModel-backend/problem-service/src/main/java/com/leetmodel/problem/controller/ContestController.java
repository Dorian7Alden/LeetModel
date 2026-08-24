package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.problem.dto.ContestRequest;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "赛事管理")
public class ContestController {
    private final ContestMapper contestMapper;

    @Operation(summary = "查询赛事列表")
    @GetMapping
    public Result<List<Contest>> list() {
        return Result.ok(contestMapper.selectList(new LambdaQueryWrapper<Contest>().orderByAsc(Contest::getCode)));
    }

    @Operation(summary = "创建赛事")
    @PostMapping
    public Result<Contest> create(@Valid @RequestBody ContestRequest request) {
        BusinessException.throwIf(contestMapper.selectCount(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getCode, request.getCode())) > 0, ProblemErrorCode.CONTEST_CODE_DUPLICATE);
        Contest contest = new Contest();
        contest.setCode(request.getCode());
        contest.setName(request.getName());
        contest.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        contestMapper.insert(contest);
        return Result.ok(contest);
    }

    @Operation(summary = "更新赛事")
    @PutMapping("/{id}")
    public Result<Contest> update(@PathVariable Long id, @Valid @RequestBody ContestRequest request) {
        Contest contest = contestMapper.selectById(id);
        BusinessException.throwIf(contest == null, ProblemErrorCode.CONTEST_NOT_FOUND);
        BusinessException.throwIf(contestMapper.selectCount(new LambdaQueryWrapper<Contest>()
                        .eq(Contest::getCode, request.getCode()).ne(Contest::getId, id)) > 0,
                ProblemErrorCode.CONTEST_CODE_DUPLICATE);
        contest.setCode(request.getCode());
        contest.setName(request.getName());
        contest.setStatus(request.getStatus() == null ? contest.getStatus() : request.getStatus());
        contestMapper.updateById(contest);
        return Result.ok(contest);
    }
}
