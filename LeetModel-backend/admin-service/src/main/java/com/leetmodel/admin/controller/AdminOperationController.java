package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.client.RankingAdminFeignClient;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.AdminProblemRankingDetailStatsDTO;
import com.leetmodel.common.api.dto.AdminRankingPageQuery;
import com.leetmodel.common.api.dto.AdminRankingScoreOverrideDTO;
import com.leetmodel.common.api.dto.AdminSubmissionPageQuery;
import com.leetmodel.common.api.dto.AdminSubmissionStatsDTO;
import com.leetmodel.common.api.dto.AdminTeamCreateDTO;
import com.leetmodel.common.api.dto.AdminTeamPageQuery;
import com.leetmodel.common.api.dto.AdminTeamPracticeStatusDTO;
import com.leetmodel.common.api.dto.AdminTeamStatsDTO;
import com.leetmodel.common.api.dto.AdminTeamUpdateDTO;
import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.dto.SuggestionTaskSummaryDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.vo.RankingAdminEntryVO;
import com.leetmodel.common.api.vo.SubmissionAdminVO;
import com.leetmodel.common.api.vo.TeamAdminVO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.RankingFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.SuggestionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/api/admin/teams/references")
    public Result<List<TeamDTO>> teamReferences(
            @RequestParam
            @Size(min = 1, max = 100)
            List<@Positive Long> teamIds) {
        return executor.forward("队伍服务", () -> teamClient.listSummaries(teamIds));
    }

    @GetMapping("/api/admin/teams/page")
    public Result<PageResult<TeamAdminVO>> teamPage(AdminTeamPageQuery query) {
        return executor.forward("队伍服务", () -> teamClient.pageAdminTeams(query));
    }

    @GetMapping("/api/admin/teams/stats")
    public Result<AdminTeamStatsDTO> teamStats() {
        return executor.forward("队伍服务", teamClient::getAdminStats);
    }

    @GetMapping("/api/admin/teams/{id}/detail")
    public Result<TeamAdminVO> teamDetail(@PathVariable @Positive Long id) {
        return executor.forward("队伍服务", () -> teamClient.getAdminDetail(id));
    }

    @PostMapping("/api/admin/teams")
    public Result<TeamAdminVO> createTeam(@Validated @RequestBody AdminTeamCreateDTO request) {
        return executor.forward("队伍服务", () -> teamClient.adminCreateTeam(request));
    }

    @PutMapping("/api/admin/teams/{id}")
    public Result<TeamAdminVO> updateTeam(@PathVariable @Positive Long id,
                                          @Validated @RequestBody AdminTeamUpdateDTO request) {
        return executor.forward("队伍服务", () -> teamClient.adminUpdateTeam(id, request));
    }

    @PutMapping("/api/admin/teams/{id}/practice-status")
    public Result<TeamAdminVO> updateTeamPracticeStatus(
            @PathVariable @Positive Long id,
            @Validated @RequestBody AdminTeamPracticeStatusDTO request) {
        return executor.forward("队伍服务", () -> teamClient.adminUpdatePracticeStatus(id, request));
    }

    @DeleteMapping("/api/admin/teams/{id}")
    public Result<Void> dissolveTeam(@PathVariable @Positive Long id,
                                     @RequestParam(required = false) String reason) {
        return executor.forward("队伍服务", () -> teamClient.adminDissolveTeam(id, reason));
    }

    @GetMapping("/api/admin/submissions")
    public Result<List<SubmissionSnapshotDTO>> submissions(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit) {
        return executor.forward("提交服务", () -> submissionClient.listRecent(limit));
    }

    @GetMapping("/api/admin/submissions/{submissionId}/preview")
    public Result<SubmissionPreviewDTO> submissionPreview(@PathVariable @Positive Long submissionId) {
        return executor.forward("提交服务", () -> submissionClient.getPreview(submissionId));
    }

    @GetMapping("/api/admin/submissions/page")
    public Result<PageResult<SubmissionAdminVO>> submissionPage(AdminSubmissionPageQuery query) {
        return executor.forward("提交服务", () -> submissionClient.pageAdminSubmissions(query));
    }

    @GetMapping("/api/admin/submissions/stats")
    public Result<AdminSubmissionStatsDTO> submissionStats() {
        return executor.forward("提交服务", submissionClient::getAdminSubmissionStats);
    }

    @GetMapping("/api/admin/submissions/{submissionId}/detail")
    public Result<SubmissionAdminVO> submissionDetail(@PathVariable @Positive Long submissionId) {
        return executor.forward("提交服务", () -> submissionClient.getAdminSubmissionDetail(submissionId));
    }

    @PutMapping("/api/admin/submissions/{submissionId}/set-final")
    public Result<SubmissionAdminVO> setFinalSubmission(@PathVariable @Positive Long submissionId) {
        return executor.forward("提交服务", () -> submissionClient.setFinalVersion(submissionId));
    }

    @DeleteMapping("/api/admin/submissions/{submissionId}")
    public Result<Void> invalidateSubmission(@PathVariable @Positive Long submissionId) {
        return executor.forward("提交服务", () -> submissionClient.adminInvalidateSubmission(submissionId));
    }

    @PostMapping("/api/admin/submissions/{submissionId}/re-dispatch")
    public Result<Void> redispatchReview(@PathVariable @Positive Long submissionId) {
        return executor.forward("提交服务", () -> submissionClient.adminRedispatchReview(submissionId));
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

    @GetMapping("/api/admin/rankings/global-stats")
    public Result<Object> globalRankingStats() {
        return executor.forward("排行服务", rankingAdminClient::globalStats);
    }

    @PostMapping("/api/admin/rankings/problems/{problemId}/rebuild")
    public Result<Integer> rebuildRanking(@PathVariable @Positive Long problemId) {
        return executor.forward("排行服务", () -> rankingClient.rebuild(problemId));
    }

    @GetMapping("/api/admin/rankings/problems/{problemId}/page")
    public Result<PageResult<RankingAdminEntryVO>> rankingPage(
            @PathVariable @Positive Long problemId,
            AdminRankingPageQuery query) {
        return executor.forward("排行服务", () -> rankingAdminClient.pageAdminRanking(problemId, query));
    }

    @GetMapping("/api/admin/rankings/problems/{problemId}/stats")
    public Result<AdminProblemRankingDetailStatsDTO> problemRankingStats(@PathVariable @Positive Long problemId) {
        return executor.forward("排行服务", () -> rankingAdminClient.getProblemRankingDetailStats(problemId));
    }

    @DeleteMapping("/api/admin/rankings/entries/{id}")
    public Result<Void> disqualifyRankingEntry(@PathVariable @Positive Long id) {
        return executor.forward("排行服务", () -> rankingAdminClient.adminDisqualifyEntry(id));
    }

    @PutMapping("/api/admin/rankings/entries/{id}/score-override")
    public Result<Void> overrideRankingScore(
            @PathVariable @Positive Long id,
            @Validated @RequestBody AdminRankingScoreOverrideDTO request) {
        return executor.forward("排行服务", () -> rankingAdminClient.adminScoreOverride(id, request));
    }

    @PostMapping("/api/admin/rankings/rebuild-all")
    public Result<Integer> rebuildAllRankings() {
        return executor.forward("排行服务", rankingAdminClient::rebuildAllRankings);
    }
}
