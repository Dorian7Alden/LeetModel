package com.leetmodel.team.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.team.dto.JoinApplicationPageQuery;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.RecruitmentUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.dto.SubmissionPermissionUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.entity.TeamJoinApplication;
import com.leetmodel.team.entity.TeamRecruitment;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.mapper.TeamMapper;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.mapper.TeamJoinApplicationMapper;
import com.leetmodel.team.mapper.TeamRecruitmentMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

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
    private TeamRecruitmentMapper recruitmentMapper;

    @Mock
    private UserFeignClient userFeignClient;

    @Mock
    private ProblemFeignClient problemFeignClient;

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
        team.setStatus(1);
        team.setProblemId(100L);
        team.setPracticeStatus("PREPARING");
    }

    @Test
    @DisplayName("创建团队成功")
    void createTeamSuccess() {
        when(teamMapper.insert(any(Team.class))).thenReturn(1);
        when(teamMemberMapper.insert(any(TeamMember.class))).thenReturn(1);
        when(problemFeignClient.getPracticeProblem(100L))
                .thenReturn(Result.ok(new ProblemPracticeDTO(100L, "题目", 180, 1)));

        TeamCreateRequest request = new TeamCreateRequest();
        request.setName("新团队");
        request.setDescription("描述");
        request.setProblemId(100L);

        TeamVO vo = teamService.createTeam(request, 10L);

        assertNotNull(vo);
        assertEquals("新团队", vo.getName());
        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);
        verify(teamMapper).insert(teamCaptor.capture());
        assertEquals(3, vo.getMaxMembers());
        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberMapper).insert(memberCaptor.capture());
        assertTrue(memberCaptor.getValue().getCanSubmit());
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
        assertEquals("DISBANDED", team.getPracticeStatus());
    }

    @Test
    @DisplayName("练习中队伍不能解散")
    void dissolveInProgressTeamRejected() {
        team.setPracticeStatus("IN_PROGRESS");
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.dissolveTeam(1L, 10L));

        assertEquals(TeamErrorCode.PRACTICE_ALREADY_STARTED.getCode(), ex.getCode());
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
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        TeamUpdateRequest request = new TeamUpdateRequest();
        request.setName("改名");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeam(1L, request, 99L));
        assertEquals(TeamErrorCode.NOT_TEAM_LEADER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("发布招募失败 —— 至少选择一个职位")
    void publishRecruitmentRequiresRole() {
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class, () -> teamService.publishRecruitment(
                1L, new RecruitmentUpdateRequest(false, false, false), 10L));

        assertEquals(TeamErrorCode.RECRUITMENT_ROLE_REQUIRED.getCode(), ex.getCode());
        verify(recruitmentMapper, never()).insert((TeamRecruitment) any());
    }

    @Test
    @DisplayName("发布招募失败 —— 成员与开放位置达到三人")
    void publishRecruitmentReservesTeamSlot() {
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(teamMemberMapper.selectCount(any())).thenReturn(2L);
        when(recruitmentMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> teamService.publishRecruitment(
                1L, new RecruitmentUpdateRequest(true, false, false), 10L));

        assertEquals(TeamErrorCode.TEAM_SLOT_FULL.getCode(), ex.getCode());
        verify(recruitmentMapper, never()).insert((TeamRecruitment) any());
    }

    @Test
    @DisplayName("移除成员失败 —— 目标不是团队成员")
    void removeMemberNotInTeam() {
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
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
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeam(1L, new TeamUpdateRequest("改名", null), 10L));

        assertEquals(TeamErrorCode.TEAM_ALREADY_DISBANDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("练习开始后不能修改队伍名称")
    void updateNameDuringPracticeRejected() {
        team.setPracticeStatus("IN_PROGRESS");
        team.setDeadlineAt(LocalDateTime.now().plusHours(1));
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateTeam(1L, new TeamUpdateRequest("新名称", "新简介"), 10L));

        assertEquals(TeamErrorCode.TEAM_NAME_LOCKED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("队长可以提前结束练习")
    void endPracticeSuccess() {
        team.setPracticeStatus("IN_PROGRESS");
        team.setDeadlineAt(LocalDateTime.now().plusHours(1));
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(teamMapper.updateById(team)).thenReturn(1);
        when(teamMapper.selectById(1L)).thenReturn(team);

        TeamVO result = teamService.endPractice(1L, 10L);

        assertEquals("ENDED", result.getPracticeStatus());
        assertNotNull(result.getEndedAt());
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

        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
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
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        MemberRolesUpdateRequest request = new MemberRolesUpdateRequest(true, false, false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> teamService.updateMemberRoles(1L, 20L, request, 99L));

        assertEquals(TeamErrorCode.NOT_TEAM_LEADER.getCode(), ex.getCode());
        verify(teamMemberMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("练习中队长可以授予普通成员提交权限")
    void updateSubmissionPermissionDuringPractice() {
        team.setPracticeStatus("IN_PROGRESS");
        team.setDeadlineAt(LocalDateTime.now().plusHours(1));
        TeamMember member = new TeamMember();
        member.setId(100L);
        member.setTeamId(1L);
        member.setUserId(20L);
        member.setRole("member");
        member.setCanSubmit(false);
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(teamMemberMapper.selectOne(any())).thenReturn(member);
        when(teamMemberMapper.updateById(member)).thenReturn(1);
        when(userFeignClient.getPublicSummaries(List.of(20L))).thenReturn(Result.ok(List.of(
                new UserPublicSummaryDTO(20L, "成员", null))));

        TeamMemberVO result = teamService.updateSubmissionPermission(
                1L, 20L, new SubmissionPermissionUpdateRequest(true), 10L);

        assertTrue(result.getCanSubmit());
    }

    @Test
    @DisplayName("提交入队申请成功")
    void submitApplicationSuccess() {
        TeamRecruitment recruitment = recruitment(200L);
        when(userFeignClient.isUserAvailable(20L)).thenReturn(Result.ok(true));
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(recruitmentMapper.selectByIdForUpdate(200L)).thenReturn(recruitment);
        when(recruitmentMapper.selectById(200L)).thenReturn(recruitment);
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(teamMemberMapper.selectCount(any())).thenReturn(1L);
        when(applicationMapper.selectOne(any())).thenReturn(null);
        when(applicationMapper.insert(any(TeamJoinApplication.class))).thenReturn(1);
        when(userFeignClient.getPublicSummaries(List.of(20L))).thenReturn(Result.ok(List.of(
                new UserPublicSummaryDTO(20L, "申请人", null))));

        JoinApplicationCreateRequest request = new JoinApplicationCreateRequest(200L, "希望加入");
        assertEquals("pending", teamService.submitApplication(1L, request, 20L).getStatus());
        verify(teamMapper).selectByIdForUpdate(1L);
    }

    @Test
    @DisplayName("取消申请时按统一顺序锁定队伍和申请")
    void cancelApplicationLocksTeamAndApplication() {
        TeamJoinApplication application = application(100L, 20L);
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(applicationMapper.selectPendingForUpdate(1L, 20L)).thenReturn(application);

        teamService.cancelMyApplication(1L, 20L);

        assertEquals("cancelled", application.getStatus());
        verify(teamMapper).selectByIdForUpdate(1L);
        verify(applicationMapper).selectPendingForUpdate(1L, 20L);
        verify(applicationMapper).updateById(application);
    }

    @Test
    @DisplayName("练习开始后不能继续审核遗留申请")
    void reviewApplicationAfterPracticeRejected() {
        team.setPracticeStatus("IN_PROGRESS");
        team.setDeadlineAt(LocalDateTime.now().plusHours(1));
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);

        BusinessException ex = assertThrows(BusinessException.class, () -> teamService.reviewApplication(
                1L, 100L, new JoinApplicationReviewRequest("rejected"), 10L));

        assertEquals(TeamErrorCode.PRACTICE_ALREADY_STARTED.getCode(), ex.getCode());
        verify(applicationMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("审核通过申请成功")
    void approveApplicationSuccess() {
        TeamJoinApplication application = new TeamJoinApplication();
        application.setId(100L);
        application.setTeamId(1L);
        application.setRecruitmentId(200L);
        application.setApplicantId(20L);
        application.setStatus("pending");
        application.setPendingMarker(1);

        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(applicationMapper.selectByIdForUpdate(100L)).thenReturn(application);
        when(recruitmentMapper.selectByIdForUpdate(200L)).thenReturn(recruitment(200L));
        when(recruitmentMapper.selectById(200L)).thenReturn(recruitment(200L));
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

    @Test
    @DisplayName("审核申请失败 —— 招募位置不属于当前队伍")
    void approveApplicationRejectsForeignRecruitment() {
        TeamJoinApplication application = application(100L, 20L);
        TeamRecruitment recruitment = recruitment(200L);
        recruitment.setTeamId(2L);
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(applicationMapper.selectByIdForUpdate(100L)).thenReturn(application);
        when(recruitmentMapper.selectByIdForUpdate(200L)).thenReturn(recruitment);

        BusinessException ex = assertThrows(BusinessException.class, () -> teamService.reviewApplication(
                1L, 100L, new JoinApplicationReviewRequest("approved"), 10L));

        assertEquals(TeamErrorCode.RECRUITMENT_NOT_FOUND.getCode(), ex.getCode());
        verify(teamMemberMapper, never()).insert(any(TeamMember.class));
    }

    @Test
    @DisplayName("审核申请失败 —— 申请人已参加同题目的其他队伍")
    void approveApplicationRejectsOtherActiveProblemTeam() {
        TeamJoinApplication application = application(100L, 20L);
        when(teamMapper.selectByIdForUpdate(1L)).thenReturn(team);
        when(applicationMapper.selectByIdForUpdate(100L)).thenReturn(application);
        when(recruitmentMapper.selectByIdForUpdate(200L)).thenReturn(recruitment(200L));
        when(teamMemberMapper.selectOne(any())).thenReturn(null);
        when(teamMemberMapper.selectCount(any())).thenReturn(1L);
        when(teamMemberMapper.countActiveProblemTeams(20L, 100L)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> teamService.reviewApplication(
                1L, 100L, new JoinApplicationReviewRequest("approved"), 10L));

        assertEquals(TeamErrorCode.USER_HAS_ACTIVE_PROBLEM_TEAM.getCode(), ex.getCode());
        verify(teamMemberMapper, never()).insert(any(TeamMember.class));
    }

    @Test
    @DisplayName("入队申请按页返回并批量聚合招募信息")
    @SuppressWarnings("unchecked")
    void pageApplications() {
        TeamJoinApplication first = application(100L, 20L);
        TeamJoinApplication second = application(101L, 21L);
        second.setStatus("rejected");
        when(teamMapper.selectById(1L)).thenReturn(team);
        when(applicationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<TeamJoinApplication> page = invocation.getArgument(0);
            page.setRecords(List.of(first, second));
            page.setTotal(8);
            return page;
        });
        when(userFeignClient.getPublicSummaries(List.of(20L, 21L))).thenReturn(Result.ok(List.of(
                new UserPublicSummaryDTO(20L, "申请人甲", null),
                new UserPublicSummaryDTO(21L, "申请人乙", null))));
        when(recruitmentMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(recruitment(200L)));
        JoinApplicationPageQuery query = new JoinApplicationPageQuery();
        query.setPage(2);
        query.setPageSize(2);

        PageResult<com.leetmodel.team.vo.JoinApplicationVO> result =
                teamService.pageApplications(1L, query, 10L);

        assertEquals(8, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(2, result.getRows().size());
        assertTrue(result.getRows().get(0).getNeedModeler());
        verify(recruitmentMapper).selectBatchIds(any(Collection.class));
    }

    private TeamJoinApplication application(Long id, Long applicantId) {
        TeamJoinApplication application = new TeamJoinApplication();
        application.setId(id);
        application.setTeamId(1L);
        application.setRecruitmentId(200L);
        application.setApplicantId(applicantId);
        application.setStatus("pending");
        application.setPendingMarker(1);
        return application;
    }

    private TeamRecruitment recruitment(Long id) {
        TeamRecruitment recruitment = new TeamRecruitment();
        recruitment.setId(id);
        recruitment.setTeamId(1L);
        recruitment.setNeedModeler(true);
        recruitment.setNeedProgrammer(false);
        recruitment.setNeedWriter(true);
        recruitment.setStatus("OPEN");
        return recruitment;
    }
}
