package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.mapper.ContestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "赛事基础数据")
public class ContestController {
    private final ContestMapper contestMapper;

    @Operation(summary = "查询预置赛事列表")
    @GetMapping
    public Result<List<Contest>> list() {
        return Result.ok(contestMapper.selectList(new LambdaQueryWrapper<Contest>().orderByAsc(Contest::getCode)));
    }

}
