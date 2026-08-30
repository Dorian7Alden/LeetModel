package com.leetmodel.ranking.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.ranking.service.RankingService;
import com.leetmodel.ranking.vo.GlobalRankingOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/internal/rankings")
@RequiredArgsConstructor
public class InternalRankingController {

    private final RankingService rankingService;

    @Operation(summary = "重建指定题目排行")
    @PostMapping("/problems/{problemId}/rebuild")
    public Result<Integer> rebuild(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId) {
        return Result.ok(rankingService.rebuild(problemId).getTotal());
    }

    @Operation(summary = "获取当前排行记录数")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(rankingService.countCurrent());
    }

    @Operation(summary = "获取全局排行统计")
    @GetMapping("/global-stats")
    public Result<GlobalRankingOverviewVO> globalStats() {
        return Result.ok(rankingService.getGlobalStats());
    }
}
