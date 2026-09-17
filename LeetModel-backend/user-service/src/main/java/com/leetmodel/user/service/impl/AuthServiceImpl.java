package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.UserRoleDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.security.util.TokenUtil;
import com.leetmodel.user.dto.LoginRequest;
import com.leetmodel.user.dto.LoginResponse;
import com.leetmodel.user.dto.RegisterRequest;
import com.leetmodel.user.audit.UserAuditEventProducer;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.entity.Role;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.RoleMapper;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.AuthService;
import com.leetmodel.user.service.RoleService;
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
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final UserAuditEventProducer audit;

    private static final String DEFAULT_ROLE_CODE = "user";

    /**
     * 注册新用户并分配默认角色。
     *
     * @param request 注册请求对象，不能为 null
     * @return 新建用户唯一 ID
     * @throws BusinessException 若用户名重复或默认角色不存在
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

        // 根据稳定编码获取默认角色
        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(Role::getCode, DEFAULT_ROLE_CODE);
        Role defaultRole = roleMapper.selectOne(roleWrapper);
        BusinessException.throwIf(defaultRole == null, UserErrorCode.DEFAULT_ROLE_NOT_FOUND);

        // 分配默认角色
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(defaultRole.getId());
        userRoleMapper.insert(userRole);

        return user.getId();
    }

    /**
     * 用户登录，校验密码与账号状态后签发 Token。
     *
     * @param request 登录请求对象，不能为 null
     * @return 包含 Token 与权限信息的登录响应对象
     * @throws BusinessException 若用户不存在、密码错误或账号被禁用
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userService.findByUsername(request.getUsername());
        if (user == null) {
            audit.loginRejected("USER_NOT_FOUND");
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            audit.loginRejected("PASSWORD_INVALID");
            throw new BusinessException(UserErrorCode.PASSWORD_INVALID);
        }

        // 校验账号状态
        if (user.getStatus() == 0) {
            audit.loginRejected("ACCOUNT_DISABLED");
            throw new BusinessException(UserErrorCode.ACCOUNT_DISABLED);
        }

        // 签发 Token
        String token = TokenUtil.login(user.getId());

        // 查询用户角色和权限，供客户端初始化菜单和路由
        UserRoleDTO authorization = roleService.getUserRoles(user.getId());
        audit.loginSucceeded(user.getId(), authorization);
        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                authorization.getRoles(),
                authorization.getPermissions()
        );
    }

    /**
     * 用户登出并清除 Sa-Token 当前会话。
     */
    @Override
    public void logout() {
        TokenUtil.logout();
    }
}
