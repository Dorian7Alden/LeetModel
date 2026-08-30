package com.leetmodel.admin.client;

import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ranking-service", contextId = "rankingAdminFeignClient")
public interface RankingAdminFeignClient {
    @GetMapping("/internal/rankings/global-stats")
    Result<Object> globalStats();

    @GetMapping("/api/rankings/problems/{problemId}")
    Result<Object> current(@PathVariable("problemId") Long problemId,
                           @RequestParam(value = "keyword", required = false) String keyword);
}
