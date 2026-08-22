package com.leetmodel.user.service;

import com.leetmodel.user.dto.LoginRequest;
import com.leetmodel.user.dto.LoginResponse;
import com.leetmodel.user.dto.RegisterRequest;

/**
 * 认证服务接口。
 *
 * @author LeetModel
 */
public interface AuthService {

    /**
     * 用户注册。
     *
     * @param request 注册请求
     * @return 新用户 ID
     */
    Long register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return Token + 用户信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户登出。
     * 将当前 Token 加入 Redis 黑名单。
     */
    void logout();
}
