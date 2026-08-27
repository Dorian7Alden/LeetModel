package com.leetmodel.admin.controller;

import com.leetmodel.common.api.dto.AiCallStatsDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {
    @Mock UserFeignClient users;
    @Mock TeamFeignClient teams;
    @Mock ProblemFeignClient problems;
    @Mock SubmissionFeignClient submissions;
    @Mock ReviewFeignClient reviews;
    @Mock SuggestionFeignClient suggestions;
    @Mock RankingFeignClient rankings;
    @Mock AssistantFeignClient assistant;
    @Mock EvaluationFeignClient evaluations;
    @Mock AiGatewayFeignClient aiGateway;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(users, teams, problems, submissions, reviews,
                suggestions, rankings, assistant, evaluations, aiGateway);
    }

    @Test
    void shouldKeepRealZeroDistinctFromUnavailableMetric() {
        when(users.getUserCount()).thenReturn(Result.ok(0L));
        when(teams.getActiveTeamCount()).thenThrow(new IllegalStateException("down"));
        when(problems.getProblemCount()).thenReturn(Result.ok(2L));
        when(submissions.getSubmissionCount()).thenReturn(Result.ok(3L));
        when(reviews.getReviewCount()).thenReturn(Result.ok(4L));
        when(suggestions.getSuggestionCount()).thenReturn(Result.ok(5L));
        when(rankings.getCurrentRankingCount()).thenReturn(Result.ok(6L));
        when(assistant.getConversationCount()).thenReturn(Result.ok(7L));
        when(evaluations.countTasks()).thenReturn(Result.ok(8L));
        when(aiGateway.getCallStats()).thenReturn(Result.ok(new AiCallStatsDTO(9L, 8L, 1L, 100L, 20L)));

        var dashboard = controller.stats().getData();

        assertThat(dashboard.isPartialFailure()).isTrue();
        assertThat(dashboard.getMetrics()).hasSize(10);
        assertThat(dashboard.getMetrics().get("users").isAvailable()).isTrue();
        assertThat(dashboard.getMetrics().get("users").getValue()).isZero();
        assertThat(dashboard.getMetrics().get("teams").isAvailable()).isFalse();
        assertThat(dashboard.getMetrics().get("teams").getValue()).isNull();
        assertThat(dashboard.getMetrics().get("aiCalls").getValue()).isEqualTo(9L);
    }

    @Test
    void failedBusinessResultMustBeVisibleInsteadOfEmptySuccess() {
        when(users.getUserCount()).thenReturn(Result.fail(50001, "用户查询失败"));
        when(teams.getActiveTeamCount()).thenReturn(Result.ok(0L));
        when(problems.getProblemCount()).thenReturn(Result.ok(0L));
        when(submissions.getSubmissionCount()).thenReturn(Result.ok(0L));
        when(reviews.getReviewCount()).thenReturn(Result.ok(0L));
        when(suggestions.getSuggestionCount()).thenReturn(Result.ok(0L));
        when(rankings.getCurrentRankingCount()).thenReturn(Result.ok(0L));
        when(assistant.getConversationCount()).thenReturn(Result.ok(0L));
        when(evaluations.countTasks()).thenReturn(Result.ok(0L));
        when(aiGateway.getCallStats()).thenReturn(Result.ok(new AiCallStatsDTO(0L, 0L, 0L, 0L, 0L)));

        var metric = controller.stats().getData().getMetrics().get("users");

        assertThat(metric.isAvailable()).isFalse();
        assertThat(metric.getMessage()).isEqualTo("用户查询失败");
    }
}
