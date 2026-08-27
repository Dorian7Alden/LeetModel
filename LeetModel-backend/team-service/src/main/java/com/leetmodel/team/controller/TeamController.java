package com.leetmodel.team.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.team.dto.MemberRolesUpdateRequest;
import com.leetmodel.team.dto.JoinApplicationCreateRequest;
import com.leetmodel.team.dto.JoinApplicationPageQuery;
import com.leetmodel.team.dto.JoinApplicationReviewRequest;
import com.leetmodel.team.dto.MyTeamPageQuery;
import com.leetmodel.team.dto.RecruitmentUpdateRequest;
import com.leetmodel.team.dto.SubmissionPermissionUpdateRequest;
import com.leetmodel.team.dto.TeamCreateRequest;
import com.leetmodel.team.dto.TeamPublicPageQuery;
import com.leetmodel.team.dto.TeamUpdateRequest;
import com.leetmodel.team.service.TeamService;
import com.leetmodel.team.vo.TeamMemberVO;
import com.leetmodel.team.vo.JoinApplicationVO;
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

    @Operation(summary = "分页查询公共队伍")
    @GetMapping("/public")
    public Result<PageResult<TeamVO>> pagePublicTeams(@Valid TeamPublicPageQuery query) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.pagePublicTeams(query, userId));
    }

    @Operation(summary = "查询存在组建中缺人队伍的题目标识")
    @GetMapping("/public/preparing-problem-ids")
    public Result<List<Long>> listPublicPreparingProblemIds() {
        return Result.ok(teamService.listPublicPreparingProblemIds());
    }

    @Operation(summary = "查询我的队伍")
    @GetMapping({"/mine", ""})
    public Result<List<TeamVO>> listMyTeams(Integer status) {
        Long userId = UserContext.getUserId();
        List<TeamVO> teams = teamService.listMyTeams(userId, status);
        return Result.ok(teams);
    }

    @Operation(summary = "分页查询我的队伍")
    @GetMapping("/mine/page")
    public Result<PageResult<TeamVO>> pageMyTeams(@Valid MyTeamPageQuery query) {
        return Result.ok(teamService.pageMyTeams(UserContext.getUserId(), query));
    }

    @Operation(summary = "获取团队详情")
    @GetMapping("/{id}")
    public Result<TeamVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        TeamVO vo = teamService.getTeamDetail(id, userId);
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

    @Operation(summary = "发布一个招募位置")
    @PostMapping("/{id}/recruitments")
    public Result<TeamVO> publishRecruitment(@PathVariable Long id,
                                            @Valid @RequestBody RecruitmentUpdateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.publishRecruitment(id, request, userId));
    }

    @Operation(summary = "编辑一个开放招募位置")
    @PutMapping("/{id}/recruitments/{recruitmentId}")
    public Result<TeamVO> updateRecruitment(@PathVariable Long id, @PathVariable Long recruitmentId,
                                           @Valid @RequestBody RecruitmentUpdateRequest request) {
        return Result.ok(teamService.updateRecruitment(id, recruitmentId, request, UserContext.getUserId()));
    }

    @Operation(summary = "关闭一个招募位置")
    @DeleteMapping("/{id}/recruitments/{recruitmentId}")
    public Result<Void> closeRecruitment(@PathVariable Long id, @PathVariable Long recruitmentId) {
        teamService.closeRecruitment(id, recruitmentId, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "解散团队（队长）")
    @DeleteMapping("/{id}")
    public Result<Void> dissolve(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.dissolveTeam(id, userId);
        return Result.ok();
    }

    @Operation(summary = "提交入队申请")
    @PostMapping("/{id}/applications")
    public Result<JoinApplicationVO> submitApplication(
            @PathVariable Long id,
            @Valid @RequestBody JoinApplicationCreateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.submitApplication(id, request, userId));
    }

    @Operation(summary = "取消本人入队申请")
    @DeleteMapping("/{id}/applications/mine")
    public Result<Void> cancelMyApplication(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.cancelMyApplication(id, userId);
        return Result.ok();
    }

    @Operation(summary = "查询队伍入队申请")
    @GetMapping("/{id}/applications")
    public Result<PageResult<JoinApplicationVO>> pageApplications(
            @PathVariable Long id,
            @Valid JoinApplicationPageQuery query) {
        return Result.ok(teamService.pageApplications(id, query, UserContext.getUserId()));
    }

    @Operation(summary = "审核入队申请")
    @PutMapping("/{id}/applications/{applicationId}")
    public Result<JoinApplicationVO> reviewApplication(
            @PathVariable Long id,
            @PathVariable Long applicationId,
            @Valid @RequestBody JoinApplicationReviewRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.reviewApplication(id, applicationId, request, userId));
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

    @Operation(summary = "开始限时练习")
    @PostMapping("/{id}/practice/start")
    public Result<TeamVO> startPractice(@PathVariable Long id) {
        return Result.ok(teamService.startPractice(id, UserContext.getUserId()));
    }

    @Operation(summary = "提前结束限时练习")
    @PostMapping("/{id}/practice/end")
    public Result<TeamVO> endPractice(@PathVariable Long id) {
        return Result.ok(teamService.endPractice(id, UserContext.getUserId()));
    }

    @Operation(summary = "设置成员作品提交权限")
    @PutMapping("/{id}/members/{memberId}/submission-permission")
    public Result<TeamMemberVO> updateSubmissionPermission(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @Valid @RequestBody SubmissionPermissionUpdateRequest request) {
        return Result.ok(teamService.updateSubmissionPermission(
                id, memberId, request, UserContext.getUserId()));
    }
}
