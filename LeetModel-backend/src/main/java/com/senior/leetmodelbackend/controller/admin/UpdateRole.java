package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.dto.admin.RoleDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.RoleService;
import com.senior.leetmodelbackend.validator.admin.RoleParamValidator;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class UpdateRole extends AdminRoleController {

    private final RoleService roleService;
    private final RoleParamValidator validator;

    @PutMapping("/{roleId}")
    public Result<Void> updateRole(@PathVariable Long roleId, @RequestBody RoleDTO request) {
        validator.validate(request);
        roleService.updateRole(roleId, request);
        return Result.success("更新成功");
    }
}
