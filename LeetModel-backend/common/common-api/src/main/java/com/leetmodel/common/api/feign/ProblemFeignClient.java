package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 题目服务 Feign 客户端 —— 供其他微服务调用。
 */
@FeignClient(
        name = "problem-service",
        fallbackFactory = ProblemFeignFallback.class
)
public interface ProblemFeignClient {

    @GetMapping("/internal/problems/count")
    Result<Long> getProblemCount();

    @GetMapping("/internal/problems/{problemId}/practice")
    Result<ProblemPracticeDTO> getPracticeProblem(@PathVariable Long problemId);
}
