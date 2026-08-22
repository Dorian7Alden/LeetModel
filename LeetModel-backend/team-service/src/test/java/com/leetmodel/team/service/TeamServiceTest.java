package com.leetmodel.team.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.mapper.TeamMapper;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.service.impl.TeamServiceImpl;
import com.leetmodel.team.vo.TeamVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 团队服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamMapper teamMapper;

    @Mock
    private TeamMemberMapper teamMemberMapper;

    @InjectMocks
    private TeamServiceImpl teamService;

    private Team team;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(teamService, "baseMapper", teamMapper);

        team = new Team();
        team.setId(1L);
        team.setName("测试团队");
        team.setDescription("一个测试团队");
        team.setLeaderId(10L);
        team.setMaxMembers(3);
        team.setStatus(1);
    }

    @Test
    @DisplayName("创建团队成功")
    void createTeamSuccess() {
        when(teamMapper.insert(any(Team.class))).thenReturn(1);
        when(teamMemberMapper.insert(any(TeamMember.class))).thenReturn(1);

        TeamCreateRequest request = new TeamCreateRequest();
        request.setName("新团队");
        request.setDescription("描述");

        TeamVO vo = teamService.createTeam(request, 10L);

        assertNotNull(vo);
        assertEquals("新团队", vo.getName());
        verify(teamMapper).insert(any(Team.class));
        verify(teamMemberMapper).insert(any(TeamMember.class));
    }

    @Test
    @DisplayName("解散团队失败 —— 非队长操作")
    void dissolveNotLeader() {
        when(teamMapper.selectById(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.dissolveTeam(1L, 99L));
        assertEquals(TeamErrorCode.NOT_TEAM_LEADER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("解散团队成功")
    void dissolveSuccess() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(teamMapper.updateById(any(Team.class))).thenReturn(1);

        assertDoesNotThrow(() -> teamService.dissolveTeam(1L, 10L));
    }

    @Test
    @DisplayName("查询团队详情失败 —— 不存在")
    void getDetailNotFound() {
        when(teamMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> teamService.getTeamDetail(999L));
    }

    @Test
    @DisplayName("更新团队信息 —— 非队长操作")
    void updateNotLeader() {
        when(teamMapper.selectById(1L)).thenReturn(team);

        TeamUpdateRequest request = new TeamUpdateRequest();
        request.setName("改名");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeam(1L, request, 99L));
        assertEquals(TeamErrorCode.NOT_TEAM_LEADER.getCode(), ex.getCode());
    }
}
