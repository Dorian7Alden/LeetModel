package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class GetUserRoles extends AdminUserController {

    private final UserService userService;

    @GetMapping("/{userId}/roles")
    public Result<List<Role>> getUserRoles(@PathVariable Long userId) {
        List<Role> roles = userService.getUserRoles(userId);
        return Result.success(roles);
    }
}
