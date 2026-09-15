package com.leetmodel.admin.controller;

import com.leetmodel.admin.client.RankingAdminFeignClient;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.AdminProblemRankingDetailStatsDTO;
import com.leetmodel.common.api.dto.AdminRankingPageQuery;
import com.leetmodel.common.api.dto.AdminRankingScoreOverrideDTO;
import com.leetmodel.common.api.dto.AdminSubmissionPageQuery;
import com.leetmodel.common.api.dto.AdminSubmissionStatsDTO;
import com.leetmodel.common.api.dto.AdminTeamPageQuery;
import com.leetmodel.common.api.dto.AdminTeamStatsDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.RankingFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.SuggestionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.api.vo.RankingAdminEntryVO;
import com.leetmodel.common.api.vo.SubmissionAdminVO;
import com.leetmodel.common.api.vo.TeamAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationControllerTest {

    @Mock private TeamFeignClient teamClient;
    @Mock private SubmissionFeignClient submissionClient;
    @Mock private ReviewFeignClient reviewClient;
    @Mock private SuggestionFeignClient suggestionClient;
    @Mock private AssistantFeignClient assistantClient;
    @Mock private RankingFeignClient rankingClient;
    @Mock private RankingAdminFeignClient rankingAdminClient;

    private AdminOperationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminOperationController(
                teamClient,
                submissionClient,
                reviewClient,
                suggestionClient,
                assistantClient,
                rankingClient,
                rankingAdminClient,
                new AdminFeignExecutor()
        );
    }

    @Test
    void teamPageShouldForwardToTeamClient() {
        AdminTeamPageQuery query = new AdminTeamPageQuery(1, 20, "测试队", 101L, 1, "IN_PROGRESS");
        PageResult<TeamAdminVO> pageResult = new PageResult<>(1L, 1, 20, List.of(
                TeamAdminVO.builder().id(1L).name("测试队").memberCount(3).build()
        ));
        when(teamClient.pageAdminTeams(any())).thenReturn(Result.ok(pageResult));

        Result<PageResult<TeamAdminVO>> result = controller.teamPage(query);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        assertThat(result.getData().getRows().get(0).getName()).isEqualTo("测试队");
        verify(teamClient).pageAdminTeams(query);
    }

    @Test
    void teamStatsShouldForwardToTeamClient() {
        AdminTeamStatsDTO stats = AdminTeamStatsDTO.builder()
                .totalTeams(100L)
                .activeTeams(80L)
                .disbandedTeams(20L)
                .build();
        when(teamClient.getAdminStats()).thenReturn(Result.ok(stats));

        Result<AdminTeamStatsDTO> result = controller.teamStats();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotalTeams()).isEqualTo(100L);
        verify(teamClient).getAdminStats();
    }

    @Test
    void submissionPageShouldForwardToSubmissionClient() {
        AdminSubmissionPageQuery query = new AdminSubmissionPageQuery(1, 20, "paper", 1L, 101L, 9L, "SUCCESS", true);
        PageResult<SubmissionAdminVO> pageResult = new PageResult<>(1L, 1, 20, List.of(
                SubmissionAdminVO.builder().id(201L).originalFilename("paper.pdf").finalVersion(true).build()
        ));
        when(submissionClient.pageAdminSubmissions(any())).thenReturn(Result.ok(pageResult));

        Result<PageResult<SubmissionAdminVO>> result = controller.submissionPage(query);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRows().get(0).getOriginalFilename()).isEqualTo("paper.pdf");
        verify(submissionClient).pageAdminSubmissions(query);
    }

    @Test
    void submissionStatsShouldForwardToSubmissionClient() {
        AdminSubmissionStatsDTO stats = AdminSubmissionStatsDTO.builder()
                .totalSubmissions(500L)
                .successSubmissions(450L)
                .build();
        when(submissionClient.getAdminSubmissionStats()).thenReturn(Result.ok(stats));

        Result<AdminSubmissionStatsDTO> result = controller.submissionStats();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotalSubmissions()).isEqualTo(500L);
        verify(submissionClient).getAdminSubmissionStats();
    }

    @Test
    void setFinalSubmissionShouldForwardToSubmissionClient() {
        SubmissionAdminVO vo = SubmissionAdminVO.builder().id(301L).finalVersion(true).build();
        when(submissionClient.setFinalVersion(301L)).thenReturn(Result.ok(vo));

        Result<SubmissionAdminVO> result = controller.setFinalSubmission(301L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getFinalVersion()).isTrue();
        verify(submissionClient).setFinalVersion(301L);
    }

    @Test
    void rankingPageShouldForwardToRankingAdminClient() {
        AdminRankingPageQuery query = new AdminRankingPageQuery(1, 20, "先锋队", null, null, null);
        PageResult<RankingAdminEntryVO> pageResult = new PageResult<>(1L, 1, 20, List.of(
                RankingAdminEntryVO.builder().id(10L).rank(1).score(new BigDecimal("95.50")).build()
        ));
        when(rankingAdminClient.pageAdminRanking(eq(101L), any())).thenReturn(Result.ok(pageResult));

        Result<PageResult<RankingAdminEntryVO>> result = controller.rankingPage(101L, query);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getRows().get(0).getRank()).isEqualTo(1);
        verify(rankingAdminClient).pageAdminRanking(101L, query);
    }

    @Test
    void problemRankingStatsShouldForwardToRankingAdminClient() {
        AdminProblemRankingDetailStatsDTO stats = AdminProblemRankingDetailStatsDTO.builder()
                .problemId(101L)
                .totalTeams(50L)
                .averageScore(new BigDecimal("82.30"))
                .build();
        when(rankingAdminClient.getProblemRankingDetailStats(101L)).thenReturn(Result.ok(stats));

        Result<AdminProblemRankingDetailStatsDTO> result = controller.problemRankingStats(101L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getTotalTeams()).isEqualTo(50L);
        verify(rankingAdminClient).getProblemRankingDetailStats(101L);
    }

    @Test
    void overrideRankingScoreShouldForwardToRankingAdminClient() {
        AdminRankingScoreOverrideDTO request = new AdminRankingScoreOverrideDTO(new BigDecimal("88.00"), "评委复议调整");
        when(rankingAdminClient.adminScoreOverride(eq(5L), any())).thenReturn(Result.ok());

        Result<Void> result = controller.overrideRankingScore(5L, request);

        assertThat(result.isSuccess()).isTrue();
        verify(rankingAdminClient).adminScoreOverride(5L, request);
    }

    @Test
    void rebuildAllRankingsShouldForwardToRankingAdminClient() {
        when(rankingAdminClient.rebuildAllRankings()).thenReturn(Result.ok(10));

        Result<Integer> result = controller.rebuildAllRankings();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(10);
        verify(rankingAdminClient).rebuildAllRankings();
    }
}
