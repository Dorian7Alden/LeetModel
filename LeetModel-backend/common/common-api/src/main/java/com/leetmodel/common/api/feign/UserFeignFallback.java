package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * UserFeignClient 降级工厂。
 *
 * <p>当 user 服务不可用时，返回默认的 user 角色以保证核心功能不中断。</p>
 */
@Component
public class UserFeignFallback implements FallbackFactory<UserFeignClient> {

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
            public Result<Long> getUserCount() {
                return Result.ok(0L);
            }
        };
    }
}
