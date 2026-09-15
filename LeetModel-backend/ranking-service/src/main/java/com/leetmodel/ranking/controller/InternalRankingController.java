package com.leetmodel.ranking.controller;

import com.leetmodel.common.api.dto.AdminProblemRankingDetailStatsDTO;
import com.leetmodel.common.api.dto.AdminRankingPageQuery;
import com.leetmodel.common.api.dto.AdminRankingScoreOverrideDTO;
import com.leetmodel.common.api.vo.RankingAdminEntryVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.ranking.service.RankingService;
import com.leetmodel.ranking.vo.GlobalRankingOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/internal/rankings")
@RequiredArgsConstructor
public class InternalRankingController {

    private final RankingService rankingService;

    /**
     * 重建指定题目的当前有效榜单批次。
     *
     * @param problemId 目标题目 ID，不能为 null
     * @return 榜单上榜队伍总数
     */
    @Operation(summary = "重建指定题目排行")
    @PostMapping("/problems/{problemId}/rebuild")
    public Result<Integer> rebuild(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId) {
        return Result.ok(rankingService.rebuild(problemId).getTotal());
    }

    /**
     * 统计当前处于激活状态的榜单总记录数。
     *
     * @return 当前榜单记录数
     */
    @Operation(summary = "获取当前排行记录数")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(rankingService.countCurrent());
    }

    /**
     * 汇总全平台题目的成功提交量、评审均分与上榜队伍总览统计。
     *
     * @return 全局排行概览视图对象
     */
    @Operation(summary = "获取全局排行统计")
    @GetMapping("/global-stats")
    public Result<GlobalRankingOverviewVO> globalStats() {
        return Result.ok(rankingService.getGlobalStats());
    }

    /**
     * 管理端题目榜单明细分页检索。
     */
    @Operation(summary = "管理端题目榜单明细分页检索")
    @GetMapping("/problems/{problemId}/admin/page")
    public Result<PageResult<RankingAdminEntryVO>> pageAdminRanking(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId,
            @Validated AdminRankingPageQuery query) {
        return Result.ok(rankingService.pageAdminRanking(problemId, query));
    }

    /**
     * 管理端查询单题深度统计指标与奖项梯队。
     */
    @Operation(summary = "管理端查询单题深度统计指标与奖项梯队")
    @GetMapping("/problems/{problemId}/admin/stats")
    public Result<AdminProblemRankingDetailStatsDTO> getProblemRankingDetailStats(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId) {
        return Result.ok(rankingService.getProblemRankingDetailStats(problemId));
    }

    /**
     * 管理端剔除违规榜单条目。
     */
    @Operation(summary = "管理端剔除违规榜单条目")
    @DeleteMapping("/entries/{id}/admin")
    public Result<Void> adminDisqualifyEntry(@PathVariable Long id) {
        rankingService.adminDisqualifyEntry(id);
        return Result.ok();
    }

    /**
     * 管理端人工复议修正成绩。
     */
    @Operation(summary = "管理端人工复议修正成绩")
    @PutMapping("/entries/{id}/admin/score-override")
    public Result<Void> adminScoreOverride(@PathVariable Long id,
                                           @Validated @RequestBody AdminRankingScoreOverrideDTO request) {
        rankingService.adminScoreOverride(id, request);
        return Result.ok();
    }

    /**
     * 管理端批量重建全平台赛题榜单。
     */
    @Operation(summary = "管理端批量重建全平台赛题榜单")
    @PostMapping("/admin/rebuild-all")
    public Result<Integer> rebuildAllRankings() {
        return Result.ok(rankingService.rebuildAllRankings());
    }
}
