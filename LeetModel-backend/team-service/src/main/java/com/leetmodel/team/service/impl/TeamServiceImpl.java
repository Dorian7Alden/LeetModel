package com.leetmodel.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.team.dto.AddMemberRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.mapper.TeamMapper;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.service.TeamService;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 团队服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    private final TeamMemberMapper teamMemberMapper;
    private final UserFeignClient userFeignClient;

    private static final String ROLE_LEADER = "leader";
    private static final String ROLE_MEMBER = "member";
    private static final int DEFAULT_MAX_MEMBERS = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISBANDED = 0;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO createTeam(TeamCreateRequest request, Long leaderId) {
        // 创建团队
        Team team = new Team();
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setLeaderId(leaderId);
        team.setMaxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : DEFAULT_MAX_MEMBERS);
        team.setStatus(STATUS_ACTIVE);

        // 保存团队
        save(team);

        // 创建者自动成为队长
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(leaderId);
        member.setRole(ROLE_LEADER);
        initializeProfessionalRoles(member);
        member.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(member);

        log.info("创建团队: {} [ID: {}], 队长: {}", team.getName(), team.getId(), leaderId);
        return toVO(team, List.of(member));
    }

    /** {@inheritDoc} */
    @Override
    public TeamVO getTeamDetail(Long teamId) {
        // 查询团队
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);

        // 查询成员并组装详情
        List<TeamMember> members = getMembersByTeamId(teamId);
        return toVO(team, members);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId) {
        // 校验团队和操作权限
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        checkLeader(team, operatorId);

        // 更新非空字段
        if (request.getName() != null && !request.getName().isBlank()) {
            team.setName(request.getName());
        }
        if (request.getDescription() != null) {
            team.setDescription(request.getDescription());
        }
        if (request.getName() != null || request.getDescription() != null) {
            updateById(team);
        }

        // 返回最新详情
        log.info("更新团队信息: {}", teamId);
        List<TeamMember> members = getMembersByTeamId(teamId);
        return toVO(team, members);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void dissolveTeam(Long teamId, Long operatorId) {
        // 校验团队和操作权限
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        checkLeader(team, operatorId);

        // 标记为已解散，保留团队和成员历史
        team.setStatus(STATUS_DISBANDED);
        updateById(team);

        log.info("解散团队: {} [ID: {}], 操作者: {}", team.getName(), teamId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    public List<TeamVO> listMyTeams(Long userId) {
        // 查询用户关联的团队 ID
        LambdaQueryWrapper<TeamMember> tmWrapper = new LambdaQueryWrapper<>();
        tmWrapper.eq(TeamMember::getUserId, userId);
        List<Long> teamIds = teamMemberMapper.selectList(tmWrapper).stream()
                .map(TeamMember::getTeamId)
                .toList();

        if (teamIds.isEmpty()) return List.of();

        // 批量查询团队和成员，避免逐队查询
        List<Team> teams = listByIds(teamIds);
        if (teams.isEmpty()) return List.of();

        Map<Long, List<TeamMember>> membersByTeamId = getMembersByTeamIds(teamIds);
        List<TeamVO> result = new ArrayList<>();
        for (Team team : teams) {
            result.add(toVO(team, membersByTeamId.getOrDefault(team.getId(), List.of())));
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void addMember(Long teamId, AddMemberRequest request, Long operatorId) {
        // 校验目标用户
        validateUserAvailable(request.getUserId());

        // 锁定团队，串行化容量检查和成员写入
        Team team = baseMapper.selectByIdForUpdate(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        checkLeader(team, operatorId);

        Long targetUserId = request.getUserId();
        // 校验成员唯一性和团队容量
        checkUserNotInTeam(teamId, targetUserId);
        long currentMemberCount = getMemberCount(teamId);
        BusinessException.throwIf(currentMemberCount >= team.getMaxMembers(), TeamErrorCode.TEAM_FULL);

        // 新增普通成员
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(targetUserId);
        member.setRole(ROLE_MEMBER);
        initializeProfessionalRoles(member);
        member.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(member);

        log.info("添加成员: teamId={}, userId={}, 操作者={}", teamId, targetUserId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void removeMember(Long teamId, Long memberId, Long operatorId) {
        // 校验团队和操作权限
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        checkLeader(team, operatorId);

        // 校验目标成员
        BusinessException.throwIf(team.getLeaderId().equals(memberId), TeamErrorCode.CANNOT_REMOVE_LEADER);
        checkUserInTeam(teamId, memberId);

        // 删除成员关联
        removeTeamMember(teamId, memberId);
        log.info("移除成员: teamId={}, memberId={}, 操作者={}", teamId, memberId, operatorId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public TeamMemberVO updateMemberRoles(Long teamId, Long memberId,
                                          MemberRolesUpdateRequest request, Long operatorId) {
        // 校验团队和操作权限
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);
        checkLeader(team, operatorId);

        // 查询目标成员
        TeamMember member = getTeamMember(teamId, memberId);
        BusinessException.throwIf(member == null, TeamErrorCode.NOT_TEAM_MEMBER);

        // 覆盖三个可多选的专业角色状态
        member.setModeler(request.getModeler());
        member.setProgrammer(request.getProgrammer());
        member.setWriter(request.getWriter());
        teamMemberMapper.updateById(member);

        log.info("更新成员专业角色: teamId={}, memberId={}, 操作者={}", teamId, memberId, operatorId);
        return toMemberVO(member);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        // 校验团队状态
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        checkTeamActive(team);

        // 校验成员身份
        BusinessException.throwIf(team.getLeaderId().equals(userId), TeamErrorCode.LEADER_CANNOT_LEAVE);
        checkUserInTeam(teamId, userId);

        // 删除成员关联
        removeTeamMember(teamId, userId);
        log.info("成员退出团队: teamId={}, userId={}", teamId, userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验操作者是否为队长。
     *
     * @param team 团队实体
     * @param operatorId 操作者 ID
     */
    private void checkLeader(Team team, Long operatorId) {
        BusinessException.throwIf(
                !team.getLeaderId().equals(operatorId),
                TeamErrorCode.NOT_TEAM_LEADER
        );
    }

    /**
     * 校验团队是否处于活跃状态。
     *
     * @param team 团队实体
     */
    private void checkTeamActive(Team team) {
        BusinessException.throwIf(
                team.getStatus() == STATUS_DISBANDED,
                TeamErrorCode.TEAM_ALREADY_DISBANDED
        );
    }

    /**
     * 校验用户是否为指定团队成员。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     */
    private void checkUserInTeam(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId);
        BusinessException.throwIf(
                !teamMemberMapper.exists(wrapper),
                TeamErrorCode.NOT_TEAM_MEMBER
        );
    }

    /**
     * 校验用户尚未加入指定团队。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     */
    private void checkUserNotInTeam(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId);
        BusinessException.throwIf(
                teamMemberMapper.exists(wrapper),
                TeamErrorCode.USER_ALREADY_IN_TEAM
        );
    }

    /**
     * 查询指定团队成员。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     * @return 成员实体，不存在时返回 null
     */
    private TeamMember getTeamMember(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId);
        return teamMemberMapper.selectOne(wrapper);
    }

    /**
     * 初始化成员专业角色状态。
     *
     * @param member 成员实体
     */
    private void initializeProfessionalRoles(TeamMember member) {
        member.setModeler(false);
        member.setProgrammer(false);
        member.setWriter(false);
    }

    /**
     * 校验用户存在且账号可用。
     *
     * @param userId 用户 ID
     */
    private void validateUserAvailable(Long userId) {
        Result<Boolean> result = userFeignClient.isUserAvailable(userId);
        BusinessException.throwIf(result == null || !result.isSuccess(), ErrorCodeEnum.SYSTEM_ERROR);
        BusinessException.throwIf(!Boolean.TRUE.equals(result.getData()), TeamErrorCode.USER_NOT_AVAILABLE);
    }

    /**
     * 获取团队当前成员数。
     *
     * @param teamId 团队 ID
     * @return 当前成员数
     */
    private long getMemberCount(Long teamId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId);
        return teamMemberMapper.selectCount(wrapper);
    }

    /**
     * 删除指定团队成员关联。
     *
     * @param teamId 团队 ID
     * @param userId 用户 ID
     */
    private void removeTeamMember(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId);
        teamMemberMapper.delete(wrapper);
    }

    /**
     * 获取指定团队的成员列表。
     *
     * @param teamId 团队 ID
     * @return 成员列表
     */
    private List<TeamMember> getMembersByTeamId(Long teamId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).orderByAsc(TeamMember::getJoinedAt);
        return teamMemberMapper.selectList(wrapper);
    }

    /**
     * 批量获取团队成员并按团队 ID 分组。
     *
     * @param teamIds 团队 ID 列表
     * @return 团队 ID 到成员列表的映射
     */
    private Map<Long, List<TeamMember>> getMembersByTeamIds(List<Long> teamIds) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TeamMember::getTeamId, teamIds)
                .orderByAsc(TeamMember::getJoinedAt);
        List<TeamMember> members = teamMemberMapper.selectList(wrapper);

        Map<Long, List<TeamMember>> result = new HashMap<>();
        for (TeamMember member : members) {
            result.computeIfAbsent(member.getTeamId(), key -> new ArrayList<>()).add(member);
        }
        return result;
    }

    /**
     * 将团队实体和成员列表转换为团队视图。
     *
     * @param team 团队实体
     * @param members 成员列表
     * @return 团队视图
     */
    private TeamVO toVO(Team team, List<TeamMember> members) {
        List<TeamMemberVO> memberVOs = new ArrayList<>();
        for (TeamMember member : members) {
            memberVOs.add(toMemberVO(member));
        }

        return TeamVO.builder()
                .id(team.getId())
                .name(team.getName())
                .description(team.getDescription())
                .leaderId(team.getLeaderId())
                .maxMembers(team.getMaxMembers())
                .status(team.getStatus())
                .createTime(team.getCreateTime())
                .members(memberVOs)
                .build();
    }

    /**
     * 将成员实体转换为成员视图。
     *
     * @param member 成员实体
     * @return 成员视图
     */
    private TeamMemberVO toMemberVO(TeamMember member) {
        return TeamMemberVO.builder()
                .id(member.getId())
                .userId(member.getUserId())
                .role(member.getRole())
                .modeler(member.getModeler())
                .programmer(member.getProgrammer())
                .writer(member.getWriter())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
