package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.pojo.Result;
import com.senior.leetmodelbackend.pojo.User;
import com.senior.leetmodelbackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;


    @GetMapping("/email/{email}")
    public Result<User> getUserByEmail(@PathVariable String email) {
        User userByEmail = userService.getUserByEmail(email);
        if (userByEmail == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(userByEmail);
    }



}
