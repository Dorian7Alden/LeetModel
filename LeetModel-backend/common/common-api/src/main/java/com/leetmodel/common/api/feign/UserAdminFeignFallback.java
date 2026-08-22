package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.common.api.dto.UserRolesRequest;
import com.leetmodel.common.api.dto.UserStatusRequest;
import com.leetmodel.common.api.vo.UserAdminVO;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * UserAdminFeignClient 降级工厂。
 */
@Slf4j
@Component
public class UserAdminFeignFallback implements FallbackFactory<UserAdminFeignClient> {

    @Override
    public UserAdminFeignClient create(Throwable cause) {
        log.error("UserAdminFeignClient 调用失败", cause);
        return new UserAdminFeignClient() {
            @Override
            public Result<PageResult<UserAdminVO>> page(UserPageQuery query) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<UserAdminVO> detail(Long userId) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<Void> updateStatus(Long userId, UserStatusRequest request) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<Void> updateRoles(Long userId, UserRolesRequest request) {
                return Result.fail(50001, "用户服务暂不可用");
            }
        };
    }
}
