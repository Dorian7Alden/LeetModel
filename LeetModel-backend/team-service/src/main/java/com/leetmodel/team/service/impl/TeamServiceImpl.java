package com.leetmodel.team.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.team.dto.AddMemberRequest;
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
import java.util.List;

/**
 * 团队服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team> implements TeamService {

    private final TeamMemberMapper teamMemberMapper;

    private static final String ROLE_LEADER = "leader";
    private static final String ROLE_MEMBER = "member";
    private static final int DEFAULT_MAX_MEMBERS = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISBANDED = 0;

    @Override
    @Transactional
    public TeamVO createTeam(TeamCreateRequest request, Long leaderId) {
        Team team = new Team();
        team.setName(request.getName());
        team.setDescription(request.getDescription());
        team.setLeaderId(leaderId);
        team.setMaxMembers(request.getMaxMembers() != null ? request.getMaxMembers() : DEFAULT_MAX_MEMBERS);
        team.setStatus(STATUS_ACTIVE);

        save(team);

        // 创建者自动成为队长
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(leaderId);
        member.setRole(ROLE_LEADER);
        member.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(member);

        log.info("创建团队: {} [ID: {}], 队长: {}", team.getName(), team.getId(), leaderId);
        return toVO(team, List.of(member));
    }

    @Override
    public TeamVO getTeamDetail(Long teamId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);

        List<TeamMember> members = getMembersByTeamId(teamId);
        return toVO(team, members);
    }

    @Override
    @Transactional
    public TeamVO updateTeam(Long teamId, TeamUpdateRequest request, Long operatorId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);

        checkLeader(team, operatorId);

        if (request.getName() != null && !request.getName().isBlank()) {
            team.setName(request.getName());
        }
        if (request.getDescription() != null) {
            team.setDescription(request.getDescription());
        }
        updateById(team);

        log.info("更新团队信息: {}", teamId);
        List<TeamMember> members = getMembersByTeamId(teamId);
        return toVO(team, members);
    }

    @Override
    @Transactional
    public void dissolveTeam(Long teamId, Long operatorId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);

        checkLeader(team, operatorId);
        BusinessException.throwIf(team.getStatus() == STATUS_DISBANDED, TeamErrorCode.TEAM_ALREADY_DISBANDED);

        team.setStatus(STATUS_DISBANDED);
        updateById(team);

        log.info("解散团队: {} [ID: {}], 操作者: {}", team.getName(), teamId, operatorId);
    }

    @Override
    public List<TeamVO> listMyTeams(Long userId) {
        // 查询用户所在的所有团队（通过 team_member 表）
        LambdaQueryWrapper<TeamMember> tmWrapper = new LambdaQueryWrapper<>();
        tmWrapper.eq(TeamMember::getUserId, userId);
        List<Long> teamIds = teamMemberMapper.selectList(tmWrapper).stream()
                .map(TeamMember::getTeamId)
                .toList();

        if (teamIds.isEmpty()) {
            return List.of();
        }

        List<Team> teams = listByIds(teamIds);
        return teams.stream()
                .map(t -> toVO(t, getMembersByTeamId(t.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void addMember(Long teamId, AddMemberRequest request, Long operatorId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        BusinessException.throwIf(team.getStatus() == STATUS_DISBANDED, TeamErrorCode.TEAM_ALREADY_DISBANDED);

        checkLeader(team, operatorId);

        Long targetUserId = request.getUserId();

        // 检查是否已在团队中
        checkUserNotInTeam(teamId, targetUserId);

        // 检查团队是否已满
        long currentMemberCount = getMemberCount(teamId);
        if (currentMemberCount >= team.getMaxMembers()) {
            throw new BusinessException(TeamErrorCode.TEAM_FULL);
        }

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(targetUserId);
        member.setRole(ROLE_MEMBER);
        member.setJoinedAt(LocalDateTime.now());
        teamMemberMapper.insert(member);

        log.info("添加成员: teamId={}, userId={}, 操作者={}", teamId, targetUserId, operatorId);
    }

    @Override
    @Transactional
    public void removeMember(Long teamId, Long memberId, Long operatorId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);

        checkLeader(team, operatorId);

        // 不能移除队长
        if (team.getLeaderId().equals(memberId)) {
            throw new BusinessException(TeamErrorCode.CANNOT_REMOVE_LEADER);
        }

        removeTeamMember(teamId, memberId);
        log.info("移除成员: teamId={}, memberId={}, 操作者={}", teamId, memberId, operatorId);
    }

    @Override
    @Transactional
    public void leaveTeam(Long teamId, Long userId) {
        Team team = getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);

        // 队长不能退出
        if (team.getLeaderId().equals(userId)) {
            throw new BusinessException(TeamErrorCode.LEADER_CANNOT_LEAVE);
        }

        // 检查是否是团队成员
        checkUserInTeam(teamId, userId);

        removeTeamMember(teamId, userId);
        log.info("成员退出团队: teamId={}, userId={}", teamId, userId);
    }

    // ==================== 私有方法 ====================

    private void checkLeader(Team team, Long operatorId) {
        if (!team.getLeaderId().equals(operatorId)) {
            throw new BusinessException(TeamErrorCode.NOT_TEAM_LEADER);
        }
    }

    private void checkUserInTeam(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId);
        BusinessException.throwIf(!teamMemberMapper.exists(wrapper), TeamErrorCode.NOT_TEAM_MEMBER);
    }

    private void checkUserNotInTeam(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId);
        BusinessException.throwIf(teamMemberMapper.exists(wrapper), TeamErrorCode.USER_ALREADY_IN_TEAM);
    }

    private long getMemberCount(Long teamId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId);
        return teamMemberMapper.selectCount(wrapper);
    }

    private void removeTeamMember(Long teamId, Long userId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId);
        teamMemberMapper.delete(wrapper);
    }

    private List<TeamMember> getMembersByTeamId(Long teamId) {
        LambdaQueryWrapper<TeamMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamMember::getTeamId, teamId).orderByAsc(TeamMember::getJoinedAt);
        return teamMemberMapper.selectList(wrapper);
    }

    private TeamVO toVO(Team team, List<TeamMember> members) {
        List<TeamMemberVO> memberVOs = members.stream()
                .map(m -> TeamMemberVO.builder()
                        .id(m.getId())
                        .userId(m.getUserId())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();

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
}
