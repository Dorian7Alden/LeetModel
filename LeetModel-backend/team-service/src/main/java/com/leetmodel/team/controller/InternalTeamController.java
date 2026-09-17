package com.leetmodel.team.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.AdminTeamCreateDTO;
import com.leetmodel.common.api.dto.AdminTeamPageQuery;
import com.leetmodel.common.api.dto.AdminTeamPracticeStatusDTO;
import com.leetmodel.common.api.dto.AdminTeamStatsDTO;
import com.leetmodel.common.api.dto.AdminTeamUpdateDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.vo.TeamAdminVO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 查询指定队伍的跨服务 DTO 实体（供 submission-service、ranking-service 使用）。
     *
     * @param teamId 目标队伍唯一 ID，不能为 null
     * @return 队伍 DTO 对象
     */
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

    /**
     * 查询指定队伍中所有成员的用户 ID 集合。
     *
     * @param teamId 目标队伍唯一 ID，不能为 null
     * @return 队员用户 ID 列表
     */
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

    /**
     * 校验并获取指定成员在队伍中的作品提交权限及实训状态。
     *
     * @param teamId 目标队伍唯一 ID，不能为 null
     * @param userId 目标用户 ID，不能为 null
     * @return 包含提交权限与实训状态的校验 DTO
     */
    @Operation(summary = "获取成员作品提交资格")
    @GetMapping("/{teamId}/members/{userId}/submission-access")
    public Result<TeamSubmissionAccessDTO> getSubmissionAccess(@PathVariable Long teamId,
                                                               @PathVariable Long userId) {
        return Result.ok(teamService.getSubmissionAccess(teamId, userId));
    }

    /**
     * 统计当前处于活跃正常状态的队伍总数。
     *
     * @return 活跃队伍数量
     */
    @Operation(summary = "获取活跃团队数量")
    @GetMapping("/count")
    public Result<Long> getActiveTeamCount() {
        long count = teamService.count(
                new LambdaQueryWrapper<Team>().eq(Team::getStatus, 1)
        );
        return Result.ok(count);
    }

    /**
     * 按创建时间倒序查询最近创建的队伍记录。
     *
     * @param limit 单次拉取数量上限
     * @return 队伍 DTO 列表
     */
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

    /**
     * 按标识批量查询队伍业务摘要，供管理聚合层补全关联名称。
     *
     * @param teamIds 队伍 ID 集合
     * @return 已存在的队伍摘要
     */
    @Operation(summary = "批量查询队伍摘要")
    @GetMapping("/summaries")
    public Result<List<TeamDTO>> listSummaries(
            @RequestParam
            @Size(min = 1, max = 100, message = "队伍数量必须在1到100之间")
            List<@Positive Long> teamIds) {
        List<Long> distinctIds = teamIds.stream().distinct().toList();
        List<Team> teams = teamService.listByIds(distinctIds);
        return Result.ok(teams.stream().map(team -> new TeamDTO(
                team.getId(),
                team.getName(),
                team.getLeaderId(),
                team.getStatus(),
                null,
                team.getProblemId(),
                team.getPracticeStatus(),
                team.getStartedAt(),
                team.getDeadlineAt(),
                team.getEndedAt()
        )).toList());
    }

    /**
     * 查询已达截止时间但仍处于未归档状态的实训队伍。
     *
     * @return 已过期的练习队伍 DTO 列表
     */
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

    /**
     * 查询指定用户当前所属的队伍信息。
     *
     * @param userId 目标用户 ID
     * @return 队伍 DTO，未入队返回 null
     */
    @Operation(summary = "查询用户所属当前队伍")
    @GetMapping("/users/{userId}/current")
    public Result<TeamDTO> getUserCurrentTeam(@PathVariable Long userId) {
        TeamMember member = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .orderByDesc(TeamMember::getCreateTime)
                        .last("LIMIT 1")
        );
        if (member == null) {
            return Result.ok(null);
        }
        Team team = teamService.getById(member.getTeamId());
        if (team == null) {
            return Result.ok(null);
        }
        long memberCount = teamMemberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, team.getId())
        );
        return Result.ok(new TeamDTO(team.getId(), team.getName(), team.getLeaderId(),
                team.getStatus(), (int) memberCount, team.getProblemId(), team.getPracticeStatus(),
                team.getStartedAt(), team.getDeadlineAt(), team.getEndedAt()));
    }

    /**
     * 管理端队伍多维检索分页。
     */
    @Operation(summary = "管理端队伍多维检索分页")
    @GetMapping("/admin/page")
    public Result<PageResult<TeamAdminVO>> pageAdminTeams(@Validated AdminTeamPageQuery query) {
        return Result.ok(teamService.pageAdminTeams(query));
    }

    /**
     * 管理端队伍数据大盘统计。
     */
    @Operation(summary = "管理端队伍数据大盘统计")
    @GetMapping("/admin/stats")
    public Result<AdminTeamStatsDTO> getAdminStats() {
        return Result.ok(teamService.getAdminStats());
    }

    /**
     * 管理端查询队伍完整档案。
     */
    @Operation(summary = "管理端查询队伍完整档案")
    @GetMapping("/{teamId}/admin/detail")
    public Result<TeamAdminVO> getAdminDetail(@PathVariable Long teamId) {
        return Result.ok(teamService.getAdminDetail(teamId));
    }

    /**
     * 管理端代建队伍。
     */
    @Operation(summary = "管理端代建队伍")
    @PostMapping("/admin")
    public Result<TeamAdminVO> adminCreateTeam(@Validated @RequestBody AdminTeamCreateDTO request) {
        return Result.ok(teamService.adminCreateTeam(request));
    }

    /**
     * 管理端修改队伍基础信息。
     */
    @Operation(summary = "管理端修改队伍基础信息")
    @PutMapping("/{teamId}/admin")
    public Result<TeamAdminVO> adminUpdateTeam(@PathVariable Long teamId,
                                               @Validated @RequestBody AdminTeamUpdateDTO request) {
        return Result.ok(teamService.adminUpdateTeam(teamId, request));
    }

    /**
     * 管理端调控队伍练习阶段。
     */
    @Operation(summary = "管理端调控队伍练习阶段")
    @PutMapping("/{teamId}/admin/practice-status")
    public Result<TeamAdminVO> adminUpdatePracticeStatus(@PathVariable Long teamId,
                                                         @Validated @RequestBody AdminTeamPracticeStatusDTO request) {
        return Result.ok(teamService.adminUpdatePracticeStatus(teamId, request));
    }

    /**
     * 管理端强制解散队伍。
     */
    @Operation(summary = "管理端强制解散队伍")
    @DeleteMapping("/{teamId}/admin")
    public Result<Void> adminDissolveTeam(@PathVariable Long teamId,
                                          @RequestParam(required = false) String reason) {
        teamService.adminDissolveTeam(teamId, reason);
        return Result.ok();
    }
}
