package com.leetmodel.team.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.entity.TeamMember;
import com.leetmodel.team.mapper.TeamMemberMapper;
import com.leetmodel.team.service.TeamService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队服务内部 Feign 接口实现。
 *
 * @author LeetModel
 */
@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/teams")
@RequiredArgsConstructor
public class InternalTeamController {

    private final TeamService teamService;
    private final TeamMemberMapper teamMemberMapper;

    @GetMapping("/{teamId}")
    public Result<TeamDTO> getTeamInfo(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        if (team == null) {
            return Result.fail(40401, "团队不存在");
        }
        long memberCount = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId)
        );
        return Result.ok(new TeamDTO(team.getId(), team.getName(), team.getLeaderId(),
                team.getStatus(), (int) memberCount));
    }

    @GetMapping("/{teamId}/members")
    public Result<List<Long>> getMemberIds(@PathVariable Long teamId) {
        Team team = teamService.getById(teamId);
        if (team == null) {
            return Result.fail(40401, "团队不存在");
        }
        List<Long> memberIds = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId)
        ).stream().map(TeamMember::getUserId).toList();
        return Result.ok(memberIds);
    }

    @GetMapping("/count")
    public Result<Long> getActiveTeamCount() {
        long count = teamService.count(
                new LambdaQueryWrapper<Team>().eq(Team::getStatus, 1)
        );
        return Result.ok(count);
    }
}
