package com.leetmodel.user.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.user.dto.LoginRequest;
import com.leetmodel.user.dto.RegisterRequest;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 认证服务单元测试。
 *
 * <p>login 方法涉及 Sa-Token 上下文（Servlet 环境），纯单元测试中可用 MockedStatic 模拟，
 * 或通过 SpringBootTest 集成测试覆盖。此处聚焦于注册和权限校验逻辑的单元测试。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password123");
        registerRequest.setNickname("Test");

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$encoded");
        user.setNickname("Test");
        user.setStatus(1);
    }

    @Test
    @DisplayName("注册成功")
    void registerSuccess() {
        when(userService.findByUsername("testuser")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(userService.save(any(User.class))).thenReturn(true);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        Long userId = authService.register(registerRequest);

        // 在 mock 环境下 ID 由 MyBatis-Plus 雪花算法生成，此处仅验证调用链正确
        verify(userService).save(any(User.class));
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    @Test
    @DisplayName("注册失败 —— 用户名重复")
    void registerDuplicateUsername() {
        when(userService.findByUsername("testuser")).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(registerRequest));
        assertEquals(UserErrorCode.USERNAME_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录失败 —— 用户不存在")
    void loginUserNotFound() {
        when(userService.findByUsername("testuser")).thenReturn(null);

        LoginRequest request = new LoginRequest("testuser", "pass");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request));
        assertEquals(UserErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录失败 —— 密码错误")
    void loginInvalidPassword() {
        when(userService.findByUsername("testuser")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$encoded")).thenReturn(false);

        LoginRequest request = new LoginRequest("testuser", "wrong");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request));
        assertEquals(UserErrorCode.PASSWORD_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录失败 —— 账号已禁用")
    void loginAccountDisabled() {
        user.setStatus(0);
        when(userService.findByUsername("testuser")).thenReturn(user);
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);

        LoginRequest request = new LoginRequest("testuser", "password123");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request));
        assertEquals(UserErrorCode.ACCOUNT_DISABLED.getCode(), ex.getCode());
    }
}
