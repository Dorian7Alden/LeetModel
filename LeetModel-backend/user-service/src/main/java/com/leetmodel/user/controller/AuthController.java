package com.leetmodel.user.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.user.dto.LoginRequest;
import com.leetmodel.user.dto.LoginResponse;
import com.leetmodel.user.dto.RegisterRequest;
import com.leetmodel.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端认证控制器。
 *
 * <p>面向客户端，提供注册、登录、登出接口。用户未登录时通过网关
 * SaTokenConfig 白名单访问，不需要登录态。</p>
 */
@Tag(name = "客户端-认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册新用户并自动分配默认角色。
     *
     * @param request 包含用户名、密码与昵称的注册请求对象，不能为 null
     * @return 注册成功的新增用户唯一标识（userId）
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request);
        return Result.ok(userId);
    }

    /**
     * 用户登录并签发 Sa-Token 访问凭据。
     *
     * @param request 包含用户名与密码的登录请求对象，不能为 null
     * @return 包含 Token、基础资料、角色及权限编码的登录响应对象
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.ok(response);
    }

    /**
     * 当前登录用户登出并清理登录态。
     *
     * @return 统一成功空响应
     */
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }
}
