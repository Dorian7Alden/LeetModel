package com.leetmodel.ranking.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.ranking.service.RankingService;
import com.leetmodel.ranking.cache.RankingCachePolicy;
import com.leetmodel.common.cache.HttpCacheSupport;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final RankingCachePolicy cachePolicy;

    /**
     * 客户端查询指定题目的当前榜单（支持按队伍名即时过滤与 ETag 协商缓存）。
     *
     * @param problemId   目标题目 ID，不能为 null 且须为正整数
     * @param keyword     可选的队伍名称模糊过滤关键字
     * @param ifNoneMatch 请求头 ETag 缓存标识，可为空
     * @return 包含榜单概览视图对象的 HTTP 响应实体
     */
    @Operation(summary = "查询题目当前排行")
    @GetMapping("/problems/{problemId}")
    public ResponseEntity<Result<RankingOverviewVO>> current(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId,
            @RequestParam(required = false)
            @Size(max = 100, message = "队伍名称关键字不能超过100个字符") String keyword,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        HttpCacheSupport.Validator validator = cachePolicy.currentValidator(problemId, keyword);
        if (validator.matches(ifNoneMatch)) return validator.notModified();
        return validator.ok(Result.ok(rankingService.getCurrent(problemId, keyword)));
    }

    /**
     * 定位指定队伍在题目榜单中的排名及前后名次上下文。
     *
     * @param problemId   目标题目 ID，不能为 null
     * @param teamId      目标队伍 ID，不能为 null
     * @param radius      前后展示半径行数（0 到 10，默认 2）
     * @param ifNoneMatch 请求头 ETag 缓存标识，可为空
     * @return 包含目标队伍排名与邻近排行的 HTTP 响应实体
     */
    @Operation(summary = "定位队伍在题目排行中的位置")
    @GetMapping("/problems/{problemId}/teams/{teamId}")
    public ResponseEntity<Result<TeamRankingContextVO>> locate(
            @PathVariable @Positive(message = "题目标识必须为正整数") Long problemId,
            @PathVariable @Positive(message = "队伍标识必须为正整数") Long teamId,
            @RequestParam(defaultValue = "2")
            @Min(value = 0, message = "附近范围不能小于0")
            @Max(value = 10, message = "附近范围不能超过10") Integer radius,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        HttpCacheSupport.Validator validator = cachePolicy.locateValidator(problemId, teamId, radius);
        if (validator.matches(ifNoneMatch)) return validator.notModified();
        return validator.ok(Result.ok(rankingService.locate(problemId, teamId, radius)));
    }
}
