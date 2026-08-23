package com.leetmodel.team.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.team.dto.AddMemberRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.entity.TeamJoinApplication;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.mapper.TeamMapper;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.mapper.TeamJoinApplicationMapper;
import com.leetmodel.team.service.impl.TeamServiceImpl;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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

    @Mock
    private TeamJoinApplicationMapper applicationMapper;

    @Mock
    private UserFeignClient userFeignClient;

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
        team.setRecruiting(true);
        team.setNeedModeler(false);
        team.setNeedProgrammer(false);
        team.setNeedWriter(false);
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
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.dissolveTeam(1L, 99L));
        assertEquals(TeamErrorCode.NOT_TEAM_LEADER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("解散团队成功")
    void dissolveSuccess() {
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
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

    @Test
    @DisplayName("添加成员成功")
    void addMemberSuccess() {
        when(userFeignClient.isUserAvailable(20L)).thenReturn(Result.ok(true));
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(teamMemberMapper.selectCount(any())).thenReturn(1L);
        when(teamMemberMapper.insert(any(TeamMember.class))).thenReturn(1);

        assertDoesNotThrow(() -> teamService.addMember(1L, new AddMemberRequest(20L), 10L));
        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberMapper).insert((TeamMember) memberCaptor.capture());
        assertEquals(20L, memberCaptor.getValue().getUserId());
        assertEquals("member", memberCaptor.getValue().getRole());
    }

    @Test
    @DisplayName("添加成员失败 —— 用户不可用")
    void addMemberUserUnavailable() {
        when(userFeignClient.isUserAvailable(20L)).thenReturn(Result.ok(false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.addMember(1L, new AddMemberRequest(20L), 10L));

        assertEquals(TeamErrorCode.USER_NOT_AVAILABLE.getCode(), ex.getCode());
        verify(teamMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    @DisplayName("添加成员失败 —— 团队已满")
    void addMemberTeamFull() {
        when(userFeignClient.isUserAvailable(20L)).thenReturn(Result.ok(true));
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(teamMemberMapper.selectCount(any())).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.addMember(1L, new AddMemberRequest(20L), 10L));

        assertEquals(TeamErrorCode.TEAM_FULL.getCode(), ex.getCode());
        verify(teamMemberMapper, never()).insert((TeamMember) any());
    }

    @Test
    @DisplayName("移除成员失败 —— 目标不是团队成员")
    void removeMemberNotInTeam() {
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.removeMember(1L, 20L, 10L));

        assertEquals(TeamErrorCode.NOT_TEAM_MEMBER.getCode(), ex.getCode());
        verify(teamMemberMapper, never()).delete(any());
    }

    @Test
    @DisplayName("已解散团队不能继续更新")
    void updateDisbandedTeam() {
        team.setStatus(0);
        when(teamMapper.selectById(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeam(1L, new TeamUpdateRequest("改名", null), 10L));

        assertEquals(TeamErrorCode.TEAM_ALREADY_DISBANDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("更新成员专业角色成功 —— 支持多选")
    void updateMemberRolesSuccess() {
        TeamMember member = new TeamMember();
        member.setId(100L);
        member.setTeamId(1L);
        member.setUserId(20L);
        member.setRole("member");
        member.setModeler(false);
        member.setProgrammer(false);
        member.setWriter(false);

        when(teamMapper.selectById(1L)).thenReturn(team);
        when(teamMemberMapper.selectOne(any())).thenReturn(member);
        when(teamMemberMapper.updateById(any(TeamMember.class))).thenReturn(1);
        when(userFeignClient.getPublicSummaries(List.of(20L))).thenReturn(Result.ok(List.of(
                new UserPublicSummaryDTO(20L, "申请人", null))));

        MemberRolesUpdateRequest request = new MemberRolesUpdateRequest(true, true, false);
        TeamMemberVO result = teamService.updateMemberRoles(1L, 20L, request, 10L);

        assertTrue(result.getModeler());
        assertTrue(result.getProgrammer());
        assertFalse(result.getWriter());
        verify(teamMemberMapper).updateById(member);
    }

    @Test
    @DisplayName("更新成员专业角色失败 —— 非队长操作")
    void updateMemberRolesNotLeader() {
        when(teamMapper.selectById(1L)).thenReturn(team);

        MemberRolesUpdateRequest request = new MemberRolesUpdateRequest(true, false, false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateMemberRoles(1L, 20L, request, 99L));

        assertEquals(TeamErrorCode.NOT_TEAM_LEADER.getCode(), ex.getCode());
        verify(teamMemberMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("提交入队申请成功")
    void submitApplicationSuccess() {
        when(userFeignClient.isUserAvailable(20L)).thenReturn(Result.ok(true));
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(teamMemberMapper.selectCount(any())).thenReturn(1L);
        when(applicationMapper.selectOne(any())).thenReturn(null);
        when(applicationMapper.insert(any(TeamJoinApplication.class))).thenReturn(1);
        when(userFeignClient.getPublicSummaries(List.of(20L))).thenReturn(Result.ok(List.of(
                new UserPublicSummaryDTO(20L, "申请人", null))));

        JoinApplicationCreateRequest request = new JoinApplicationCreateRequest(true, false, true, "希望加入");
        assertEquals("pending", teamService.submitApplication(1L, request, 20L).getStatus());
    }

    @Test
    @DisplayName("审核通过申请成功")
    void approveApplicationSuccess() {
        TeamJoinApplication application = new TeamJoinApplication();
        application.setId(100L);
        application.setTeamId(1L);
        application.setApplicantId(20L);
        application.setDesiredModeler(true);
        application.setDesiredProgrammer(false);
        application.setDesiredWriter(true);
        application.setStatus("pending");
        application.setPendingMarker(1);

        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(applicationMapper.selectByIdForUpdate(100L)).thenReturn(application);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(teamMemberMapper.selectCount(any())).thenReturn(2L);
        when(teamMemberMapper.insert(any(TeamMember.class))).thenReturn(1);
        when(applicationMapper.updateById(application)).thenReturn(1);
        when(userFeignClient.getPublicSummaries(List.of(20L))).thenReturn(Result.ok(List.of(
                new UserPublicSummaryDTO(20L, "申请人", null))));

        assertEquals("approved", teamService.reviewApplication(
                1L, 100L, new JoinApplicationReviewRequest("approved"), 10L).getStatus());
        verify(teamMemberMapper).insert(any(TeamMember.class));
    }
}
