package com.leetmodel.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationPageQuery;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.MyTeamPageQuery;
import com.leetmodel.team.dto.RecruitmentUpdateRequest;
import com.leetmodel.team.dto.SubmissionPermissionUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamPublicPageQuery;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamJoinApplication;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.entity.TeamRecruitment;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.mapper.TeamJoinApplicationMapper;
import com.leetmodel.team.mapper.TeamMapper;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.mapper.TeamRecruitmentMapper;
import com.leetmodel.team.service.TeamService;
import com.leetmodel.team.vo.JoinApplicationVO;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamRecruitmentVO;
import com.leetmodel.team.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final String RECRUITMENT_OPEN = "OPEN";
    private static final String RECRUITMENT_FILLED = "FILLED";
    private static final String RECRUITMENT_CLOSED = "CLOSED";
    private static final int DEFAULT_MAX_MEMBERS = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISBANDED = 0;

    private final TeamMemberMapper teamMemberMapper;
    private final TeamJoinApplicationMapper applicationMapper;
    private final TeamRecruitmentMapper recruitmentMapper;
    private final UserFeignClient userFeignClient;
    private final ProblemFeignClient problemFeignClient;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO createTeam(TeamCreateRequest request, Long leaderId) {
        ProblemPracticeDTO problem = getPracticeProblem(request.getProblemId());
        checkNoActiveProblemTeam(leaderId, request.getProblemId());
        // 创建团队；招募位置由队长按需发布。
        Team team = new Team();
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setLeaderId(leaderId);
        team.setProblemId(request.getProblemId());
        team.setStatus(STATUS_ACTIVE);
        team.setPracticeStatus("PREPARING");
        save(team);

        // 创建者成为队长
        TeamMember leader = buildMember(team.getId(), leaderId, ROLE_LEADER, false, false, false);
        teamMemberMapper.insert(leader);
        TeamVO result = assembleTeamVO(team, List.of(leader), leaderId, Map.of(), Set.of());
        result.setProblemTitle(problem.getTitle());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<TeamVO> pagePublicTeams(TeamPublicPageQuery query, Long currentUserId) {
        // 组合公共查询条件
        QueryWrapper<Team> wrapper = new QueryWrapper<>();
        wrapper.eq("status", STATUS_ACTIVE)
                .eq("practice_status", "PREPARING")
                .eq("deleted", 0)
                .apply("(SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = team.id) < {0}",
                        DEFAULT_MAX_MEMBERS);
        if (query.getProblemId() != null) wrapper.eq("problem_id", query.getProblemId());
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(value -> value.like("name", query.getKeyword())
                    .or().like("description", query.getKeyword()));
        }
        if (Boolean.TRUE.equals(query.getRecruitingOnly())) {
            wrapper.exists("SELECT 1 FROM team_recruitment tr WHERE tr.team_id = team.id AND tr.status = 'OPEN'");
        }
        if (Boolean.TRUE.equals(query.getExcludeJoined()) && currentUserId != null) {
            wrapper.notExists("SELECT 1 FROM team_member joined_tm WHERE joined_tm.team_id = team.id AND joined_tm.user_id = {0}",
                    currentUserId);
        }
        if (Boolean.TRUE.equals(query.getAvailableOnly())) {
            wrapper.apply("(SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = team.id) < {0}",
                    DEFAULT_MAX_MEMBERS);
        }
        addRecruitmentRoleFilter(wrapper, query);
        if ("remainingSlots".equals(query.getSortBy())) {
            wrapper.orderByDesc("(3 - (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = team.id))")
                    .orderByDesc("create_time").orderByDesc("id");
        } else {
            wrapper.orderByDesc("create_time").orderByDesc("id");
        }

        // 分页查询并批量组装
        Page<Team> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<Team> teamPage = baseMapper.selectPage(page, wrapper);
        List<TeamVO> rows = assembleTeams(teamPage.getRecords(), currentUserId);
        return new PageResult<>(teamPage.getTotal(), query.getPage(), query.getPageSize(), rows);
    }

    /** {@inheritDoc} */
    @Override
    public List<Long> listPublicPreparingProblemIds() {
        QueryWrapper<Team> wrapper = new QueryWrapper<>();
        wrapper.select("problem_id")
                .eq("status", STATUS_ACTIVE)
                .eq("practice_status", "PREPARING")
                .eq("deleted", 0)
                .apply("(SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = team.id) < {0}",
                        DEFAULT_MAX_MEMBERS)
                .groupBy("problem_id")
                .orderByDesc("MAX(create_time)");
        return baseMapper.selectObjs(wrapper).stream().map(value -> ((Number) value).longValue()).toList();
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
        TeamVO result = assembleTeamVO(team, members, currentUserId, summaries, pendingTeamIds);
        ProblemPracticeDTO practice = getPracticeProblem(team.getProblemId());
        result.setProblemTitle(practice.getTitle());
        result.setProblemCode(practice.getCode());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public TeamVO getTeamDetail(Long teamId) {
        Team team = getRequiredTeam(teamId);
        List<TeamMember> members = getMembersByTeamId(teamId);
        TeamVO result = assembleTeamVO(team, members, null, getUserSummaries(memberUserIds(members)), Set.of());
        ProblemPracticeDTO practice = getPracticeProblem(team.getProblemId());
        result.setProblemTitle(practice.getTitle());
        result.setProblemCode(practice.getCode());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        refreshExpiredPractice(team);
        BusinessException.throwIf(!"PREPARING".equals(team.getPracticeStatus())
                        && request.getName() != null && !request.getName().equals(team.getName()),
                TeamErrorCode.TEAM_NAME_LOCKED);
        BusinessException.throwIf(!"PREPARING".equals(team.getPracticeStatus())
                        && !"IN_PROGRESS".equals(team.getPracticeStatus()),
                TeamErrorCode.TEAM_OPERATION_NOT_ALLOWED);
        if ("PREPARING".equals(team.getPracticeStatus())
                && request.getName() != null && !request.getName().isBlank()) team.setName(request.getName());
        if (request.getDescription() != null) team.setDescription(request.getDescription());
        updateById(team);
        return getTeamDetail(teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO publishRecruitment(Long teamId, RecruitmentUpdateRequest request, Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        BusinessException.throwIf(!Boolean.TRUE.equals(request.getNeedModeler())
                        && !Boolean.TRUE.equals(request.getNeedProgrammer())
                        && !Boolean.TRUE.equals(request.getNeedWriter()),
                TeamErrorCode.RECRUITMENT_ROLE_REQUIRED);
        BusinessException.throwIf(getMemberCount(teamId) + getOpenRecruitmentCount(teamId) >= DEFAULT_MAX_MEMBERS,
                TeamErrorCode.TEAM_SLOT_FULL);
        TeamRecruitment recruitment = new TeamRecruitment();
        recruitment.setTeamId(teamId);
        recruitment.setNeedModeler(request.getNeedModeler());
        recruitment.setNeedProgrammer(request.getNeedProgrammer());
        recruitment.setNeedWriter(request.getNeedWriter());
        recruitment.setDescription(request.getDescription());
        recruitment.setStatus(RECRUITMENT_OPEN);
        recruitment.setCreateTime(LocalDateTime.now());
        recruitment.setUpdateTime(LocalDateTime.now());
        recruitmentMapper.insert(recruitment);
        return getTeamDetail(teamId, operatorId);
    }

    @Override
    @Transactional
    public TeamVO updateRecruitment(Long teamId, Long recruitmentId, RecruitmentUpdateRequest request,
                                    Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        BusinessException.throwIf(!Boolean.TRUE.equals(request.getNeedModeler())
                        && !Boolean.TRUE.equals(request.getNeedProgrammer())
                        && !Boolean.TRUE.equals(request.getNeedWriter()),
                TeamErrorCode.RECRUITMENT_ROLE_REQUIRED);
        TeamRecruitment recruitment = recruitmentMapper.selectByIdForUpdate(recruitmentId);
        BusinessException.throwIf(recruitment == null || !teamId.equals(recruitment.getTeamId()),
                TeamErrorCode.RECRUITMENT_NOT_FOUND);
        BusinessException.throwIf(!RECRUITMENT_OPEN.equals(recruitment.getStatus()),
                TeamErrorCode.RECRUITMENT_ALREADY_CLOSED);
        recruitment.setNeedModeler(request.getNeedModeler());
        recruitment.setNeedProgrammer(request.getNeedProgrammer());
        recruitment.setNeedWriter(request.getNeedWriter());
        recruitment.setDescription(request.getDescription());
        recruitment.setUpdateTime(LocalDateTime.now());
        recruitmentMapper.updateById(recruitment);
        return getTeamDetail(teamId, operatorId);
    }

    @Override
    @Transactional
    public void closeRecruitment(Long teamId, Long recruitmentId, Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        TeamRecruitment recruitment = recruitmentMapper.selectByIdForUpdate(recruitmentId);
        BusinessException.throwIf(recruitment == null || !teamId.equals(recruitment.getTeamId()),
                TeamErrorCode.RECRUITMENT_NOT_FOUND);
        BusinessException.throwIf(!RECRUITMENT_OPEN.equals(recruitment.getStatus()),
                TeamErrorCode.RECRUITMENT_ALREADY_CLOSED);
        recruitment.setStatus(RECRUITMENT_CLOSED);
        recruitment.setUpdateTime(LocalDateTime.now());
        recruitmentMapper.updateById(recruitment);
        closePendingApplicationsForRecruitment(recruitmentId, operatorId);
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
        checkPracticePreparing(team);

        // 解散并关闭待处理申请
        team.setStatus(STATUS_DISBANDED);
        team.setPracticeStatus("DISBANDED");
        team.setEndedAt(LocalDateTime.now());
        updateById(team);
        closeOpenRecruitments(teamId);
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
                .ne(status == null, Team::getPracticeStatus, "DISBANDED")
                .orderByDesc(Team::getUpdateTime)
                .orderByDesc(Team::getId);
        List<Team> teams = baseMapper.selectList(wrapper);
        for (Team team : teams) refreshExpiredPractice(team);
        return assembleTeams(teams, userId);
    }

    /** {@inheritDoc} */
    @Override
    public List<TeamVO> listMyTeams(Long userId) {
        return listMyTeams(userId, null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public PageResult<TeamVO> pageMyTeams(Long userId, MyTeamPageQuery query) {
        // 找出当前用户参与过的队伍，并先收敛已到期状态。
        List<Long> teamIds = getUserTeamIds(userId);
        if (teamIds.isEmpty()) {
            return new PageResult<>(0, query.getPage(), query.getPageSize(), List.of());
        }
        refreshExpiredPractices(teamIds);

        // 每个生命周期板块独立分页，避免一个板块挤占其他板块。
        Page<Team> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<Team> teamPage = baseMapper.selectPage(page, new LambdaQueryWrapper<Team>()
                .in(Team::getId, teamIds)
                .eq(Team::getStatus, STATUS_ACTIVE)
                .eq(Team::getPracticeStatus, query.getPracticeStatus())
                .orderByDesc(Team::getUpdateTime)
                .orderByDesc(Team::getId));
        return new PageResult<>(teamPage.getTotal(), query.getPage(), query.getPageSize(),
                assembleTeams(teamPage.getRecords(), userId));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void removeMember(Long teamId, Long memberId, Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
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
        Team team = lockRequiredActiveTeam(teamId);
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
        Team team = lockRequiredActiveTeam(teamId);
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
        // 与开始练习、关闭招募和审核申请保持相同锁顺序。
        Team team = lockRequiredActiveTeam(teamId);
        TeamRecruitment recruitment = recruitmentMapper.selectByIdForUpdate(request.getRecruitmentId());
        validateApplicationAllowed(team, recruitment, applicantId);

        TeamJoinApplication application = new TeamJoinApplication();
        application.setTeamId(teamId);
        application.setRecruitmentId(recruitment.getId());
        application.setApplicantId(applicantId);
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
        Team team = lockRequiredActiveTeam(teamId);
        checkPracticePreparing(team);
        TeamJoinApplication application = applicationMapper.selectPendingForUpdate(teamId, applicantId);
        BusinessException.throwIf(application == null, TeamErrorCode.APPLICATION_NOT_FOUND);
        finishApplication(application, CANCELLED, applicantId);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<JoinApplicationVO> pageApplications(Long teamId, JoinApplicationPageQuery query,
                                                          Long operatorId) {
        // 只有队长可以查看申请历史。
        Team team = getRequiredTeam(teamId);
        checkLeader(team, operatorId);

        // 待处理申请优先，其余按最近申请时间稳定倒序。
        Page<TeamJoinApplication> page = new Page<>(query.getPage(), query.getPageSize());
        QueryWrapper<TeamJoinApplication> wrapper = new QueryWrapper<>();
        wrapper.eq("team_id", teamId)
                .eq(query.getStatus() != null && !query.getStatus().isBlank(), "status", query.getStatus())
                .orderByAsc("CASE WHEN status = 'pending' THEN 0 ELSE 1 END")
                .orderByDesc("create_time")
                .orderByDesc("id");
        IPage<TeamJoinApplication> applicationPage = applicationMapper.selectPage(page, wrapper);
        List<TeamJoinApplication> applications = applicationPage.getRecords();

        // 批量聚合用户与招募位置，避免逐条查询。
        Map<Long, UserPublicSummaryDTO> summaries = getUserSummaries(applicationUserIds(applications));
        Map<Long, TeamRecruitment> recruitments = getRecruitmentsByIds(applicationRecruitmentIds(applications));
        List<JoinApplicationVO> result = new ArrayList<>();
        for (TeamJoinApplication application : applications) {
            result.add(toApplicationVO(application, summaries.get(application.getApplicantId()),
                    recruitments.get(application.getRecruitmentId())));
        }
        return new PageResult<>(applicationPage.getTotal(), query.getPage(), query.getPageSize(), result);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public JoinApplicationVO reviewApplication(Long teamId, Long applicationId,
                                               JoinApplicationReviewRequest request, Long operatorId) {
        // 锁定团队和申请，串行化审核与容量检查
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        checkPracticePreparing(team);
        TeamJoinApplication application = applicationMapper.selectByIdForUpdate(applicationId);
        BusinessException.throwIf(application == null || !teamId.equals(application.getTeamId()),
                TeamErrorCode.APPLICATION_NOT_FOUND);
        BusinessException.throwIf(!PENDING.equals(application.getStatus()),
                TeamErrorCode.APPLICATION_ALREADY_HANDLED);

        // 按审核决定流转
        if (APPROVED.equals(request.getDecision())) {
            TeamRecruitment recruitment = recruitmentMapper.selectByIdForUpdate(application.getRecruitmentId());
            validateApplicationApproval(team, recruitment, application.getApplicantId());
            checkNoActiveProblemTeam(application.getApplicantId(), team.getProblemId());
            try {
                teamMemberMapper.insert(buildMember(teamId, application.getApplicantId(), ROLE_MEMBER,
                        recruitment.getNeedModeler(), recruitment.getNeedProgrammer(), recruitment.getNeedWriter()));
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(TeamErrorCode.USER_ALREADY_IN_TEAM);
            }
            recruitment.setStatus(RECRUITMENT_FILLED);
            recruitment.setUpdateTime(LocalDateTime.now());
            recruitmentMapper.updateById(recruitment);
            finishApplication(application, APPROVED, operatorId);
            closePendingApplicationsForRecruitment(recruitment.getId(), operatorId);
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
        updateById(team);
        closeOpenRecruitments(teamId);
        closePendingApplications(teamId, operatorId);
        return getTeamDetail(teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO endPractice(Long teamId, Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        refreshExpiredPractice(team);
        BusinessException.throwIf(!"IN_PROGRESS".equals(team.getPracticeStatus()),
                TeamErrorCode.PRACTICE_NOT_IN_PROGRESS);
        markPracticeEnded(team, LocalDateTime.now());
        return getTeamDetail(teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamMemberVO updateSubmissionPermission(Long teamId, Long memberId,
                                                   SubmissionPermissionUpdateRequest request,
                                                   Long operatorId) {
        Team team = lockRequiredActiveTeam(teamId);
        checkLeader(team, operatorId);
        refreshExpiredPractice(team);
        BusinessException.throwIf(!"PREPARING".equals(team.getPracticeStatus())
                        && !"IN_PROGRESS".equals(team.getPracticeStatus()),
                TeamErrorCode.TEAM_OPERATION_NOT_ALLOWED);
        TeamMember member = getTeamMember(teamId, memberId);
        BusinessException.throwIf(member == null, TeamErrorCode.NOT_TEAM_MEMBER);
        BusinessException.throwIf(ROLE_LEADER.equals(member.getRole()),
                TeamErrorCode.LEADER_SUBMISSION_PERMISSION_FIXED);
        member.setCanSubmit(request.getCanSubmit());
        teamMemberMapper.updateById(member);
        return toMemberVO(member, getUserSummaries(List.of(memberId)).get(memberId));
    }

    /** {@inheritDoc} */
    @Override
    @Scheduled(fixedDelayString = "${team.practice-expiration.delay-ms:60000}")
    @Transactional
    public void endExpiredPractices() {
        LocalDateTime now = LocalDateTime.now();
        List<Team> expired = baseMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getStatus, STATUS_ACTIVE)
                .eq(Team::getPracticeStatus, "IN_PROGRESS")
                .le(Team::getDeadlineAt, now));
        for (Team team : expired) markPracticeEnded(team, team.getDeadlineAt());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamSubmissionAccessDTO getSubmissionAccess(Long teamId, Long userId) {
        Team team = getRequiredTeam(teamId);
        TeamMember member = getTeamMember(teamId, userId);
        boolean canSubmit = member != null && (ROLE_LEADER.equals(member.getRole())
                || Boolean.TRUE.equals(member.getCanSubmit()));
        return new TeamSubmissionAccessDTO(teamId, team.getProblemId(), member != null, canSubmit,
                team.getPracticeStatus(), team.getDeadlineAt(), team.getEndedAt());
    }

    // ==================== 查询与组装 ====================

    private List<TeamVO> assembleTeams(List<Team> teams, Long currentUserId) {
        if (teams.isEmpty()) return List.of();
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, List<TeamMember>> memberMap = getMembersByTeamIds(teamIds);
        Set<Long> userIds = new LinkedHashSet<>();
        for (List<TeamMember> members : memberMap.values()) userIds.addAll(memberUserIds(members));
        Map<Long, UserPublicSummaryDTO> summaries = getUserSummaries(new ArrayList<>(userIds));
        Map<Long, ProblemPracticeDTO> problems = getProblemSummaries(
                teams.stream().map(Team::getProblemId).distinct().toList());
        Set<Long> pendingTeamIds = getPendingTeamIds(currentUserId, teamIds);
        List<TeamVO> result = new ArrayList<>();
        for (Team team : teams) {
            TeamVO view = assembleTeamVO(team, memberMap.getOrDefault(team.getId(), List.of()),
                    currentUserId, summaries, pendingTeamIds);
            ProblemPracticeDTO practice = problems.get(team.getProblemId());
            view.setProblemTitle(practice.getTitle());
            view.setProblemCode(practice.getCode());
            result.add(view);
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
        List<TeamRecruitmentVO> recruitments = "PREPARING".equals(team.getPracticeStatus())
                ? getOpenRecruitmentVOs(team.getId()) : List.of();
        long openRecruitments = recruitments.stream().filter(item -> RECRUITMENT_OPEN.equals(item.getStatus())).count();
        return TeamVO.builder()
                .id(team.getId()).name(team.getName()).description(team.getDescription())
                .leaderId(team.getLeaderId()).problemId(team.getProblemId())
                .maxMembers(DEFAULT_MAX_MEMBERS).status(team.getStatus())
                .practiceStatus(team.getPracticeStatus()).startedAt(team.getStartedAt())
                .deadlineAt(team.getDeadlineAt()).endedAt(team.getEndedAt())
                .recruitments(recruitments)
                .memberCount(memberCount).remainingSlots(Math.max(0, DEFAULT_MAX_MEMBERS - memberCount))
                .currentUserRelation(relation)
                .canApply(active && "PREPARING".equals(team.getPracticeStatus())
                        && openRecruitments > 0
                        && "none".equals(relation))
                .canManage("leader".equals(relation))
                .canLeave(active && "PREPARING".equals(team.getPracticeStatus()) && "member".equals(relation))
                .createTime(team.getCreateTime()).members(memberVOs).build();
    }

    private TeamMemberVO toMemberVO(TeamMember member, UserPublicSummaryDTO summary) {
        return TeamMemberVO.builder().id(member.getId()).userId(member.getUserId())
                .nickname(summary != null ? summary.getNickname() : null)
                .avatarUrl(summary != null ? summary.getAvatarUrl() : null)
                .role(member.getRole()).modeler(member.getModeler()).programmer(member.getProgrammer())
                .writer(member.getWriter())
                .canSubmit(ROLE_LEADER.equals(member.getRole()) || Boolean.TRUE.equals(member.getCanSubmit()))
                .joinedAt(member.getJoinedAt()).build();
    }

    private JoinApplicationVO toApplicationVO(TeamJoinApplication application, UserPublicSummaryDTO summary) {
        return toApplicationVO(application, summary, recruitmentMapper.selectById(application.getRecruitmentId()));
    }

    /**
     * 使用已批量查询的招募位置组装申请视图。
     */
    private JoinApplicationVO toApplicationVO(TeamJoinApplication application, UserPublicSummaryDTO summary,
                                              TeamRecruitment recruitment) {
        return JoinApplicationVO.builder().id(application.getId()).teamId(application.getTeamId())
                .recruitmentId(application.getRecruitmentId())
                .applicantId(application.getApplicantId())
                .nickname(summary != null ? summary.getNickname() : null)
                .avatarUrl(summary != null ? summary.getAvatarUrl() : null)
                .needModeler(recruitment != null && Boolean.TRUE.equals(recruitment.getNeedModeler()))
                .needProgrammer(recruitment != null && Boolean.TRUE.equals(recruitment.getNeedProgrammer()))
                .needWriter(recruitment != null && Boolean.TRUE.equals(recruitment.getNeedWriter()))
                .message(application.getMessage()).status(application.getStatus())
                .handledBy(application.getHandledBy()).handledAt(application.getHandledAt())
                .createTime(application.getCreateTime()).build();
    }

    // ==================== 业务校验 ====================

    private void validateApplicationAllowed(Team team, TeamRecruitment recruitment, Long applicantId) {
        checkPracticePreparing(team);
        BusinessException.throwIf(recruitment == null || !team.getId().equals(recruitment.getTeamId()),
                TeamErrorCode.RECRUITMENT_NOT_FOUND);
        BusinessException.throwIf(!RECRUITMENT_OPEN.equals(recruitment.getStatus()), TeamErrorCode.TEAM_NOT_RECRUITING);
        BusinessException.throwIf(team.getLeaderId().equals(applicantId), TeamErrorCode.CANNOT_APPLY_OWN_TEAM);
        BusinessException.throwIf(getTeamMember(team.getId(), applicantId) != null, TeamErrorCode.USER_ALREADY_IN_TEAM);
        BusinessException.throwIf(getMemberCount(team.getId()) >= DEFAULT_MAX_MEMBERS, TeamErrorCode.TEAM_FULL);
        BusinessException.throwIf(getPendingApplication(team.getId(), applicantId) != null,
                TeamErrorCode.APPLICATION_ALREADY_PENDING);
    }

    private void validateApplicationApproval(Team team, TeamRecruitment recruitment, Long applicantId) {
        checkPracticePreparing(team);
        BusinessException.throwIf(recruitment == null || !team.getId().equals(recruitment.getTeamId()),
                TeamErrorCode.RECRUITMENT_NOT_FOUND);
        BusinessException.throwIf(!RECRUITMENT_OPEN.equals(recruitment.getStatus()),
                TeamErrorCode.RECRUITMENT_ALREADY_CLOSED);
        validateCanAddMember(team, applicantId);
    }

    private void validateCanAddMember(Team team, Long userId) {
        BusinessException.throwIf(getTeamMember(team.getId(), userId) != null, TeamErrorCode.USER_ALREADY_IN_TEAM);
        BusinessException.throwIf(getMemberCount(team.getId()) >= DEFAULT_MAX_MEMBERS, TeamErrorCode.TEAM_FULL);
    }

    private void checkPracticePreparing(Team team) {
        BusinessException.throwIf(!"PREPARING".equals(team.getPracticeStatus()),
                TeamErrorCode.PRACTICE_ALREADY_STARTED);
    }

    private void refreshExpiredPractice(Team team) {
        if (!"IN_PROGRESS".equals(team.getPracticeStatus()) || team.getDeadlineAt() == null
                || LocalDateTime.now().isBefore(team.getDeadlineAt())) return;
        markPracticeEnded(team, team.getDeadlineAt());
    }

    private void markPracticeEnded(Team team, LocalDateTime endedAt) {
        team.setPracticeStatus("ENDED");
        team.setEndedAt(endedAt);
        updateById(team);
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
        refreshExpiredPractice(team);
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

    private long getOpenRecruitmentCount(Long teamId) {
        return recruitmentMapper.selectCount(new LambdaQueryWrapper<TeamRecruitment>()
                .eq(TeamRecruitment::getTeamId, teamId)
                .eq(TeamRecruitment::getStatus, RECRUITMENT_OPEN));
    }

    /**
     * 查询队伍当前开放的招募位置。
     */
    private List<TeamRecruitmentVO> getOpenRecruitmentVOs(Long teamId) {
        return recruitmentMapper.selectList(new LambdaQueryWrapper<TeamRecruitment>()
                        .eq(TeamRecruitment::getTeamId, teamId)
                        .eq(TeamRecruitment::getStatus, RECRUITMENT_OPEN)
                        .orderByAsc(TeamRecruitment::getCreateTime))
                .stream().map(item -> TeamRecruitmentVO.builder()
                        .id(item.getId()).needModeler(item.getNeedModeler())
                        .needProgrammer(item.getNeedProgrammer()).needWriter(item.getNeedWriter())
                        .description(item.getDescription())
                        .status(item.getStatus()).createTime(item.getCreateTime()).build())
                .toList();
    }

    /**
     * 批量查询招募位置并按 ID 建立索引。
     */
    private Map<Long, TeamRecruitment> getRecruitmentsByIds(List<Long> recruitmentIds) {
        if (recruitmentIds.isEmpty()) return Map.of();
        List<TeamRecruitment> recruitments = recruitmentMapper.selectBatchIds(recruitmentIds);
        Map<Long, TeamRecruitment> result = new HashMap<>();
        for (TeamRecruitment recruitment : recruitments) result.put(recruitment.getId(), recruitment);
        return result;
    }

    /**
     * 查询用户参加过的全部队伍 ID。
     */
    private List<Long> getUserTeamIds(Long userId) {
        return teamMemberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId))
                .stream()
                .map(TeamMember::getTeamId)
                .distinct()
                .toList();
    }

    /**
     * 收敛指定队伍中已经到期的练习状态。
     */
    private void refreshExpiredPractices(List<Long> teamIds) {
        LocalDateTime now = LocalDateTime.now();
        List<Team> expired = baseMapper.selectList(new LambdaQueryWrapper<Team>()
                .in(Team::getId, teamIds)
                .eq(Team::getStatus, STATUS_ACTIVE)
                .eq(Team::getPracticeStatus, "IN_PROGRESS")
                .le(Team::getDeadlineAt, now));
        for (Team team : expired) markPracticeEnded(team, team.getDeadlineAt());
    }

    private void closeOpenRecruitments(Long teamId) {
        recruitmentMapper.update(null, new UpdateWrapper<TeamRecruitment>()
                .eq("team_id", teamId).eq("status", RECRUITMENT_OPEN)
                .set("status", RECRUITMENT_CLOSED).set("update_time", LocalDateTime.now()));
    }

    private void closePendingApplicationsForRecruitment(Long recruitmentId, Long operatorId) {
        applicationMapper.update(null, new UpdateWrapper<TeamJoinApplication>()
                .eq("recruitment_id", recruitmentId).eq("pending_marker", 1)
                .set("status", CLOSED).set("pending_marker", null)
                .set("handled_by", operatorId).set("handled_at", LocalDateTime.now()));
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

    /**
     * 批量获取题目摘要并按题目标识建立索引。
     *
     * @param problemIds 题目标识集合
     * @return 题目摘要索引
     */
    private Map<Long, ProblemPracticeDTO> getProblemSummaries(List<Long> problemIds) {
        if (problemIds.isEmpty()) return Map.of();
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(problemIds));
        Result<List<ProblemPracticeDTO>> result = problemFeignClient.getPracticeProblems(distinctIds);
        BusinessException.throwIf(result == null || !result.isSuccess() || result.getData() == null,
                ErrorCodeEnum.SYSTEM_ERROR);
        Map<Long, ProblemPracticeDTO> summaries = new HashMap<>();
        for (ProblemPracticeDTO summary : result.getData()) summaries.put(summary.getId(), summary);
        BusinessException.throwIf(summaries.size() != distinctIds.size(), ErrorCodeEnum.SYSTEM_ERROR);
        return summaries;
    }

    private Set<Long> getPendingTeamIds(Long userId, List<Long> teamIds) {
        if (userId == null || teamIds.isEmpty()) return Set.of();
        List<TeamJoinApplication> applications = applicationMapper.selectList(
                new LambdaQueryWrapper<TeamJoinApplication>()
                        .eq(TeamJoinApplication::getApplicantId, userId)
                        .eq(TeamJoinApplication::getStatus, PENDING)
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
        applicationMapper.update(application, new UpdateWrapper<TeamJoinApplication>()
                .eq("id", application.getId())
                .set("pending_marker", null));
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
        member.setCanSubmit(ROLE_LEADER.equals(role));
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

    /**
     * 提取申请关联的去重招募位置 ID。
     */
    private List<Long> applicationRecruitmentIds(List<TeamJoinApplication> applications) {
        return applications.stream().map(TeamJoinApplication::getRecruitmentId).distinct().toList();
    }

    private void addRecruitmentRoleFilter(QueryWrapper<Team> wrapper, TeamPublicPageQuery query) {
        boolean modeler = Boolean.TRUE.equals(query.getNeedModeler());
        boolean programmer = Boolean.TRUE.equals(query.getNeedProgrammer());
        boolean writer = Boolean.TRUE.equals(query.getNeedWriter());
        if (!modeler && !programmer && !writer) return;
        List<String> roleConditions = new ArrayList<>();
        if (modeler) roleConditions.add("tr.need_modeler = 1");
        if (programmer) roleConditions.add("tr.need_programmer = 1");
        if (writer) roleConditions.add("tr.need_writer = 1");
        wrapper.exists("SELECT 1 FROM team_recruitment tr WHERE tr.team_id = team.id AND tr.status = 'OPEN' AND "
                + String.join(" AND ", roleConditions));
    }
}
