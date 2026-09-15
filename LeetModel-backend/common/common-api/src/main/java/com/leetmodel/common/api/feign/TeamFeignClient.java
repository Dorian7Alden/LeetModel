package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.AdminTeamCreateDTO;
import com.leetmodel.common.api.dto.AdminTeamPageQuery;
import com.leetmodel.common.api.dto.AdminTeamPracticeStatusDTO;
import com.leetmodel.common.api.dto.AdminTeamStatsDTO;
import com.leetmodel.common.api.dto.AdminTeamUpdateDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.api.vo.TeamAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 团队服务 Feign 客户端 —— 供其他微服务调用。
 */
@FeignClient(
        name = "team-service",
        fallbackFactory = TeamFeignFallback.class
)
public interface TeamFeignClient {

    /**
     * 获取团队基本信息。
     *
     * @param teamId 团队 ID
     * @return 团队 DTO
     */
    @GetMapping("/internal/teams/{teamId}")
    Result<TeamDTO> getTeamInfo(@PathVariable Long teamId);

    /**
     * 获取团队成员用户 ID 列表。
     *
     * @param teamId 团队 ID
     * @return 成员 ID 列表
     */
    @GetMapping("/internal/teams/{teamId}/members")
    Result<List<Long>> getMemberIds(@PathVariable Long teamId);

    @GetMapping("/internal/teams/{teamId}/members/{userId}/submission-access")
    Result<TeamSubmissionAccessDTO> getSubmissionAccess(@PathVariable Long teamId,
                                                        @PathVariable Long userId);

    @GetMapping("/internal/teams/count")
    Result<Long> getActiveTeamCount();

    @GetMapping("/internal/teams/recent")
    Result<List<TeamDTO>> listRecent(@RequestParam("limit") Integer limit);

    /**
     * 按标识批量获取队伍业务摘要。
     *
     * @param teamIds 队伍 ID 集合
     * @return 已存在的队伍摘要
     */
    @GetMapping("/internal/teams/summaries")
    Result<List<TeamDTO>> listSummaries(@RequestParam("teamIds") List<Long> teamIds);

    @GetMapping("/internal/teams/practice/expired")
    Result<List<TeamDTO>> listExpiredPractices();

    @GetMapping("/internal/teams/users/{userId}/current")
    Result<TeamDTO> getUserCurrentTeam(@PathVariable Long userId);

    @GetMapping("/internal/teams/admin/page")
    Result<PageResult<TeamAdminVO>> pageAdminTeams(@SpringQueryMap AdminTeamPageQuery query);

    @GetMapping("/internal/teams/admin/stats")
    Result<AdminTeamStatsDTO> getAdminStats();

    @GetMapping("/internal/teams/{teamId}/admin/detail")
    Result<TeamAdminVO> getAdminDetail(@PathVariable("teamId") Long teamId);

    @PostMapping("/internal/teams/admin")
    Result<TeamAdminVO> adminCreateTeam(@RequestBody AdminTeamCreateDTO request);

    @PutMapping("/internal/teams/{teamId}/admin")
    Result<TeamAdminVO> adminUpdateTeam(@PathVariable("teamId") Long teamId, @RequestBody AdminTeamUpdateDTO request);

    @PutMapping("/internal/teams/{teamId}/admin/practice-status")
    Result<TeamAdminVO> adminUpdatePracticeStatus(@PathVariable("teamId") Long teamId, @RequestBody AdminTeamPracticeStatusDTO request);

    @DeleteMapping("/internal/teams/{teamId}/admin")
    Result<Void> adminDissolveTeam(@PathVariable("teamId") Long teamId, @RequestParam(value = "reason", required = false) String reason);
}
