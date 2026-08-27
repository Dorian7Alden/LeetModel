package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.PermissionRequest;
import com.leetmodel.common.api.dto.RolePermissionsRequest;
import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

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
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<RoleVO> getRole(Long roleId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<RoleVO> createRole(RoleRequest request) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<RoleVO> updateRole(Long roleId, RoleRequest request) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<Void> deleteRole(Long roleId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<PermissionVO>> getRolePermissions(Long roleId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<Void> updateRolePermissions(Long roleId, RolePermissionsRequest request) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<PermissionVO>> listPermissions() {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<PermissionVO> getPermission(Long permissionId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<PermissionVO> createPermission(PermissionRequest request) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<PermissionVO> updatePermission(Long permissionId, PermissionRequest request) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<Void> deletePermission(Long permissionId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }
        };
    }
}
