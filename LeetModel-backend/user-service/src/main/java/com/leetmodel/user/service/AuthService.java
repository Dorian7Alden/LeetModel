package com.leetmodel.user.service;

import com.leetmodel.user.dto.LoginRequest;
import com.leetmodel.user.dto.LoginResponse;
import com.leetmodel.user.dto.RegisterRequest;

/**
 * 认证服务接口。
 */
public interface AuthService {

    /**
     * 注册新用户并自动分配系统默认角色。
     *
     * @param request 注册请求对象，不能为 null
     * @return 注册成功的新增用户唯一 ID
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户名已被占用或默认角色不存在
     */
    Long register(RegisterRequest request);

    /**
     * 校验用户凭证、账号状态并签发 Sa-Token 登录令牌。
     *
     * @param request 登录请求对象，不能为 null
     * @return 包含 Token、基础信息与权限的登录响应对象
     * @throws com.leetmodel.common.core.exception.BusinessException 若用户不存在、密码错误或账号被禁用
     */
    LoginResponse login(LoginRequest request);

    /**
     * 注销当前用户的会话登录态。
     */
    void logout();
}
