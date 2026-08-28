package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI 调用审计只读入口。 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminAiController {
    private final AiGatewayFeignClient aiGatewayClient;
    private final AdminFeignExecutor executor;

    @GetMapping("/calls")
    public Result<List<AiCallLogDTO>> calls(@Valid AiCallQueryDTO query) {
        return executor.forward("AI 网关", () -> aiGatewayClient.listCalls(query));
    }

    @GetMapping("/calls/stats")
    public Result<AiCallStatsDTO> stats(@Valid AiCallQueryDTO query) {
        return executor.forward("AI 网关", () -> aiGatewayClient.getCallStats(query));
    }

    @GetMapping("/queue")
    public Result<List<AiQueueTaskDTO>> queue(@Valid AiQueueQueryDTO query) {
        return executor.forward("AI 网关", () -> aiGatewayClient.listQueueTasks(query));
    }

    @PostMapping("/queue/{taskId}/cancel")
    public Result<AiQueueTaskDTO> cancelQueueTask(@PathVariable String taskId) {
        return executor.forward("AI 网关", () -> aiGatewayClient.cancelQueueTask(taskId));
    }
}
