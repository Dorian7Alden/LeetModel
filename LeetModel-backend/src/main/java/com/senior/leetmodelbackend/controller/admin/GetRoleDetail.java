package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.service.RoleService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class GetRoleDetail extends AdminRoleController {

    private final RoleService roleService;

    @GetMapping("/{roleId}")
    public Result<Role> getRoleDetail(@PathVariable Long roleId) {
        Role role = roleService.getRoleById(roleId);
        return Result.success(role);
    }
}
