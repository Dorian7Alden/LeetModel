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
 * 角色权限管理 Feign 客户端降级工厂。
 *
 * <p>当角色管理内部端点调用失败时触发降级，统一返回系统错误响应。</p>
 */
@Slf4j
@Component
public class RoleAdminFeignFallback implements FallbackFactory<RoleAdminFeignClient> {

    /**
     * 创建 RoleAdminFeignClient 失败降级代理实例。
     *
     * @param cause 触发远程调用失败的底层异常对象
     * @return 返回统一错误码的降级客户端实例
     */
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
