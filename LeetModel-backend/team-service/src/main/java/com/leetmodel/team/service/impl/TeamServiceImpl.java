package com.leetmodel.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.team.dto.AddMemberRequest;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.RecruitmentUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamPublicPageQuery;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamJoinApplication;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.mapper.TeamJoinApplicationMapper;
import com.leetmodel.team.mapper.TeamMapper;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.service.TeamService;
import com.leetmodel.team.vo.JoinApplicationVO;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 团队服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    private static final String ROLE_LEADER = "leader";
    private static final String ROLE_MEMBER = "member";
    private static final String PENDING = "pending";
    private static final String APPROVED = "approved";
    private static final String REJECTED = "rejected";
    private static final String CANCELLED = "cancelled";
    private static final String CLOSED = "closed";
    private static final int DEFAULT_MAX_MEMBERS = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISBANDED = 0;

    private final TeamMemberMapper teamMemberMapper;
    private final TeamJoinApplicationMapper applicationMapper;
    private final UserFeignClient userFeignClient;
    private final ProblemFeignClient problemFeignClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO createTeam(TeamCreateRequest request, Long leaderId) {
        getPracticeProblem(request.getProblemId());
        checkNoActiveProblemTeam(leaderId, request.getProblemId());
        // 创建团队并启用招募
        Team team = new Team();
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setLeaderId(leaderId);
        team.setProblemId(request.getProblemId());
        team.setMaxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : DEFAULT_MAX_MEMBERS);
        team.setStatus(STATUS_ACTIVE);
        team.setPracticeStatus("PREPARING");
        team.setRecruiting(true);
        team.setNeedModeler(false);
        team.setNeedProgrammer(false);
        team.setNeedWriter(false);
        save(team);

        // 创建者成为队长
        TeamMember leader = buildMember(team.getId(), leaderId, ROLE_LEADER, false, false, false);
        teamMemberMapper.insert(leader);
        return assembleTeamVO(team, List.of(leader), leaderId, Map.of(), Set.of());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TeamVO> pagePublicTeams(TeamPublicPageQuery query, Long currentUserId) {
        // 组合公共查询条件
        QueryWrapper<Team> wrapper = new QueryWrapper<>();
        wrapper.eq("status", STATUS_ACTIVE).eq("deleted", 0);
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(value -> value.like("name", query.getKeyword())
                    .or().like("description", query.getKeyword()));
        }
        if (Boolean.TRUE.equals(query.getRecruitingOnly())) wrapper.eq("recruiting", 1);
        if (Boolean.TRUE.equals(query.getAvailableOnly())) {
            wrapper.apply("(SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = team.id) < max_members");
        }
        addRecruitmentRoleFilter(wrapper, query);
        if ("remainingSlots".equals(query.getSortBy())) {
            wrapper.orderByDesc("(max_members - (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = team.id))");
        } else {
            wrapper.orderByDesc("create_time");
        }

        // 分页查询并批量组装
        Page<Team> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<Team> teamPage = baseMapper.selectPage(page, wrapper);
        List<TeamVO> rows = assembleTeams(teamPage.getRecords(), currentUserId);
        return new PageResult<>(teamPage.getTotal(), query.getPage(), query.getPageSize(), rows);
    }

    /** {@inheritDoc} */
    @Override
    public TeamVO getTeamDetail(Long teamId, Long currentUserId) {
        Team team = getRequiredTeam(teamId);
        List<TeamMember> members = getMembersByTeamId(teamId);
        if (team.getStatus() == STATUS_DISBANDED) {
            BusinessException.throwIf(!containsUser(members, currentUserId), TeamErrorCode.NOT_TEAM_MEMBER);
        }
        Map<Long, UserPublicSummaryDTO> summaries = getUserSummaries(memberUserIds(members));
        Set<Long> pendingTeamIds = getPendingTeamIds(currentUserId, List.of(teamId));
        return assembleTeamVO(team, members, currentUserId, summaries, pendingTeamIds);
    }

    /** {@inheritDoc} */
    @Override
    public TeamVO getTeamDetail(Long teamId) {
        Team team = getRequiredTeam(teamId);
        List<TeamMember> members = getMembersByTeamId(teamId);
        return assembleTeamVO(team, members, null, getUserSummaries(memberUserIds(members)), Set.of());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId) {
        Team team = getRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        if (request.getName() != null && !request.getName().isBlank()) team.setName(request.getName());
        if (request.getDescription() != null) team.setDescription(request.getDescription());
        updateById(team);
        return getTeamDetail(teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO updateRecruitment(Long teamId, RecruitmentUpdateRequest request, Long operatorId) {
        Team team = getRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        team.setRecruiting(request.getRecruiting());
        team.setNeedModeler(request.getNeedModeler());
        team.setNeedProgrammer(request.getNeedProgrammer());
        team.setNeedWriter(request.getNeedWriter());
        updateById(team);
        return getTeamDetail(teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void dissolveTeam(Long teamId, Long operatorId) {
        // 锁定团队，防止与申请审核并发
        Team team = baseMapper.selectByIdForUpdate(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        checkLeader(team, operatorId);

        // 解散并关闭待处理申请
        team.setStatus(STATUS_DISBANDED);
        team.setRecruiting(false);
        updateById(team);
        closePendingApplications(teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    public List<TeamVO> listMyTeams(Long userId, Integer status) {
        BusinessException.throwIf(status != null && status != STATUS_DISBANDED && status != STATUS_ACTIVE,
                TeamErrorCode.INVALID_TEAM_STATUS);
        List<Long> teamIds = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId))
                .stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) return List.of();
        LambdaQueryWrapper<Team> wrapper = new LambdaQueryWrapper<Team>()
                .in(Team::getId, teamIds)
                .eq(status != null, Team::getStatus, status)
                .orderByDesc(Team::getCreateTime);
        return assembleTeams(baseMapper.selectList(wrapper), userId);
    }

    /** {@inheritDoc} */
    @Override
    public List<TeamVO> listMyTeams(Long userId) {
        return listMyTeams(userId, null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void addMember(Long teamId, AddMemberRequest request, Long operatorId) {
        validateUserAvailable(request.getUserId());
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        checkNoActiveProblemTeam(request.getUserId(), team.getProblemId());
        validateCanAddMember(team, request.getUserId());
        teamMemberMapper.insert(buildMember(teamId, request.getUserId(), ROLE_MEMBER, false, false, false));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void removeMember(Long teamId, Long memberId, Long operatorId) {
        Team team = getRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        BusinessException.throwIf(team.getLeaderId().equals(memberId), TeamErrorCode.CANNOT_REMOVE_LEADER);
        checkUserInTeam(teamId, memberId);
        removeTeamMember(teamId, memberId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamMemberVO updateMemberRoles(Long teamId, Long memberId,
                                          MemberRolesUpdateRequest request, Long operatorId) {
        Team team = getRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        TeamMember member = getTeamMember(teamId, memberId);
        BusinessException.throwIf(member == null, TeamErrorCode.NOT_TEAM_MEMBER);
        member.setModeler(request.getModeler());
        member.setProgrammer(request.getProgrammer());
        member.setWriter(request.getWriter());
        teamMemberMapper.updateById(member);
        return toMemberVO(member, getUserSummaries(List.of(memberId)).get(memberId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        Team team = getRequiredActiveTeam(teamId);
        checkPracticePreparing(team);
        BusinessException.throwIf(team.getLeaderId().equals(userId), TeamErrorCode.LEADER_CANNOT_LEAVE);
        checkUserInTeam(teamId, userId);
        removeTeamMember(teamId, userId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public JoinApplicationVO submitApplication(Long teamId, JoinApplicationCreateRequest request,
                                               Long applicantId) {
        validateUserAvailable(applicantId);
        Team team = getRequiredActiveTeam(teamId);
        validateApplicationAllowed(team, applicantId);

        TeamJoinApplication application = new TeamJoinApplication();
        application.setTeamId(teamId);
        application.setApplicantId(applicantId);
        application.setDesiredModeler(request.getDesiredModeler());
        application.setDesiredProgrammer(request.getDesiredProgrammer());
        application.setDesiredWriter(request.getDesiredWriter());
        application.setMessage(request.getMessage());
        application.setStatus(PENDING);
        application.setPendingMarker(1);
        application.setCreateTime(LocalDateTime.now());
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TeamErrorCode.APPLICATION_ALREADY_PENDING);
        }
        return toApplicationVO(application, getUserSummaries(List.of(applicantId)).get(applicantId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void cancelMyApplication(Long teamId, Long applicantId) {
        TeamJoinApplication application = getPendingApplication(teamId, applicantId);
        BusinessException.throwIf(application == null, TeamErrorCode.APPLICATION_NOT_FOUND);
        finishApplication(application, CANCELLED, applicantId);
    }

    /** {@inheritDoc} */
    @Override
    public List<JoinApplicationVO> listApplications(Long teamId, Long operatorId) {
        Team team = getRequiredTeam(teamId);
        checkLeader(team, operatorId);
        List<TeamJoinApplication> applications = applicationMapper.selectList(
                new LambdaQueryWrapper<TeamJoinApplication>()
                        .eq(TeamJoinApplication::getTeamId, teamId)
                        .orderByAsc(TeamJoinApplication::getStatus)
                        .orderByDesc(TeamJoinApplication::getCreateTime));
        Map<Long, UserPublicSummaryDTO> summaries = getUserSummaries(applicationUserIds(applications));
        List<JoinApplicationVO> result = new ArrayList<>();
        for (TeamJoinApplication application : applications) {
            result.add(toApplicationVO(application, summaries.get(application.getApplicantId())));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public JoinApplicationVO reviewApplication(Long teamId, Long applicationId,
                                               JoinApplicationReviewRequest request, Long operatorId) {
        // 锁定团队和申请，串行化审核与容量检查
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        TeamJoinApplication application = applicationMapper.selectByIdForUpdate(applicationId);
        BusinessException.throwIf(application == null || !teamId.equals(application.getTeamId()),
                TeamErrorCode.APPLICATION_NOT_FOUND);
        BusinessException.throwIf(!PENDING.equals(application.getStatus()),
                TeamErrorCode.APPLICATION_ALREADY_HANDLED);

        // 按审核决定流转
        if (APPROVED.equals(request.getDecision())) {
            validateApplicationApproval(team, application.getApplicantId());
            teamMemberMapper.insert(buildMember(teamId, application.getApplicantId(), ROLE_MEMBER,
                    application.getDesiredModeler(), application.getDesiredProgrammer(), application.getDesiredWriter()));
            finishApplication(application, APPROVED, operatorId);
        } else {
            BusinessException.throwIf(!REJECTED.equals(request.getDecision()), TeamErrorCode.INVALID_APPLICATION_DECISION);
            finishApplication(application, REJECTED, operatorId);
        }
        return toApplicationVO(application,
                getUserSummaries(List.of(application.getApplicantId())).get(application.getApplicantId()));
    }

    @Override
    @Transactional
    public TeamVO startPractice(Long teamId, Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        List<TeamMember> members = getMembersByTeamId(teamId);
        boolean modeler = members.stream().anyMatch(member -> Boolean.TRUE.equals(member.getModeler()));
        boolean programmer = members.stream().anyMatch(member -> Boolean.TRUE.equals(member.getProgrammer()));
        boolean writer = members.stream().anyMatch(member -> Boolean.TRUE.equals(member.getWriter()));
        BusinessException.throwIf(!modeler || !programmer || !writer, TeamErrorCode.ROLES_NOT_COVERED);
        ProblemPracticeDTO problem = getPracticeProblem(team.getProblemId());
        LocalDateTime now = LocalDateTime.now();
        team.setPracticeStatus("IN_PROGRESS");
        team.setStartedAt(now);
        team.setDeadlineAt(now.plusMinutes(problem.getDurationMinutes()));
        team.setRecruiting(false);
        updateById(team);
        closePendingApplications(teamId, operatorId);
        return getTeamDetail(teamId, operatorId);
    }

    // ==================== 查询与组装 ====================

    private List<TeamVO> assembleTeams(List<Team> teams, Long currentUserId) {
        if (teams.isEmpty()) return List.of();
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, List<TeamMember>> memberMap = getMembersByTeamIds(teamIds);
        Set<Long> userIds = new LinkedHashSet<>();
        for (List<TeamMember> members : memberMap.values()) userIds.addAll(memberUserIds(members));
        Map<Long, UserPublicSummaryDTO> summaries = getUserSummaries(new ArrayList<>(userIds));
        Set<Long> pendingTeamIds = getPendingTeamIds(currentUserId, teamIds);
        List<TeamVO> result = new ArrayList<>();
        for (Team team : teams) {
            result.add(assembleTeamVO(team, memberMap.getOrDefault(team.getId(), List.of()),
                    currentUserId, summaries, pendingTeamIds));
        }
        return result;
    }

    private TeamVO assembleTeamVO(Team team, List<TeamMember> members, Long currentUserId,
                                  Map<Long, UserPublicSummaryDTO> summaries, Set<Long> pendingTeamIds) {
        List<TeamMemberVO> memberVOs = new ArrayList<>();
        for (TeamMember member : members) {
            memberVOs.add(toMemberVO(member, summaries.get(member.getUserId())));
        }
        String relation = relationOf(team, members, currentUserId, pendingTeamIds);
        int memberCount = members.size();
        boolean active = team.getStatus() == STATUS_ACTIVE;
        return TeamVO.builder()
                .id(team.getId()).name(team.getName()).description(team.getDescription())
                .leaderId(team.getLeaderId()).problemId(team.getProblemId())
                .maxMembers(team.getMaxMembers()).status(team.getStatus())
                .practiceStatus(team.getPracticeStatus()).startedAt(team.getStartedAt())
                .deadlineAt(team.getDeadlineAt()).endedAt(team.getEndedAt())
                .recruiting(team.getRecruiting()).needModeler(team.getNeedModeler())
                .needProgrammer(team.getNeedProgrammer()).needWriter(team.getNeedWriter())
                .memberCount(memberCount).remainingSlots(Math.max(0, team.getMaxMembers() - memberCount))
                .currentUserRelation(relation)
                .canApply(active && Boolean.TRUE.equals(team.getRecruiting()) && memberCount < team.getMaxMembers()
                        && "none".equals(relation))
                .canManage("leader".equals(relation)).canLeave(active && "member".equals(relation))
                .createTime(team.getCreateTime()).members(memberVOs).build();
    }

    private TeamMemberVO toMemberVO(TeamMember member, UserPublicSummaryDTO summary) {
        return TeamMemberVO.builder().id(member.getId()).userId(member.getUserId())
                .nickname(summary != null ? summary.getNickname() : null)
                .avatarUrl(summary != null ? summary.getAvatarUrl() : null)
                .role(member.getRole()).modeler(member.getModeler()).programmer(member.getProgrammer())
                .writer(member.getWriter()).joinedAt(member.getJoinedAt()).build();
    }

    private JoinApplicationVO toApplicationVO(TeamJoinApplication application, UserPublicSummaryDTO summary) {
        return JoinApplicationVO.builder().id(application.getId()).teamId(application.getTeamId())
                .applicantId(application.getApplicantId())
                .nickname(summary != null ? summary.getNickname() : null)
                .avatarUrl(summary != null ? summary.getAvatarUrl() : null)
                .desiredModeler(application.getDesiredModeler())
                .desiredProgrammer(application.getDesiredProgrammer()).desiredWriter(application.getDesiredWriter())
                .message(application.getMessage()).status(application.getStatus())
                .handledBy(application.getHandledBy()).handledAt(application.getHandledAt())
                .createTime(application.getCreateTime()).build();
    }

    // ==================== 业务校验 ====================

    private void validateApplicationAllowed(Team team, Long applicantId) {
        BusinessException.throwIf(!Boolean.TRUE.equals(team.getRecruiting()), TeamErrorCode.TEAM_NOT_RECRUITING);
        BusinessException.throwIf(team.getLeaderId().equals(applicantId), TeamErrorCode.CANNOT_APPLY_OWN_TEAM);
        BusinessException.throwIf(getTeamMember(team.getId(), applicantId) != null, TeamErrorCode.USER_ALREADY_IN_TEAM);
        BusinessException.throwIf(getMemberCount(team.getId()) >= team.getMaxMembers(), TeamErrorCode.TEAM_FULL);
        BusinessException.throwIf(getPendingApplication(team.getId(), applicantId) != null,
                TeamErrorCode.APPLICATION_ALREADY_PENDING);
    }

    private void validateApplicationApproval(Team team, Long applicantId) {
        BusinessException.throwIf(!Boolean.TRUE.equals(team.getRecruiting()), TeamErrorCode.TEAM_NOT_RECRUITING);
        validateCanAddMember(team, applicantId);
    }

    private void validateCanAddMember(Team team, Long userId) {
        BusinessException.throwIf(getTeamMember(team.getId(), userId) != null, TeamErrorCode.USER_ALREADY_IN_TEAM);
        BusinessException.throwIf(getMemberCount(team.getId()) >= team.getMaxMembers(), TeamErrorCode.TEAM_FULL);
    }

    private void checkPracticePreparing(Team team) {
        BusinessException.throwIf(!"PREPARING".equals(team.getPracticeStatus()),
                TeamErrorCode.PRACTICE_ALREADY_STARTED);
    }

    private void checkNoActiveProblemTeam(Long userId, Long problemId) {
        BusinessException.throwIf(teamMemberMapper.countActiveProblemTeams(userId, problemId) > 0,
                TeamErrorCode.USER_HAS_ACTIVE_PROBLEM_TEAM);
    }

    private ProblemPracticeDTO getPracticeProblem(Long problemId) {
        Result<ProblemPracticeDTO> result = problemFeignClient.getPracticeProblem(problemId);
        BusinessException.throwIf(result == null || !result.isSuccess() || result.getData() == null,
                TeamErrorCode.PROBLEM_NOT_AVAILABLE);
        return result.getData();
    }

    private void checkLeader(Team team, Long operatorId) {
        BusinessException.throwIf(!team.getLeaderId().equals(operatorId), TeamErrorCode.NOT_TEAM_LEADER);
    }

    private void checkTeamActive(Team team) {
        BusinessException.throwIf(team.getStatus() == STATUS_DISBANDED, TeamErrorCode.TEAM_ALREADY_DISBANDED);
    }

    private void checkUserInTeam(Long teamId, Long userId) {
        BusinessException.throwIf(getTeamMember(teamId, userId) == null, TeamErrorCode.NOT_TEAM_MEMBER);
    }

    private void validateUserAvailable(Long userId) {
        Result<Boolean> result = userFeignClient.isUserAvailable(userId);
        BusinessException.throwIf(result == null || !result.isSuccess(), ErrorCodeEnum.SYSTEM_ERROR);
        BusinessException.throwIf(!Boolean.TRUE.equals(result.getData()), TeamErrorCode.USER_NOT_AVAILABLE);
    }

    // ==================== 数据访问 ====================

    private Team getRequiredTeam(Long teamId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        return team;
    }

    private Team getRequiredActiveTeam(Long teamId) {
        Team team = getRequiredTeam(teamId);
        checkTeamActive(team);
        return team;
    }

    private Team lockRequiredActiveTeam(Long teamId) {
        Team team = baseMapper.selectByIdForUpdate(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        return team;
    }

    private TeamMember getTeamMember(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
    }

    private TeamJoinApplication getPendingApplication(Long teamId, Long applicantId) {
        return applicationMapper.selectOne(new LambdaQueryWrapper<TeamJoinApplication>()
                .eq(TeamJoinApplication::getTeamId, teamId)
                .eq(TeamJoinApplication::getApplicantId, applicantId)
                .eq(TeamJoinApplication::getPendingMarker, 1));
    }

    private long getMemberCount(Long teamId) {
        return teamMemberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId));
    }

    private void removeTeamMember(Long teamId, Long userId) {
        teamMemberMapper.delete(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId));
    }

    private List<TeamMember> getMembersByTeamId(Long teamId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId).orderByAsc(TeamMember::getJoinedAt));
    }

    private Map<Long, List<TeamMember>> getMembersByTeamIds(List<Long> teamIds) {
        List<TeamMember> members = teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .in(TeamMember::getTeamId, teamIds).orderByAsc(TeamMember::getJoinedAt));
        Map<Long, List<TeamMember>> result = new HashMap<>();
        for (TeamMember member : members) {
            result.computeIfAbsent(member.getTeamId(), key -> new ArrayList<>()).add(member);
        }
        return result;
    }

    private Map<Long, UserPublicSummaryDTO> getUserSummaries(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        Result<List<UserPublicSummaryDTO>> result = userFeignClient.getPublicSummaries(distinctIds);
        BusinessException.throwIf(result == null || !result.isSuccess() || result.getData() == null,
                ErrorCodeEnum.SYSTEM_ERROR);
        Map<Long, UserPublicSummaryDTO> summaries = new HashMap<>();
        for (UserPublicSummaryDTO summary : result.getData()) summaries.put(summary.getUserId(), summary);
        BusinessException.throwIf(summaries.size() != distinctIds.size(), ErrorCodeEnum.SYSTEM_ERROR);
        return summaries;
    }

    private Set<Long> getPendingTeamIds(Long userId, List<Long> teamIds) {
        if (userId == null || teamIds.isEmpty()) return Set.of();
        List<TeamJoinApplication> applications = applicationMapper.selectList(
                new LambdaQueryWrapper<TeamJoinApplication>()
                        .eq(TeamJoinApplication::getApplicantId, userId)
                        .eq(TeamJoinApplication::getPendingMarker, 1)
                        .in(TeamJoinApplication::getTeamId, teamIds));
        Set<Long> result = new LinkedHashSet<>();
        for (TeamJoinApplication application : applications) result.add(application.getTeamId());
        return result;
    }

    private void finishApplication(TeamJoinApplication application, String status, Long handlerId) {
        application.setStatus(status);
        application.setPendingMarker(null);
        application.setHandledBy(handlerId);
        application.setHandledAt(LocalDateTime.now());
        applicationMapper.updateById(application);
    }

    private void closePendingApplications(Long teamId, Long operatorId) {
        applicationMapper.update(null, new UpdateWrapper<TeamJoinApplication>()
                .eq("team_id", teamId)
                .eq("pending_marker", 1)
                .set("status", CLOSED)
                .set("pending_marker", null)
                .set("handled_by", operatorId)
                .set("handled_at", LocalDateTime.now()));
    }

    // ==================== 小型转换方法 ====================

    private TeamMember buildMember(Long teamId, Long userId, String role,
                                   boolean modeler, boolean programmer, boolean writer) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId); member.setUserId(userId); member.setRole(role);
        member.setModeler(modeler); member.setProgrammer(programmer); member.setWriter(writer);
        member.setJoinedAt(LocalDateTime.now());
        return member;
    }

    private String relationOf(Team team, List<TeamMember> members, Long userId, Set<Long> pendingTeamIds) {
        if (userId == null) return "none";
        if (team.getLeaderId().equals(userId)) return "leader";
        if (containsUser(members, userId)) return "member";
        if (pendingTeamIds.contains(team.getId())) return PENDING;
        return "none";
    }

    private boolean containsUser(List<TeamMember> members, Long userId) {
        if (userId == null) return false;
        for (TeamMember member : members) if (userId.equals(member.getUserId())) return true;
        return false;
    }

    private List<Long> memberUserIds(List<TeamMember> members) {
        return members.stream().map(TeamMember::getUserId).toList();
    }

    private List<Long> applicationUserIds(List<TeamJoinApplication> applications) {
        return applications.stream().map(TeamJoinApplication::getApplicantId).distinct().toList();
    }

    private void addRecruitmentRoleFilter(QueryWrapper<Team> wrapper, TeamPublicPageQuery query) {
        boolean modeler = Boolean.TRUE.equals(query.getNeedModeler());
        boolean programmer = Boolean.TRUE.equals(query.getNeedProgrammer());
        boolean writer = Boolean.TRUE.equals(query.getNeedWriter());
        if (!modeler && !programmer && !writer) return;
        wrapper.and(value -> {
            boolean hasPrevious = false;
            if (modeler) {
                value.eq("need_modeler", 1);
                hasPrevious = true;
            }
            if (programmer) {
                if (hasPrevious) value.or();
                value.eq("need_programmer", 1);
                hasPrevious = true;
            }
            if (writer) {
                if (hasPrevious) value.or();
                value.eq("need_writer", 1);
            }
        });
    }
}
