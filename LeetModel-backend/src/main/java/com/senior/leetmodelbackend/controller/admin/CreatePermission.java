package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.dto.admin.PermissionDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.PermissionService;
import com.senior.leetmodelbackend.validator.admin.PermissionParamValidator;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class CreatePermission extends AdminPermissionController {

    private final PermissionService permissionService;
    private final PermissionParamValidator validator;

    @PostMapping
    public Result<Void> createPermission(@RequestBody PermissionDTO request) {
        validator.validate(request);
        permissionService.createPermission(request);
        return Result.success("创建成功");
    }
}
