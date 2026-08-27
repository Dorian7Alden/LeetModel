package com.leetmodel.suggestion.controller;

import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/suggestions")
@RequiredArgsConstructor
public class InternalSuggestionController {

    private final SuggestionService suggestionService;

    @Operation(summary = "获取论文建议任务数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(suggestionService.count());
    }

    @Operation(summary = "获取最近论文建议任务")
    @GetMapping("/tasks")
    public Result<List<SuggestionTaskSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(suggestionService.listRecent(limit));
    }
}
