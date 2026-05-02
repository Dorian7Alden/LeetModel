package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.entity.Permission;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.service.PermissionService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class GetPermissionList extends AdminPermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public Result<List<Permission>> getPermissionList() {
        List<Permission> list = permissionService.getPermissionList();
        return Result.success(list);
    }
}
