package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 团队服务 Feign 客户端 —— 供其他微服务调用。
 *
 * @author LeetModel
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

    @GetMapping("/internal/teams/count")
    Result<Long> getActiveTeamCount();
}
