package com.leetmodel.assistant.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.assistant.workflow.AssistantProductionSnapshot;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainToolsTest {

    private TeamFeignClient teamFeignClient;
    private SubmissionFeignClient submissionFeignClient;
    private ObjectMapper objectMapper;
    private QueryUserTeamTool userTeamTool;
    private QuerySubmissionStatusTool submissionStatusTool;

    @BeforeEach
    void setUp() {
        teamFeignClient = mock(TeamFeignClient.class);
        submissionFeignClient = mock(SubmissionFeignClient.class);
        objectMapper = new ObjectMapper();
        userTeamTool = new QueryUserTeamTool(teamFeignClient, objectMapper);
        submissionStatusTool = new QuerySubmissionStatusTool(submissionFeignClient, teamFeignClient, objectMapper);
    }

    @Test
    void queryUserTeamReturnsTeamFactWhenInTeam() {
        when(teamFeignClient.getUserCurrentTeam(100L)).thenReturn(Result.ok(new TeamDTO(
                1024L, "极值探索队", 100L, 1, 3, 51001L, "IN_PROGRESS",
                LocalDateTime.now(), LocalDateTime.now().plusDays(3), null)));

        AssistantToolOutput output = userTeamTool.execute(
                new QueryUserTeamTool.Input(),
                context(100L));

        assertThat(output.modelResultJson()).contains("1024", "极值探索队", "IN_PROGRESS");
        assertThat(output.modelResultJson()).contains("\"inTeam\":true");
    }

    @Test
    void queryUserTeamReturnsNotInTeamWhenNoTeam() {
        when(teamFeignClient.getUserCurrentTeam(100L)).thenReturn(Result.ok(null));

        AssistantToolOutput output = userTeamTool.execute(
                new QueryUserTeamTool.Input(),
                context(100L));

        assertThat(output.modelResultJson()).contains("\"inTeam\":false");
    }

    @Test
    void querySubmissionStatusReturnsLatestSubmissionForCurrentTeam() {
        when(teamFeignClient.getUserCurrentTeam(100L)).thenReturn(Result.ok(new TeamDTO(
                1024L, "极值探索队", 100L, 1, 3, 51001L, "IN_PROGRESS", null, null, null)));
        when(submissionFeignClient.getLatestTeamSubmission(1024L)).thenReturn(Result.ok(new SubmissionSnapshotDTO(
                2048L, 1024L, 51001L, 100L, 2, "solution.pdf", "obj_key", "SCORED", true, LocalDateTime.now())));

        AssistantToolOutput output = submissionStatusTool.execute(
                new QuerySubmissionStatusTool.Input(null, null),
                context(100L));

        assertThat(output.modelResultJson()).contains("2048", "solution.pdf", "SCORED");
        assertThat(output.modelResultJson()).contains("\"hasSubmission\":true");
    }

    private AssistantToolExecutionContext context(Long userId) {
        AssistantProductionSnapshot snapshot = new AssistantProductionSnapshot(
                "CFG", 1, "ASSISTANT_TOOLS_NO_RAG_V1", "PROMPT", "MODEL", "TOOLSET", "NONE", null);
        return new AssistantToolExecutionContext(userId, 10L, 1L, 2L,
                1, 1, "TOOLSET", snapshot, Instant.now().plusSeconds(60));
    }
}
