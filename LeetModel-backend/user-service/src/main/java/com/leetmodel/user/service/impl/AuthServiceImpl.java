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
 * 认证服务实现 —— 注册、登录、登出。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Long DEFAULT_ROLE_ID = 3L; // user 角色

    @Override
    @Transactional
    public Long register(RegisterRequest request) {
        // 1. 校验用户名是否已存在
        User existing = userService.findByUsername(request.getUsername());
        BusinessException.throwIf(existing != null, UserErrorCode.USERNAME_DUPLICATE);

        // 2. 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        userService.save(user);

        // 3. 分配默认角色（user）
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(userRole);

        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查用户
        User user = userService.findByUsername(request.getUsername());
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 2. 校验密码
        BusinessException.throwIf(
                !passwordEncoder.matches(request.getPassword(), user.getPassword()),
                UserErrorCode.PASSWORD_INVALID
        );

        // 3. 校验账号状态
        BusinessException.throwIf(user.getStatus() == 0, UserErrorCode.ACCOUNT_DISABLED);

        // 4. 签发 Token
        String token = TokenUtil.login(user.getId());

        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    @Override
    public void logout() {
        TokenUtil.logout();
    }
}
