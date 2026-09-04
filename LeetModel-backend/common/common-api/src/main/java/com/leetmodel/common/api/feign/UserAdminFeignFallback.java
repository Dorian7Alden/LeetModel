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
 * 用户管理端点 Feign 客户端降级工厂。
 *
 * <p>当用户后台查询端点调用失败时触发降级，统一返回用户服务暂不可用错误提示。</p>
 */
@Slf4j
@Component
public class UserAdminFeignFallback implements FallbackFactory<UserAdminFeignClient> {

    /**
     * 创建 UserAdminFeignClient 失败降级代理实例。
     *
     * @param cause 触发远程调用失败的底层异常对象
     * @return 返回指定错误文本的降级客户端实例
     */
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
