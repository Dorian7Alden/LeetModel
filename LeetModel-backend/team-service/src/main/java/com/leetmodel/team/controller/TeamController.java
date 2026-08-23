package com.leetmodel.team.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.team.dto.AddMemberRequest;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.service.TeamService;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.TeamVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 团队管理接口。
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Tag(name = "团队管理")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "创建团队")
    @PostMapping
    public Result<TeamVO> create(@Valid @RequestBody TeamCreateRequest request) {
        Long userId = UserContext.getUserId();
        TeamVO vo = teamService.createTeam(request, userId);
        return Result.ok(vo);
    }

    @Operation(summary = "我加入的团队列表")
    @GetMapping
    public Result<List<TeamVO>> listMyTeams() {
        Long userId = UserContext.getUserId();
        List<TeamVO> teams = teamService.listMyTeams(userId);
        return Result.ok(teams);
    }

    @Operation(summary = "获取团队详情")
    @GetMapping("/{id}")
    public Result<TeamVO> detail(@PathVariable Long id) {
        TeamVO vo = teamService.getTeamDetail(id);
        return Result.ok(vo);
    }

    @Operation(summary = "更新团队信息（队长）")
    @PutMapping("/{id}")
    public Result<TeamVO> update(@PathVariable Long id,
                                  @Valid @RequestBody TeamUpdateRequest request) {
        Long userId = UserContext.getUserId();
        TeamVO vo = teamService.updateTeam(id, request, userId);
        return Result.ok(vo);
    }

    @Operation(summary = "解散团队（队长）")
    @DeleteMapping("/{id}")
    public Result<Void> dissolve(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.dissolveTeam(id, userId);
        return Result.ok();
    }

    @Operation(summary = "添加成员（队长）")
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id,
                                   @Valid @RequestBody AddMemberRequest request) {
        Long userId = UserContext.getUserId();
        teamService.addMember(id, request, userId);
        return Result.ok();
    }

    @Operation(summary = "移除成员（队长）")
    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long id,
                                      @PathVariable Long memberId) {
        Long userId = UserContext.getUserId();
        teamService.removeMember(id, memberId, userId);
        return Result.ok();
    }

    @Operation(summary = "设置成员专业角色")
    @PutMapping("/{id}/members/{memberId}/roles")
    public Result<TeamMemberVO> updateMemberRoles(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberRolesUpdateRequest request) {
        Long userId = UserContext.getUserId();
        TeamMemberVO member = teamService.updateMemberRoles(id, memberId, request, userId);
        return Result.ok(member);
    }

    @Operation(summary = "退出团队")
    @DeleteMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.leaveTeam(id, userId);
        return Result.ok();
    }
}
