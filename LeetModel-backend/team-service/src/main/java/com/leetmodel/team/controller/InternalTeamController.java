package com.leetmodel.team.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.enums.TeamErrorCode;
import com.leetmodel.team.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队服务内部 Feign 接口实现。
 */
@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/teams")
@RequiredArgsConstructor
public class InternalTeamController {

    private final TeamService teamService;
    private final TeamMemberMapper teamMemberMapper;

    @Operation(summary = "获取团队信息")
    @GetMapping("/{teamId}")
    public Result<TeamDTO> getTeamInfo(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        long memberCount = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId)
        );
        return Result.ok(new TeamDTO(team.getId(), team.getName(), team.getLeaderId(),
                team.getStatus(), (int) memberCount, team.getProblemId(), team.getPracticeStatus(),
                team.getStartedAt(), team.getDeadlineAt()));
    }

    @Operation(summary = "获取团队成员用户 ID 列表")
    @GetMapping("/{teamId}/members")
    public Result<List<Long>> getMemberIds(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        List<Long> memberIds = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId)
        ).stream().map(TeamMember::getUserId).toList();
        return Result.ok(memberIds);
    }

    @Operation(summary = "获取活跃团队数量")
    @GetMapping("/count")
    public Result<Long> getActiveTeamCount() {
        long count = teamService.count(
                new LambdaQueryWrapper<Team>().eq(Team::getStatus, 1)
        );
        return Result.ok(count);
    }

    @Operation(summary = "标记队伍已提交")
    @PostMapping("/{teamId}/practice/submitted")
    public Result<Void> markSubmitted(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        BusinessException.throwIf(team == null || !("IN_PROGRESS".equals(team.getPracticeStatus())
                        || "SUBMITTED".equals(team.getPracticeStatus())),
                TeamErrorCode.PRACTICE_NOT_IN_PROGRESS);
        if ("IN_PROGRESS".equals(team.getPracticeStatus())) {
            team.setPracticeStatus("SUBMITTED");
            teamService.updateById(team);
        }
        return Result.ok();
    }

    @Operation(summary = "标记队伍练习完成")
    @PostMapping("/{teamId}/practice/completed")
    public Result<Void> markCompleted(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        BusinessException.throwIf(team == null, TeamErrorCode.TEAM_NOT_FOUND);
        if (!"COMPLETED".equals(team.getPracticeStatus())) {
            team.setPracticeStatus("COMPLETED");
            team.setEndedAt(java.time.LocalDateTime.now());
            teamService.updateById(team);
        }
        return Result.ok();
    }

    @Operation(summary = "查询已到截止时间的练习")
    @GetMapping("/practice/expired")
    public Result<List<TeamDTO>> listExpiredPractices() {
        List<Team> teams = teamService.list(new LambdaQueryWrapper<Team>()
                .in(Team::getPracticeStatus, "IN_PROGRESS", "SUBMITTED")
                .le(Team::getDeadlineAt, java.time.LocalDateTime.now()));
        return Result.ok(teams.stream().map(team -> new TeamDTO(team.getId(), team.getName(), team.getLeaderId(),
                team.getStatus(), null, team.getProblemId(), team.getPracticeStatus(),
                team.getStartedAt(), team.getDeadlineAt())).toList());
    }
}
