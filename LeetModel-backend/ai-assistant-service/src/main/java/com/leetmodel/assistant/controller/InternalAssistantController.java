package com.leetmodel.assistant.controller;

import com.leetmodel.assistant.service.AssistantService;
import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.core.result.Result;
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
@RequestMapping("/internal/assistant/conversations")
@RequiredArgsConstructor
public class InternalAssistantController {

    private final AssistantService assistantService;

    @Operation(summary = "获取 AI 客服会话数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(assistantService.countConversations());
    }

    @Operation(summary = "获取最近 AI 客服会话")
    @GetMapping
    public Result<List<AssistantConversationSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(assistantService.listRecent(limit));
    }
}
