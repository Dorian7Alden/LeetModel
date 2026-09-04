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

    /**
     * 管理员查询系统预置的赛事字典列表。
     *
     * @return 赛事字典实体列表
     */
    @Operation(summary = "查询预置赛事列表")
    @GetMapping
    public Result<List<Contest>> list() {
        return Result.ok(contestService.list());
    }

    /**
     * 管理员修改赛事的编码或名称基础数据。
     *
     * @param id      目标赛事 ID，不能为 null
     * @param request 包含新编码与名称的请求对象，不能为 null
     * @return 更新后的赛事字典实体
     */
    @Operation(summary = "更新赛事基础数据")
    @PutMapping("/{id}")
    public Result<Contest> update(@PathVariable Long id,
                                  @Valid @RequestBody ContestRequest request) {
        return Result.ok(contestService.update(id, request.getCode(), request.getName()));
    }
}
