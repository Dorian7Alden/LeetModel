package com.leetmodel.common.api.feign;

import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 题目服务 Feign 客户端 —— 供其他微服务调用。
 *
 * @author LeetModel
 */
@FeignClient(
        name = "leetmodel-problem",
        fallbackFactory = ProblemFeignFallback.class
)
public interface ProblemFeignClient {

    @GetMapping("/internal/problems/count")
    Result<Long> getProblemCount();
}
