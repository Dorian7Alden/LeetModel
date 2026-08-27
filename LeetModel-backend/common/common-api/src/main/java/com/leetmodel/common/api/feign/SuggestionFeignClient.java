package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 论文建议服务内部调用契约。
 */
@FeignClient(name = "ai-suggestion-service")
public interface SuggestionFeignClient {

    @GetMapping("/internal/suggestions/count")
    Result<Long> getSuggestionCount();

    @GetMapping("/internal/suggestions/tasks")
    Result<List<SuggestionTaskSummaryDTO>> listRecentTasks(@RequestParam Integer limit);
}
