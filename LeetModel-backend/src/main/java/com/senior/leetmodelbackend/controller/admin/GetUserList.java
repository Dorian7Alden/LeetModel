package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class GetUserList extends AdminUserController {

    private final UserService userService;

    @GetMapping
    public Result<List<User>> getUserList() {
        List<User> list = userService.getAllUsers();
        return Result.success(list);
    }
}
