package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.dto.admin.RoleDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.RoleService;
import com.senior.leetmodelbackend.validator.admin.RoleParamValidator;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class CreateRole extends AdminRoleController {

    private final RoleService roleService;
    private final RoleParamValidator validator;

    @PostMapping
    public Result<Void> createRole(@RequestBody RoleDTO request) {
        validator.validate(request);
        roleService.createRole(request);
        return Result.success("创建成功");
    }
}
