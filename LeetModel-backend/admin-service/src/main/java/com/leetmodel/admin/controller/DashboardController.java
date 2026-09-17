package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.vo.AdminDashboardVO;
import com.leetmodel.admin.vo.AdminMetricVO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.EvaluationFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.RankingFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.SuggestionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 管理看板聚合；每个指标独立表达真实零值或下游不可用。 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "管理端-仪表盘")
public class DashboardController {

    private final UserFeignClient userFeignClient;
    private final TeamFeignClient teamFeignClient;
    private final ProblemFeignClient problemFeignClient;
    private final SubmissionFeignClient submissionFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final SuggestionFeignClient suggestionFeignClient;
    private final RankingFeignClient rankingFeignClient;
    private final AssistantFeignClient assistantFeignClient;
    private final EvaluationFeignClient evaluationFeignClient;
    private final AiGatewayFeignClient aiGatewayFeignClient;

    /**
     * 聚合平台全量微服务的关键运行态统计大盘指标（容忍局部下游服务不可用）。
     *
     * @return 包含用户、队伍、题目、提交、评审、排行与 AI 调用的指标聚合视图对象
     */
    @Operation(summary = "获取真实汇总统计及局部失败状态")
    @GetMapping("/stats")
    public Result<AdminDashboardVO> stats() {
        Map<String, AdminMetricVO> metrics = new LinkedHashMap<>();
        metrics.put("users", metric("用户服务", userFeignClient::getUserCount));
        metrics.put("teams", metric("队伍服务", teamFeignClient::getActiveTeamCount));
        metrics.put("problems", metric("题目服务", problemFeignClient::getProblemCount));
        metrics.put("submissions", metric("提交服务", submissionFeignClient::getSubmissionCount));
        metrics.put("reviews", metric("评审服务", reviewFeignClient::getReviewCount));
        metrics.put("suggestions", metric("建议服务", suggestionFeignClient::getSuggestionCount));
        metrics.put("rankings", metric("排行服务", rankingFeignClient::getCurrentRankingCount));
        metrics.put("assistantConversations", metric("客服服务", assistantFeignClient::getConversationCount));
        metrics.put("evaluationTasks", metric("质量评价服务", evaluationFeignClient::countTasks));
        metrics.put("aiCalls", aiCallMetric());
        boolean partialFailure = metrics.values().stream().anyMatch(value -> !value.isAvailable());
        return Result.ok(new AdminDashboardVO(metrics, LocalDateTime.now(), partialFailure));
    }

    private AdminMetricVO aiCallMetric() {
        try {
            Result<AiCallStatsDTO> result = aiGatewayFeignClient.getCallStats(new AiCallQueryDTO());
            if (result != null && result.isSuccess() && result.getData() != null) {
                return AdminMetricVO.available(result.getData().getTotalCount());
            }
            return AdminMetricVO.unavailable(message(result, "AI 网关暂不可用"));
        } catch (RuntimeException exception) {
            return AdminMetricVO.unavailable("AI 网关暂不可用");
        }
    }

    private AdminMetricVO metric(String serviceName, CountCall call) {
        try {
            Result<Long> result = call.get();
            if (result != null && result.isSuccess() && result.getData() != null) {
                return AdminMetricVO.available(result.getData());
            }
            return AdminMetricVO.unavailable(message(result, serviceName + "暂不可用"));
        } catch (RuntimeException exception) {
            return AdminMetricVO.unavailable(serviceName + "暂不可用");
        }
    }

    private String message(Result<?> result, String fallback) {
        return result != null && result.getMessage() != null && !result.getMessage().isBlank()
                ? result.getMessage() : fallback;
    }

    @FunctionalInterface
    private interface CountCall {
        Result<Long> get();
    }
}
