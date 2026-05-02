package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.dto.admin.PermissionDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.PermissionService;
import com.senior.leetmodelbackend.validator.admin.PermissionParamValidator;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class UpdatePermission extends AdminPermissionController {

    private final PermissionService permissionService;
    private final PermissionParamValidator validator;

    @PutMapping("/{permissionId}")
    public Result<Void> updatePermission(@PathVariable Long permissionId, @RequestBody PermissionDTO request) {
        validator.validate(request);
        permissionService.updatePermission(permissionId, request);
        return Result.success("更新成功");
    }
}
