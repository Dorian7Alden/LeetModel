package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.client.RankingAdminFeignClient;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.RankingFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.SuggestionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 队伍、提交、评审、建议、客服和排行榜的管理查询入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminOperationController {
    private final TeamFeignClient teamClient;
    private final SubmissionFeignClient submissionClient;
    private final ReviewFeignClient reviewClient;
    private final SuggestionFeignClient suggestionClient;
    private final AssistantFeignClient assistantClient;
    private final RankingFeignClient rankingClient;
    private final RankingAdminFeignClient rankingAdminClient;
    private final AdminFeignExecutor executor;

    @GetMapping("/api/admin/teams")
    public Result<List<TeamDTO>> teams(@RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("队伍服务", () -> teamClient.listRecent(limit));
    }

    @GetMapping("/api/admin/submissions")
    public Result<List<SubmissionSnapshotDTO>> submissions(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("提交服务", () -> submissionClient.listRecent(limit));
    }

    @GetMapping("/api/admin/reviews")
    public Result<List<ReviewSummaryDTO>> reviews(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("评审服务", () -> reviewClient.listRecent(limit));
    }

    @GetMapping("/api/admin/suggestions")
    public Result<List<SuggestionTaskSummaryDTO>> suggestions(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("建议服务", () -> suggestionClient.listRecentTasks(limit));
    }

    @GetMapping("/api/admin/assistant/conversations")
    public Result<List<AssistantConversationSummaryDTO>> conversations(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("客服服务", () -> assistantClient.listRecentConversations(limit));
    }

    @GetMapping("/api/admin/rankings/problems/{problemId}")
    public Result<Object> ranking(@PathVariable @Positive Long problemId,
                                  @RequestParam(required = false) @Size(max = 100) String keyword) {
        return executor.forward("排行服务", () -> rankingAdminClient.current(problemId, keyword));
    }

    @PostMapping("/api/admin/rankings/problems/{problemId}/rebuild")
    public Result<Integer> rebuildRanking(@PathVariable @Positive Long problemId) {
        return executor.forward("排行服务", () -> rankingClient.rebuild(problemId));
    }
}
