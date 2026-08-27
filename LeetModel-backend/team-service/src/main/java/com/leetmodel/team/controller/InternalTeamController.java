package com.leetmodel.team.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 团队服务内部 Feign 接口实现。
 */
@Tag(name = "内部接口")
@RestController
@Validated
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
                team.getStartedAt(), team.getDeadlineAt(), team.getEndedAt()));
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

    @Operation(summary = "获取成员作品提交资格")
    @GetMapping("/{teamId}/members/{userId}/submission-access")
    public Result<TeamSubmissionAccessDTO> getSubmissionAccess(@PathVariable Long teamId,
                                                               @PathVariable Long userId) {
        return Result.ok(teamService.getSubmissionAccess(teamId, userId));
    }

    @Operation(summary = "获取活跃团队数量")
    @GetMapping("/count")
    public Result<Long> getActiveTeamCount() {
        long count = teamService.count(
                new LambdaQueryWrapper<Team>().eq(Team::getStatus, 1)
        );
        return Result.ok(count);
    }

    @Operation(summary = "查询最近队伍")
    @GetMapping("/recent")
    public Result<List<TeamDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        List<Team> teams = teamService.list(new LambdaQueryWrapper<Team>()
                .orderByDesc(Team::getCreateTime).last("LIMIT " + limit));
        return Result.ok(teams.stream().map(team -> {
            long memberCount = teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, team.getId()));
            return new TeamDTO(team.getId(), team.getName(), team.getLeaderId(), team.getStatus(),
                    (int) memberCount, team.getProblemId(), team.getPracticeStatus(),
                    team.getStartedAt(), team.getDeadlineAt(), team.getEndedAt());
        }).toList());
    }

    @Operation(summary = "查询已到截止时间的练习")
    @GetMapping("/practice/expired")
    public Result<List<TeamDTO>> listExpiredPractices() {
        List<Team> teams = teamService.list(new LambdaQueryWrapper<Team>()
                .and(wrapper -> wrapper
                        .eq(Team::getPracticeStatus, "ENDED")
                        .or(nested -> nested
                                .eq(Team::getPracticeStatus, "IN_PROGRESS")
                                .le(Team::getDeadlineAt, java.time.LocalDateTime.now()))));
        return Result.ok(teams.stream().map(team -> new TeamDTO(team.getId(), team.getName(), team.getLeaderId(),
                team.getStatus(), null, team.getProblemId(), team.getPracticeStatus(),
                team.getStartedAt(), team.getDeadlineAt(), team.getEndedAt())).toList());
    }
}
