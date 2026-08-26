package com.leetmodel.team.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalTeamControllerTest {

    @Mock TeamService teamService;
    @Mock TeamMemberMapper memberMapper;

    @Test
    void listRecentIncludesActualMemberCountAndLifecycle() {
        Team team = new Team();
        team.setId(9007199254740993L);
        team.setName("运行中队伍");
        team.setLeaderId(10L);
        team.setStatus(1);
        team.setProblemId(20L);
        team.setPracticeStatus("IN_PROGRESS");
        when(teamService.list(any(Wrapper.class))).thenReturn(List.of(team));
        when(memberMapper.selectCount(any())).thenReturn(3L);

        var result = new InternalTeamController(teamService, memberMapper).listRecent(20);

        assertEquals(1, result.getData().size());
        assertEquals(9007199254740993L, result.getData().get(0).getId());
        assertEquals(3, result.getData().get(0).getMemberCount());
        assertEquals("IN_PROGRESS", result.getData().get(0).getPracticeStatus());
    }
}
