package com.senior.leetmodelbackend.controller;


import com.senior.leetmodelbackend.pojo.Result;
import com.senior.leetmodelbackend.pojo.User;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import com.senior.leetmodelbackend.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    @Autowired
    private VerificationCodeService verificationCodeService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        // TODO: 使用 Spring Security 实现登录功能
        // TODO: 通过邮箱注册
        // TODO: 令牌缓存机制

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
        String token = JwtUtil.generateToken();

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user_data", user);

        return Result.success("登录成功", result);
    }

    /**
     * 发送验证码
     */
    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestBody Map<String, String> params) {
        // TODO: 解决邮箱配置验证码报错。抓取报错
        String email = params.get("email");
        verificationCodeService.generateAndSendCode(email);
        return Result.success("已成功向邮箱 " + email + " 发送验证码");
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, String> params) {
        String email = params.get("email");
        String password = params.get("password");
        String code = params.get("code");

        // 是否已经注册
        if (userService.getUserByEmail(email) != null) {
            return Result.error(400, "用户已存在");
        }

        // 验证码是否正确
        if (!verificationCodeService.verifyCode(email, code)) {
            return Result.error(400, "验证码错误");
        }

        // 注册
        try {
            userService.register(email, password, code);
            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(500, "注册失败");
        }
    }
}
