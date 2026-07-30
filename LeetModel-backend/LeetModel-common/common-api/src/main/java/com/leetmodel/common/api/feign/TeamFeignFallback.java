package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 团队服务 Feign 降级工厂。
 *
 * @author LeetModel
 */
@Slf4j
@Component
public class TeamFeignFallback implements FallbackFactory<TeamFeignClient> {

    @Override
    public TeamFeignClient create(Throwable cause) {
        log.error("TeamFeignClient 调用失败", cause);
        return new TeamFeignClient() {
            @Override
            public Result<TeamDTO> getTeamInfo(Long teamId) {
                return Result.fail(50001, "团队服务暂不可用");
            }

            @Override
            public Result<List<Long>> getMemberIds(Long teamId) {
                return Result.fail(50001, "团队服务暂不可用");
            }

            @Override
            public Result<Long> getActiveTeamCount() {
                return Result.ok(0L);
            }
        };
    }
}
