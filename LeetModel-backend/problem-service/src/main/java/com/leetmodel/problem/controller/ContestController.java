package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.problem.dto.ContestRequest;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.service.ContestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "赛事基础数据")
public class ContestController {
    private final ContestService contestService;

    @Operation(summary = "查询预置赛事列表")
    @GetMapping
    public Result<List<Contest>> list() {
        return Result.ok(contestService.list());
    }

    @Operation(summary = "更新赛事基础数据")
    @PutMapping("/{id}")
    public Result<Contest> update(@PathVariable Long id,
                                  @Valid @RequestBody ContestRequest request) {
        return Result.ok(contestService.update(id, request.getCode(), request.getName()));
    }
}
