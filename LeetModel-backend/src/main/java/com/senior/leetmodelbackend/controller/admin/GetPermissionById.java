package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.entity.Permission;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.PermissionService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class GetPermissionById extends AdminPermissionController {

    private final PermissionService permissionService;

    @GetMapping("/{permissionId}")
    public Result<Permission> getPermissionById(@PathVariable Long permissionId) {
        Permission permission = permissionService.getPermissionById(permissionId);
        return Result.success(permission);
    }
}
