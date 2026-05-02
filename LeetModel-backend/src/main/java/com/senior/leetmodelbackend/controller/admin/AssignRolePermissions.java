package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.dto.admin.AssignIdsDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.RoleService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class AssignRolePermissions extends AdminRoleController {

    private final RoleService roleService;

    @PutMapping("/{roleId}/permissions")
    public Result<Void> assignRolePermissions(@PathVariable Long roleId, @RequestBody AssignIdsDTO request) {
        roleService.assignRolePermissions(roleId, request.getIds());
        return Result.success("分配成功");
    }
}
