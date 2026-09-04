package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.dto.TeamSubmissionAccessDTO;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 团队微服务 Feign 客户端降级工厂。
 *
 * <p>当 team-service 发生超时或网络异常时触发降级，统一返回系统错误响应。</p>
 */
@Slf4j
@Component
public class TeamFeignFallback implements FallbackFactory<TeamFeignClient> {

    /**
     * 创建 TeamFeignClient 失败降级代理实例。
     *
     * @param cause 触发远程调用失败的底层异常对象
     * @return 返回统一错误码的降级客户端实例
     */
    @Override
    public TeamFeignClient create(Throwable cause) {
        log.error("TeamFeignClient 调用失败", cause);
        return new TeamFeignClient() {
            @Override
            public Result<TeamDTO> getTeamInfo(Long teamId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<Long>> getMemberIds(Long teamId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<TeamSubmissionAccessDTO> getSubmissionAccess(Long teamId, Long userId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<Long> getActiveTeamCount() {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<TeamDTO>> listRecent(Integer limit) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<TeamDTO>> listExpiredPractices() {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }
        };
    }
}
