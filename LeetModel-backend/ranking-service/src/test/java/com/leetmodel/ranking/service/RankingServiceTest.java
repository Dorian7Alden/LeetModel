package com.leetmodel.ranking.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.cache.internal.NoOpCacheSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.ranking.entity.RankingSnapshot;
import com.leetmodel.ranking.mapper.RankingSnapshotMapper;
import com.leetmodel.ranking.vo.RankingOverviewVO;
import com.leetmodel.ranking.vo.TeamRankingContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final Long PROBLEM_ID = 51L;

    @Mock
    private RankingSnapshotMapper snapshotMapper;
    @Mock
    private SubmissionFeignClient submissionFeignClient;
    @Mock
    private ReviewFeignClient reviewFeignClient;
    @Mock
    private TeamFeignClient teamFeignClient;
    @Mock
    private ProblemFeignClient problemFeignClient;

    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        NoOpCacheSupport cacheSupport = new NoOpCacheSupport();
        rankingService = new RankingService(
                snapshotMapper,
                submissionFeignClient,
                reviewFeignClient,
                teamFeignClient,
                problemFeignClient,
                cacheSupport,
                cacheSupport,
                new ObjectMapper().registerModule(new JavaTimeModule())
        );
    }

    @Test
    void rebuildUsesLatestFinalSubmissionAndCompetitionRankingForTies() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 26, 10, 0);
        List<SubmissionSnapshotDTO> submissions = List.of(
                submission(101L, 1L, base),
                submission(102L, 1L, base.plusMinutes(2)),
                submission(201L, 2L, base.plusMinutes(1)),
                submission(301L, 3L, base.plusMinutes(3))
        );
        List<ReviewSummaryDTO> reviews = List.of(
                review(1002L, 102L, 1L, "95.00", base.plusMinutes(8)),
                review(2001L, 201L, 2L, "95.0", base.plusMinutes(7)),
                review(3001L, 301L, 3L, "80", base.plusMinutes(9))
        );
        when(submissionFeignClient.listFinalSubmissions(PROBLEM_ID)).thenReturn(Result.ok(submissions));
        when(reviewFeignClient.listCompleted(PROBLEM_ID)).thenReturn(Result.ok(reviews));
        when(teamFeignClient.getTeamInfo(1L)).thenReturn(Result.ok(team(1L, "求真队")));
        when(teamFeignClient.getTeamInfo(2L)).thenReturn(Result.ok(team(2L, "先行队")));
        when(teamFeignClient.getTeamInfo(3L)).thenReturn(Result.ok(team(3L, "远山队")));

        RankingOverviewVO result = rankingService.rebuild(PROBLEM_ID);

        ArgumentCaptor<RankingSnapshot> captor = ArgumentCaptor.forClass(RankingSnapshot.class);
        verify(snapshotMapper).deactivateCurrent(PROBLEM_ID);
        verify(snapshotMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        List<RankingSnapshot> saved = captor.getAllValues();
        assertThat(saved).extracting(RankingSnapshot::getTeamId).containsExactly(2L, 1L, 3L);
        assertThat(saved).extracting(RankingSnapshot::getSubmissionId).containsExactly(201L, 102L, 301L);
        assertThat(saved).extracting(RankingSnapshot::getRankNo).containsExactly(1, 1, 3);
        assertThat(saved).extracting(RankingSnapshot::getCurrentMarker).containsOnly(1);
        assertThat(saved).extracting(RankingSnapshot::getBatchId).containsOnly(saved.get(0).getBatchId());
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems()).extracting(item -> item.getTeamName())
                .containsExactly("先行队", "求真队", "远山队");
    }

    @Test
    void rebuildDoesNotReplaceCurrentRankingWhenDependencyFails() {
        when(submissionFeignClient.listFinalSubmissions(PROBLEM_ID))
                .thenReturn(Result.fail(50000, "unavailable"));

        assertThatThrownBy(() -> rankingService.rebuild(PROBLEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(50901);
        verify(reviewFeignClient, never()).listCompleted(any());
        verify(snapshotMapper, never()).deactivateCurrent(any());
        verify(snapshotMapper, never()).insert(any(RankingSnapshot.class));
    }

    @Test
    void rebuildClearsCurrentRankingWhenNoSubmissionHasCompletedReview() {
        when(submissionFeignClient.listFinalSubmissions(PROBLEM_ID))
                .thenReturn(Result.ok(List.of(submission(101L, 1L, LocalDateTime.now()))));
        when(reviewFeignClient.listCompleted(PROBLEM_ID)).thenReturn(Result.ok(List.of()));

        RankingOverviewVO result = rankingService.rebuild(PROBLEM_ID);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getBatchId()).isNull();
        verify(snapshotMapper).deactivateCurrent(PROBLEM_ID);
        verify(snapshotMapper, never()).insert(any(RankingSnapshot.class));
        verify(teamFeignClient, never()).getTeamInfo(any());
    }

    @Test
    void rebuildRejectsReviewThatBelongsToAnotherTeamBeforeWriting() {
        LocalDateTime now = LocalDateTime.now();
        when(submissionFeignClient.listFinalSubmissions(PROBLEM_ID))
                .thenReturn(Result.ok(List.of(submission(101L, 1L, now))));
        when(reviewFeignClient.listCompleted(PROBLEM_ID))
                .thenReturn(Result.ok(List.of(review(1001L, 101L, 2L, "90", now))));

        assertThatThrownBy(() -> rankingService.rebuild(PROBLEM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40902);
        verify(snapshotMapper, never()).deactivateCurrent(any());
        verify(snapshotMapper, never()).insert(any(RankingSnapshot.class));
    }

    @Test
    void locateReturnsBoundedNearbyRowsAndFailsForAbsentTeam() {
        List<RankingSnapshot> current = List.of(
                snapshot(1L, 1), snapshot(2L, 2), snapshot(3L, 3), snapshot(4L, 4));
        when(snapshotMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<RankingSnapshot>>any()))
                .thenReturn(current);

        TeamRankingContextVO context = rankingService.locate(PROBLEM_ID, 3L, 1);

        assertThat(context.getCurrent().getTeamId()).isEqualTo(3L);
        assertThat(context.getNearby()).extracting(item -> item.getTeamId())
                .containsExactly(2L, 3L, 4L);
        assertThatThrownBy(() -> rankingService.locate(PROBLEM_ID, 99L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40901);
    }

    @Test
    void currentRankingFiltersTeamNameImmediatelyWithoutChangingOriginalRank() {
        RankingSnapshot alpha = snapshot(1L, 1);
        alpha.setTeamName("Alpha 建模队");
        RankingSnapshot beta = snapshot(2L, 2);
        beta.setTeamName("Beta 数据队");
        when(snapshotMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<RankingSnapshot>>any()))
                .thenReturn(List.of(alpha, beta));

        RankingOverviewVO result = rankingService.getCurrent(PROBLEM_ID, "  beta ");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems().get(0).getRank()).isEqualTo(2);
        assertThat(result.getItems().get(0).getTeamName()).isEqualTo("Beta 数据队");
    }

    @Test
    void globalStatsUseAllSubmissionFactsAndLatestCompletedReviewPerSubmission() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 12, 0);
        ReviewSummaryDTO older = review(1001L, 101L, 1L, "70", now.minusMinutes(2));
        ReviewSummaryDTO latest = review(1002L, 101L, 1L, "90", now);
        when(submissionFeignClient.getProblemSubmissionStats()).thenReturn(Result.ok(List.of(
                new ProblemSubmissionStatsDTO(PROBLEM_ID, 8L),
                new ProblemSubmissionStatsDTO(52L, 3L))));
        when(reviewFeignClient.listCompleted(null)).thenReturn(Result.ok(List.of(older, latest)));
        when(snapshotMapper.selectList(org.mockito.ArgumentMatchers.<Wrapper<RankingSnapshot>>any()))
                .thenReturn(List.of(snapshot(1L, 1), snapshot(2L, 2)));
        when(problemFeignClient.getPracticeProblems(any())).thenReturn(Result.ok(List.of(
                new ProblemPracticeDTO(PROBLEM_ID, 1001, "题目 A", 120, 1),
                new ProblemPracticeDTO(52L, 1002, "题目 B", 120, 1))));

        var result = rankingService.getGlobalStats();

        assertThat(result.getTotalSubmissions()).isEqualTo(11L);
        assertThat(result.getReviewedSubmissions()).isEqualTo(1L);
        assertThat(result.getOverallAverageScore()).isEqualByComparingTo("90.00");
        assertThat(result.getItems()).extracting(item -> item.getProblemTitle())
                .containsExactly("题目 A", "题目 B");
        assertThat(result.getItems().get(0).getSubmissionCount()).isEqualTo(8L);
        assertThat(result.getItems().get(0).getAverageScore()).isEqualByComparingTo("90.00");
    }

    private SubmissionSnapshotDTO submission(Long id, Long teamId, LocalDateTime createdAt) {
        return new SubmissionSnapshotDTO(
                id, teamId, PROBLEM_ID, 9L, 1, "paper.pdf", "papers/paper.pdf",
                "SUCCESS", true, createdAt);
    }

    private ReviewSummaryDTO review(Long taskId, Long submissionId, Long teamId,
                                    String score, LocalDateTime finishedAt) {
        return new ReviewSummaryDTO(
                taskId, submissionId, teamId, PROBLEM_ID, "COMPLETED", "BASIC_REVIEW_V1",
                new BigDecimal(score), "{}", "mock-model", "call-1", null, finishedAt);
    }

    private TeamDTO team(Long id, String name) {
        TeamDTO team = new TeamDTO();
        team.setId(id);
        team.setName(name);
        team.setProblemId(PROBLEM_ID);
        return team;
    }

    private RankingSnapshot snapshot(Long teamId, int rank) {
        RankingSnapshot snapshot = new RankingSnapshot();
        snapshot.setProblemId(PROBLEM_ID);
        snapshot.setBatchId("batch-1");
        snapshot.setTeamId(teamId);
        snapshot.setTeamName("Team " + teamId);
        snapshot.setSubmissionId(teamId * 100);
        snapshot.setRankNo(rank);
        snapshot.setScore(BigDecimal.valueOf(100 - rank));
        snapshot.setWorkflowVersion("BASIC_REVIEW_V1");
        snapshot.setSubmittedAt(LocalDateTime.of(2026, 8, 26, 10, rank));
        snapshot.setReviewFinishedAt(LocalDateTime.of(2026, 8, 26, 11, rank));
        snapshot.setComputedAt(LocalDateTime.of(2026, 8, 26, 12, 0));
        snapshot.setCurrentMarker(1);
        return snapshot;
    }
}
