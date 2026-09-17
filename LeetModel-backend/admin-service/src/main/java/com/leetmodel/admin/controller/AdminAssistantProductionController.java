package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.dto.AdminAssistantProductionChangeApplyDTO;
import com.leetmodel.admin.dto.AdminAssistantProductionChangePreviewDTO;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.AssistantProductionAuditDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangeResultDTO;
import com.leetmodel.common.api.dto.AssistantProductionConfigDTO;
import com.leetmodel.common.api.dto.AssistantProductionWorkflowDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
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

/** AI 客服生产版本的管理员鉴权与无状态命令代理。 */
@Validated
@RestController
@RequestMapping("/api/admin/ai/assistant/production")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminAssistantProductionController {

    private final AssistantFeignClient assistantClient;
    private final AdminFeignExecutor executor;

    @Operation(summary = "查询AI客服生产工作流目录")
    @GetMapping("/workflows")
    public Result<List<AssistantProductionWorkflowDTO>> workflows() {
        return executor.forward("AI 客服服务", assistantClient::listProductionWorkflows);
    }

    @Operation(summary = "查询AI客服当前生产配置")
    @GetMapping("/current")
    public Result<AssistantProductionConfigDTO> current() {
        return executor.forward("AI 客服服务", assistantClient::getCurrentProductionConfig);
    }

    @Operation(summary = "查询AI客服历史生产配置")
    @GetMapping("/configs")
    public Result<List<AssistantProductionConfigDTO>> configs(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("AI 客服服务", () -> assistantClient.listProductionConfigs(limit));
    }

    @Operation(summary = "查询AI客服生产变更审计")
    @GetMapping("/audits")
    public Result<List<AssistantProductionAuditDTO>> audits(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("AI 客服服务", () -> assistantClient.listProductionAudits(limit));
    }

    @Operation(summary = "预览AI客服生产配置变更")
    @PostMapping("/changes/preview")
    public Result<AssistantProductionChangePreviewDTO> preview(
            @Valid @RequestBody AdminAssistantProductionChangePreviewDTO request) {
        return executor.forward("AI 客服服务", () -> assistantClient.previewProductionChange(
                request.toInternal(UserContext.getUserId())));
    }

    @Operation(summary = "二次确认AI客服生产配置变更")
    @PostMapping("/changes/apply")
    public Result<AssistantProductionChangeResultDTO> apply(
            @Valid @RequestBody AdminAssistantProductionChangeApplyDTO request) {
        return executor.forward("AI 客服服务", () -> assistantClient.applyProductionChange(
                request.toInternal(UserContext.getUserId())));
    }
}
