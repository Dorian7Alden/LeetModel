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

        String emailLogin = params.get("email");
        String passwordLogin = params.get("password"); // TODO: 前端密码是否已经加密？

        // TODO: 适配多种登录方式？目前之后邮箱具有唯一性，所以只能支持使用邮箱登录
        log.info("login: {}", emailLogin);
        User userQuery = userService.getUserByEmail(emailLogin);

        // 账号是否存在
        if (userQuery.getEmail() == null) {
            log.error("没有邮箱为 {} 的用户", emailLogin);
            return Result.error(404, "没有邮箱为 " + emailLogin + " 的用户");
        }

        // 密码是否正确
        // TODO: 前端密码是否已经加密？
        if (!userQuery.getPassword().equals(passwordLogin)) {
            log.error("用户 {} 的登录密码错误", emailLogin);
            return Result.error(400, "用户 " + emailLogin + " 的登录密码错误");
        }

        log.info("用户 {} 登录成功", emailLogin);
        // 生成并刷新 token
        // TODO: token 缓存机制，避免用户重复登录，保证每个用户仅唯一 token
        String token = JwtUtil.generateToken(3600 * 24 * 3); // 24 * 3 小时的登录令牌
        log.info("创建 token: {}", token);

        // 封装返回结构
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user_data", userQuery); // TODO: 封装一个专门的响应用户信息的实体类

        return Result.success("登录成功", result);
    }

    /**
     * 发送验证码
     */
    @PostMapping("/verification-codes")
    public Result<Void> sendCode(@RequestBody Map<String, String> params) {
        // TODO: 发送验证码之前进行人机验证，完成验证后，携带验证的令牌
        // TODO: 适配多种验证码发送方式：邮箱、手机等类型。目前只支持邮箱验证码
        // 由于返回信息在 service 层进行了处理，这里直接返回结果
        String email = params.get("target");
        return verificationCodeService.sendCode(email);
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, Object> params) {

        String email = (String) params.get("email");
        String code = (String) params.get("code");

        // 是否已经注册
        if (userService.getUserByEmail(email) != null) {
            return Result.error(400, "用户已存在");
        }

        // 验证码是否正确
        if (!verificationCodeService.verifyCode(email, code)) {
            return Result.error(400, "验证码错误或已过期，请重新尝试"); // TODO: 验证失败场景分类
        }

        // 完成注册
        try {
            userService.register(params);
            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(500, "注册失败");
        }
    }
}
