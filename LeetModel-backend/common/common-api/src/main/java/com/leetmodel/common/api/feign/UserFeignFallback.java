package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.api.dto.UserPublicSummaryDTO;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户微服务 Feign 客户端安全降级工厂。
 *
 * <p>当 user-service 不可用或超时时触发降级：返回空的角色与权限集合，
 * 由 AuthExceptionHandler 转换为 HTTP 403 拒绝访问，保证安全不倒置。</p>
 */
@Component
public class UserFeignFallback implements FallbackFactory<UserFeignClient> {

    /**
     * 创建执行安全降级的 UserFeignClient 代理实例。
     *
     * @param cause 触发远程调用失败的底层异常对象
     * @return 返回空权限集合以触发 403 拒绝的降级客户端实例
     */
    @Override
    public UserFeignClient create(Throwable cause) {
        return new UserFeignClient() {
            @Override
            public Result<UserRoleDTO> getUserRoles(Long userId) {
                UserRoleDTO dto = new UserRoleDTO(userId, List.of(), List.of());
                return Result.ok(dto);
            }

            @Override
            public Result<Boolean> isUserAvailable(Long userId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<UserPublicSummaryDTO>> getPublicSummaries(List<Long> userIds) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<Long> getUserCount() {
                return Result.ok(0L);
            }
        };
    }
}
