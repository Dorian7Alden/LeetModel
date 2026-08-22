package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.RoleRequest;
import com.leetmodel.common.api.vo.PermissionVO;
import com.leetmodel.common.api.vo.RoleVO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 角色权限管理内部 Feign 客户端 —— 供 admin-service 调用 user-service 完成角色权限管理。
 */
@FeignClient(
        name = "user-service",
        contextId = "roleAdminFeignClient",
        path = "/internal/admin",
        fallbackFactory = RoleAdminFeignFallback.class
)
public interface RoleAdminFeignClient {

    @GetMapping("/roles")
    Result<List<RoleVO>> listRoles();

    @GetMapping("/roles/{roleId}")
    Result<RoleVO> getRole(@PathVariable("roleId") Long roleId);

    @PostMapping("/roles")
    Result<RoleVO> createRole(@RequestBody RoleRequest request);

    @PutMapping("/roles/{roleId}")
    Result<RoleVO> updateRole(@PathVariable("roleId") Long roleId,
                              @RequestBody RoleRequest request);

    @DeleteMapping("/roles/{roleId}")
    Result<Void> deleteRole(@PathVariable("roleId") Long roleId);

    @GetMapping("/permissions")
    Result<List<PermissionVO>> listPermissions();
}
