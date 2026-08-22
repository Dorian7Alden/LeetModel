package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * RoleAdminFeignClient 降级工厂。
 */
@Slf4j
@Component
public class RoleAdminFeignFallback implements FallbackFactory<RoleAdminFeignClient> {

    @Override
    public RoleAdminFeignClient create(Throwable cause) {
        log.error("RoleAdminFeignClient 调用失败", cause);
        return new RoleAdminFeignClient() {
            @Override
            public Result<List<RoleVO>> listRoles() {
                return Result.ok(Collections.emptyList());
            }

            @Override
            public Result<RoleVO> getRole(Long roleId) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<RoleVO> createRole(RoleRequest request) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<RoleVO> updateRole(Long roleId, RoleRequest request) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<Void> deleteRole(Long roleId) {
                return Result.fail(50001, "用户服务暂不可用");
            }

            @Override
            public Result<List<PermissionVO>> listPermissions() {
                return Result.ok(Collections.emptyList());
            }
        };
    }
}
