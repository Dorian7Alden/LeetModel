package com.leetmodel.admin.client;

import com.leetmodel.common.api.dto.AdminProblemRankingDetailStatsDTO;
import com.leetmodel.common.api.dto.AdminRankingPageQuery;
import com.leetmodel.common.api.dto.AdminRankingScoreOverrideDTO;
import com.leetmodel.common.api.vo.RankingAdminEntryVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ranking-service", contextId = "rankingAdminFeignClient")
public interface RankingAdminFeignClient {
    @GetMapping("/internal/rankings/global-stats")
    Result<Object> globalStats();

    @GetMapping("/api/rankings/problems/{problemId}")
    Result<Object> current(@PathVariable("problemId") Long problemId,
                           @RequestParam(value = "keyword", required = false) String keyword);

    @GetMapping("/internal/rankings/problems/{problemId}/admin/page")
    Result<PageResult<RankingAdminEntryVO>> pageAdminRanking(@PathVariable("problemId") Long problemId,
                                                             @SpringQueryMap AdminRankingPageQuery query);

    @GetMapping("/internal/rankings/problems/{problemId}/admin/stats")
    Result<AdminProblemRankingDetailStatsDTO> getProblemRankingDetailStats(@PathVariable("problemId") Long problemId);

    @DeleteMapping("/internal/rankings/entries/{id}/admin")
    Result<Void> adminDisqualifyEntry(@PathVariable("id") Long id);

    @PutMapping("/internal/rankings/entries/{id}/admin/score-override")
    Result<Void> adminScoreOverride(@PathVariable("id") Long id,
                                    @RequestBody AdminRankingScoreOverrideDTO request);

    @PostMapping("/internal/rankings/admin/rebuild-all")
    Result<Integer> rebuildAllRankings();
}
