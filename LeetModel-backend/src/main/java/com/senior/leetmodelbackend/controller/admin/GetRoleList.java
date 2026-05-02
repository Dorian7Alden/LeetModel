package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.service.RoleService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class GetRoleList extends AdminRoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<List<Role>> getRoleList() {
        List<Role> list = roleService.getRoleList();
        return Result.success(list);
    }
}
