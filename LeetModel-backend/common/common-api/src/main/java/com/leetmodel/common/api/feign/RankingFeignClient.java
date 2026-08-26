package com.leetmodel.common.api.feign;

import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 排行服务内部调用契约。
 */
@FeignClient(name = "ranking-service")
public interface RankingFeignClient {

    @PostMapping("/internal/rankings/problems/{problemId}/rebuild")
    Result<Integer> rebuild(@PathVariable Long problemId);

    @GetMapping("/internal/rankings/count")
    Result<Long> getCurrentRankingCount();
}
