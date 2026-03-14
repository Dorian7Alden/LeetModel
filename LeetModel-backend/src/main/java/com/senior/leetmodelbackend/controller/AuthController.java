package com.senior.leetmodelbackend.controller;


import com.senior.leetmodelbackend.pojo.Result;
import com.senior.leetmodelbackend.pojo.User;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> params) {
        // TODO: 使用 Spring Security 实现登录功能
        // TODO: 通过邮箱注册

        // TODO: 前端传过来的密码是明文还是密文，先假设为明文。前端使用非堆成加密密码，然后后端解密之后，再换成摘要加密算法存储密码
        String emailLogin = params.get("email");
        String passwordLogin = params.get("password");
        User user = userService.getUserByEmail(emailLogin);

        // 是否已经完成注册
        if (user.getEmail() == null) {
            return Result.error(404, "用户不存在");
        }

        // 密码是否正确
        // TODO: 密码加密存储
        if (!user.getPassword().equals(passwordLogin)) {
            return Result.error(401, "密码错误");
        }

        // 登录成功，返回 token
        String token = JwtUtil.generateToken(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
                // 待展示的更多信息
        ));
        return Result.success("登录成功", token);
    }
}
