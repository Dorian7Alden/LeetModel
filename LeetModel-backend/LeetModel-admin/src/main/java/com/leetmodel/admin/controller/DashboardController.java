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
 * 管理后台仪表盘接口。
 *
 * <p>通过 Feign 聚合各服务统计数据，后续可替换为缓存或定时任务预计算。</p>
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "管理后台-仪表盘")
public class DashboardController {

    private final UserFeignClient userFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final ProblemFeignClient problemFeignClient;

    @GetMapping("/stats")
    @Operation(summary = "获取汇总统计数据")
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
