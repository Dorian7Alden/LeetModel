package com.leetmodel.suggestion.controller;

import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.suggestion.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 统计系统当前生成的建议任务记录总数。
     *
     * @return 建议任务总数
     */
    @Operation(summary = "获取论文建议任务数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(suggestionService.count());
    }

    /**
     * 按时间倒序查询最近的建议任务摘要列表。
     *
     * @param limit 单次拉取数量上限
     * @return 建议任务摘要 DTO 列表
     */
    @Operation(summary = "获取最近论文建议任务")
    @GetMapping("/tasks")
    public Result<List<SuggestionTaskSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(suggestionService.listRecent(limit));
    }

    /**
     * 获取论文建议功能的元数据定义与候选工作流版本目录。
     *
     * @return 功能定义 DTO
     */
    @Operation(summary = "查询 AI 论文建议功能与工作流版本目录")
    @GetMapping("/feature-definition")
    public Result<AiFeatureDefinitionDTO> getFeatureDefinition() {
        return Result.ok(suggestionService.getFeatureDefinition());
    }

    /**
     * 使用跨功能通用协议执行隔离论文建议实验（供评测系统调度）。
     *
     * @param request 通用实验请求对象，不能为 null
     * @return 通用实验结果 DTO
     */
    @Operation(summary = "使用通用契约执行隔离论文建议实验")
    @PostMapping("/experiments")
    public Result<AiExperimentResultDTO> runExperiment(
            @Valid @RequestBody AiExperimentRequestDTO request) {
        return Result.ok(suggestionService.runExperiment(request));
    }
}
