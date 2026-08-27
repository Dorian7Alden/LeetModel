package com.leetmodel.ranking.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.ranking.service.RankingService;
import com.leetmodel.ranking.vo.RankingOverviewVO;
import com.leetmodel.ranking.vo.TeamRankingContextVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
@Tag(name = "论文排行")
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "查询题目当前排行")
    @GetMapping("/problems/{problemId}")
    public Result<RankingOverviewVO> current(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId,
            @RequestParam(required = false)
            @Size(max = 100, message = "队伍名称关键字不能超过100个字符") String keyword) {
        return Result.ok(rankingService.getCurrent(problemId, keyword));
    }

    @Operation(summary = "定位队伍在题目排行中的位置")
    @GetMapping("/problems/{problemId}/teams/{teamId}")
    public Result<TeamRankingContextVO> locate(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId,
            @PathVariable @Positive(message = "队伍标识必须为正整数") Long teamId,
            @RequestParam(defaultValue = "2")
            @Min(value = 0, message = "附近范围不能小于0")
            @Max(value = 10, message = "附近范围不能超过10") Integer radius) {
        return Result.ok(rankingService.locate(problemId, teamId, radius));
    }
}
