package com.leetmodel.aigateway.controller;

import com.leetmodel.aigateway.service.AiChatService;
import com.leetmodel.aigateway.service.AiModelService;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiModelInfo;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.aigateway.service.AiCallAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务服务调用 AI 网关的内部接口。
 */
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
@Tag(name = "AI 网关内部调用")
public class InternalAiController {

    private final AiChatService aiChatService;
    private final AiModelService aiModelService;
    private final AiCallAuditService aiCallAuditService;

    /**
     * 发起同步 AI 对话。
     *
     * @param request 统一请求
     * @return 统一响应
     */
    @Operation(summary = "发起同步 AI 对话")
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.ok(aiChatService.chat(request));
    }

    /**
     * 查询供应商官方模型列表。
     *
     * @param provider 供应商
     * @return 模型列表
     */
    @Operation(summary = "查询供应商官方模型列表")
    @GetMapping("/models/{provider}")
    public Result<List<AiModelInfo>> listModels(@PathVariable AiProvider provider) {
        return Result.ok(aiModelService.listModels(provider));
    }

    @Operation(summary = "查询最近 AI 调用审计")
    @GetMapping("/calls")
    public Result<List<AiCallLogDTO>> listCalls(@Valid AiCallQueryDTO query) {
        return Result.ok(aiCallAuditService.list(query));
    }

    @Operation(summary = "查询 AI 调用运行摘要")
    @GetMapping("/calls/stats")
    public Result<AiCallStatsDTO> callStats() {
        return Result.ok(aiCallAuditService.stats());
    }
}
