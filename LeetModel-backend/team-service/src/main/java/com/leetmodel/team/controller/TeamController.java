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

    /**
     * 创建新的实训队伍并设置当前用户为队长。
     *
     * @param request 包含队伍名称、题目 ID 与描述的创建请求，不能为 null
     * @return 创建成功后的队伍视图对象
     */
    @Operation(summary = "创建团队")
    @PostMapping
    public Result<TeamVO> create(@Valid @RequestBody TeamCreateRequest request) {
        Long userId = UserContext.getUserId();
        TeamVO vo = teamService.createTeam(request, userId);
        return Result.ok(vo);
    }

    /**
     * 分页查询当前平台开放招募的公共队伍列表。
     *
     * @param query 包含题目 ID、招募角色与分页参数的查询对象，不能为 null
     * @return 分页包装的队伍视图列表
     */
    @Operation(summary = "分页查询公共队伍")
    @GetMapping("/public")
    public Result<PageResult<TeamVO>> pagePublicTeams(@Valid TeamPublicPageQuery query) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.pagePublicTeams(query, userId));
    }

    /**
     * 查询当前存在组建中缺人队伍的题目 ID 集合。
     *
     * @return 存在开放招募队伍的题目 ID 列表
     */
    @Operation(summary = "查询存在组建中缺人队伍的题目标识")
    @GetMapping("/public/preparing-problem-ids")
    public Result<List<Long>> listPublicPreparingProblemIds() {
        return Result.ok(teamService.listPublicPreparingProblemIds());
    }

    /**
     * 查询当前登录用户参与的全部队伍列表。
     *
     * @param status 可选的队伍状态过滤条件（1=正常 0=解散）
     * @return 匹配的队伍视图列表
     */
    @Operation(summary = "查询我的队伍")
    @GetMapping({"/mine", ""})
    public Result<List<TeamVO>> listMyTeams(Integer status) {
        Long userId = UserContext.getUserId();
        List<TeamVO> teams = teamService.listMyTeams(userId, status);
        return Result.ok(teams);
    }

    /**
     * 分页查询当前登录用户的队伍列表。
     *
     * @param query 分页查询参数对象，不能为 null
     * @return 分页包装的队伍视图列表
     */
    @Operation(summary = "分页查询我的队伍")
    @GetMapping("/mine/page")
    public Result<PageResult<TeamVO>> pageMyTeams(@Valid MyTeamPageQuery query) {
        return Result.ok(teamService.pageMyTeams(UserContext.getUserId(), query));
    }

    /**
     * 查询指定队伍的详细信息（含成员、招募位与实训状态）。
     *
     * @param id 目标队伍唯一 ID，不能为 null
     * @return 队伍完整视图对象
     */
    @Operation(summary = "获取团队详情")
    @GetMapping("/{id}")
    public Result<TeamVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        TeamVO vo = teamService.getTeamDetail(id, userId);
        return Result.ok(vo);
    }

    /**
     * 队长修改队伍基础信息（名称、描述）。
     *
     * @param id      目标队伍唯一 ID，不能为 null
     * @param request 包含修改内容的请求对象，不能为 null
     * @return 更新后的队伍视图对象
     */
    @Operation(summary = "更新团队信息（队长）")
    @PutMapping("/{id}")
    public Result<TeamVO> update(@PathVariable Long id,
                                  @Valid @RequestBody TeamUpdateRequest request) {
        Long userId = UserContext.getUserId();
        TeamVO vo = teamService.updateTeam(id, request, userId);
        return Result.ok(vo);
    }

    /**
     * 队长发布新的队伍招募位置。
     *
     * @param id      目标队伍唯一 ID，不能为 null
     * @param request 包含需求角色与描述的招募请求对象，不能为 null
     * @return 更新后的队伍视图对象
     */
    @Operation(summary = "发布一个招募位置")
    @PostMapping("/{id}/recruitments")
    public Result<TeamVO> publishRecruitment(@PathVariable Long id,
                                            @Valid @RequestBody RecruitmentUpdateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.publishRecruitment(id, request, userId));
    }

    /**
     * 队长编辑已开放的招募位置信息。
     *
     * @param id            目标队伍唯一 ID，不能为 null
     * @param recruitmentId 招募条目唯一 ID，不能为 null
     * @param request       包含待更新角色要求的请求对象，不能为 null
     * @return 更新后的队伍视图对象
     */
    @Operation(summary = "编辑一个开放招募位置")
    @PutMapping("/{id}/recruitments/{recruitmentId}")
    public Result<TeamVO> updateRecruitment(@PathVariable Long id, @PathVariable Long recruitmentId,
                                           @Valid @RequestBody RecruitmentUpdateRequest request) {
        return Result.ok(teamService.updateRecruitment(id, recruitmentId, request, UserContext.getUserId()));
    }

    /**
     * 队长关闭指定的招募位置。
     *
     * @param id            目标队伍唯一 ID，不能为 null
     * @param recruitmentId 待关闭的招募条目 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "关闭一个招募位置")
    @DeleteMapping("/{id}/recruitments/{recruitmentId}")
    public Result<Void> closeRecruitment(@PathVariable Long id, @PathVariable Long recruitmentId) {
        teamService.closeRecruitment(id, recruitmentId, UserContext.getUserId());
        return Result.ok();
    }

    /**
     * 队长解散指定的队伍。
     *
     * @param id 目标队伍唯一 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "解散团队（队长）")
    @DeleteMapping("/{id}")
    public Result<Void> dissolve(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.dissolveTeam(id, userId);
        return Result.ok();
    }

    /**
     * 普通用户向队伍提交入队申请。
     *
     * @param id      目标队伍唯一 ID，不能为 null
     * @param request 包含申请理由的请求对象，不能为 null
     * @return 创建的入队申请视图对象
     */
    @Operation(summary = "提交入队申请")
    @PostMapping("/{id}/applications")
    public Result<JoinApplicationVO> submitApplication(
            @PathVariable Long id,
            @Valid @RequestBody JoinApplicationCreateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.submitApplication(id, request, userId));
    }

    /**
     * 申请人主动取消自己待处理的入队申请。
     *
     * @param id 目标队伍唯一 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "取消本人入队申请")
    @DeleteMapping("/{id}/applications/mine")
    public Result<Void> cancelMyApplication(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.cancelMyApplication(id, userId);
        return Result.ok();
    }

    /**
     * 队长分页查询收到的入队申请记录。
     *
     * @param id    目标队伍唯一 ID，不能为 null
     * @param query 申请分页查询参数，不能为 null
     * @return 分页包装的申请记录列表
     */
    @Operation(summary = "查询队伍入队申请")
    @GetMapping("/{id}/applications")
    public Result<PageResult<JoinApplicationVO>> pageApplications(
            @PathVariable Long id,
            @Valid JoinApplicationPageQuery query) {
        return Result.ok(teamService.pageApplications(id, query, UserContext.getUserId()));
    }

    /**
     * 队长审核入队申请（批准或拒绝）。
     *
     * @param id            目标队伍唯一 ID，不能为 null
     * @param applicationId 申请条目唯一 ID，不能为 null
     * @param request       包含审核结果的请求对象，不能为 null
     * @return 审核后的申请视图对象
     */
    @Operation(summary = "审核入队申请")
    @PutMapping("/{id}/applications/{applicationId}")
    public Result<JoinApplicationVO> reviewApplication(
            @PathVariable Long id,
            @PathVariable Long applicationId,
            @Valid @RequestBody JoinApplicationReviewRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(teamService.reviewApplication(id, applicationId, request, userId));
    }

    /**
     * 队长将指定成员移出队伍。
     *
     * @param id       目标队伍唯一 ID，不能为 null
     * @param memberId 目标成员记录唯一 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "移除成员（队长）")
    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long id,
                                      @PathVariable Long memberId) {
        Long userId = UserContext.getUserId();
        teamService.removeMember(id, memberId, userId);
        return Result.ok();
    }

    /**
     * 队长配置成员的建模、编程与论文专业分工角色。
     *
     * @param id       目标队伍唯一 ID，不能为 null
     * @param memberId 目标成员记录唯一 ID，不能为 null
     * @param request  包含所选专业角色列表的请求对象，不能为 null
     * @return 更新后的队员视图对象
     */
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

    /**
     * 队员主动退出所在队伍。
     *
     * @param id 目标队伍唯一 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "退出团队")
    @DeleteMapping("/{id}/leave")
    public Result<Void> leave(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        teamService.leaveTeam(id, userId);
        return Result.ok();
    }

    /**
     * 队长开启限时建模实训练习。
     *
     * @param id 目标队伍唯一 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    @Operation(summary = "开始限时练习")
    @PostMapping("/{id}/practice/start")
    public Result<TeamVO> startPractice(@PathVariable Long id) {
        return Result.ok(teamService.startPractice(id, UserContext.getUserId()));
    }

    /**
     * 队长提前结束当前进行中的限时练习。
     *
     * @param id 目标队伍唯一 ID，不能为 null
     * @return 更新后的队伍视图对象
     */
    @Operation(summary = "提前结束限时练习")
    @PostMapping("/{id}/practice/end")
    public Result<TeamVO> endPractice(@PathVariable Long id) {
        return Result.ok(teamService.endPractice(id, UserContext.getUserId()));
    }

    /**
     * 队长授予或撤销指定成员的论文提交权限。
     *
     * @param id       目标队伍唯一 ID，不能为 null
     * @param memberId 目标成员记录唯一 ID，不能为 null
     * @param request  包含权限授予布尔值的请求对象，不能为 null
     * @return 更新后的队员视图对象
     */
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
