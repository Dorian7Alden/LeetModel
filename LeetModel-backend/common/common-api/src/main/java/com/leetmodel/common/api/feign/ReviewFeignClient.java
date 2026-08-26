package com.leetmodel.common.api.feign;

import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "ai-review-service")
public interface ReviewFeignClient {
    @PostMapping("/internal/reviews/tasks")
    Result<Long> createTask(@RequestParam Long submissionId, @RequestParam Long teamId, @RequestParam Long problemId);
}
