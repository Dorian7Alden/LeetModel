package com.leetmodel.user.service.impl;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.security.util.TokenUtil;
import com.leetmodel.user.dto.LoginRequest;
import com.leetmodel.user.dto.LoginResponse;
import com.leetmodel.user.dto.RegisterRequest;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.AuthService;
import com.leetmodel.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现，处理注册、登录、登出。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Long DEFAULT_ROLE_ID = 3L;

    /**
     * 注册用户并分配默认角色。
     * @param request 注册请求
     * @return 新用户 ID
     */
    @Override
    @Transactional
    public Long register(RegisterRequest request) {
        // 校验用户名是否已存在
        User existing = userService.findByUsername(request.getUsername());
        BusinessException.throwIf(existing != null, UserErrorCode.USERNAME_DUPLICATE);

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        userService.save(user);

        // 分配默认角色
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(userRole);

        return user.getId();
    }

    /**
     * 用户登录，校验密码与账号状态后签发 Token。
     * @param request 登录请求
     * @return Token 与用户基础信息
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userService.findByUsername(request.getUsername());
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 校验密码
        BusinessException.throwIf(
                !passwordEncoder.matches(request.getPassword(), user.getPassword()),
                UserErrorCode.PASSWORD_INVALID
        );

        // 校验账号状态
        BusinessException.throwIf(user.getStatus() == 0, UserErrorCode.ACCOUNT_DISABLED);

        // 签发 Token
        String token = TokenUtil.login(user.getId());

        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    /**
     * 用户登出。
     */
    @Override
    public void logout() {
        TokenUtil.logout();
    }
}
