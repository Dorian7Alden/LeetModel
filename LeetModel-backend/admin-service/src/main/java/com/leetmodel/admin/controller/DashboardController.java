package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端仪表盘接口。
 *
 * <p>面向客户端管理端页面，通过 Feign 聚合各微服务统计数据。admin-service
 * 不持有业务数据，只做聚合展示。</p>
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "管理端-仪表盘")
public class DashboardController {

    private final UserFeignClient userFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final ProblemFeignClient problemFeignClient;

    @Operation(summary = "获取汇总统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 安全获取各服务统计（Feign 调用失败时 fallback 返回 0）
        result.put("userCount", safeGet(userFeignClient::getUserCount, 0L));
        result.put("teamCount", safeGet(teamFeignClient::getActiveTeamCount, 0L));
        result.put("problemCount", safeGet(problemFeignClient::getProblemCount, 0L));

        return Result.ok(result);
    }

    /**
     * 安全获取远程调用结果，异常时返回默认值。
     */
    @FunctionalInterface
    private interface SafeSupplier<T> {
        Result<T> get();
    }

    @SuppressWarnings("unchecked")
    private <T> T safeGet(SafeSupplier<T> supplier, T defaultValue) {
        try {
            Result<T> result = supplier.get();
            return result != null && result.isSuccess() ? result.getData() : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
