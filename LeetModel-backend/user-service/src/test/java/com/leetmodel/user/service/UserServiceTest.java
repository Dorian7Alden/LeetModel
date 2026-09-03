package com.leetmodel.user.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.audit.UserAuditEventProducer;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.entity.Role;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.RoleMapper;
import com.leetmodel.user.mapper.UserMapper;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.impl.UserServiceImpl;
import com.leetmodel.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试。
 *
 * <p>通过 ReflectionTestUtils 注入 MyBatis-Plus 父类 ServiceImpl 的 baseMapper 字段，
 * 使 getById / updateById 等调用能正确委托到 mock 对象。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StorageService storageService;

    @Mock
    private UserAuditEventProducer audit;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        // MyBatis-Plus ServiceImpl 的 baseMapper 通过 @Autowired 注入，Mockito 的 @InjectMocks
        // 不会注入父类字段，需手动设置。
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$encoded");
        user.setNickname("Test");
        user.setEmail("test@example.com");
        user.setAvatarPath(null);
        user.setStatus(1);
    }

    @Test
    @DisplayName("获取个人信息成功")
    void getProfileSuccess() {
        when(userMapper.selectById(1L)).thenReturn(user);

        UserVO vo = userService.getProfile(1L);

        assertNotNull(vo);
        assertEquals("testuser", vo.getUsername());
        assertEquals("Test", vo.getNickname());
    }

    @Test
    @DisplayName("获取个人信息失败 —— 用户不存在")
    void getProfileNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.getProfile(999L));
        assertEquals(UserErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("更新个人信息成功")
    void updateProfileSuccess() {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("NewName");
        request.setEmail("new@example.com");

        UserVO vo = userService.updateProfile(1L, request);

        assertNotNull(vo);
        assertEquals("NewName", vo.getNickname());
        assertEquals("new@example.com", vo.getEmail());
    }

    @Test
    @DisplayName("上传头像时只保存对象路径")
    void updateAvatarStoresObjectPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "image-data".getBytes()
        );
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(storageService.upload(file, "avatars")).thenReturn("avatars/avatar.png");
        when(storageService.getUrl("avatars/avatar.png")).thenReturn("http://storage/avatar.png");

        String avatarUrl = userService.updateAvatar(1L, file);

        assertEquals("avatars/avatar.png", user.getAvatarPath());
        assertEquals("http://storage/avatar.png", avatarUrl);
    }

    @Test
    @DisplayName("修改密码成功")
    void changePasswordSuccess() {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("oldpass", "$2a$encoded")).thenReturn(true);
        when(passwordEncoder.encode("newpass")).thenReturn("$2a$newencoded");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldpass");
        request.setNewPassword("newpass");

        assertDoesNotThrow(() -> userService.changePassword(1L, request));
    }

    @Test
    @DisplayName("修改密码失败 —— 旧密码错误")
    void changePasswordInvalidOld() {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("wrongpass", "$2a$encoded")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrongpass");
        request.setNewPassword("newpass");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, request));
        assertEquals(UserErrorCode.PASSWORD_OLD_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("修改密码失败 —— 新旧密码相同")
    void changePasswordSameAsOld() {
        when(userMapper.selectById(1L)).thenReturn(user);
        when(passwordEncoder.matches("samepass", "$2a$encoded")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("samepass");
        request.setNewPassword("samepass");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, request));
        assertEquals(UserErrorCode.PASSWORD_SAME_AS_OLD.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("更新用户角色成功并去重")
    void updateRolesSuccessAndDeduplicate() {
        Role admin = new Role();
        admin.setId(1L);
        Role userRole = new Role();
        userRole.setId(3L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(admin, userRole));

        userService.updateRoles(1L, List.of(1L, 3L, 1L));

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, times(2)).insert(any(UserRole.class));
    }

    @Test
    @DisplayName("更新用户角色失败 —— 角色不存在")
    void updateRolesRoleNotFound() {
        Role admin = new Role();
        admin.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(admin));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateRoles(1L, List.of(1L, 999L))
        );

        assertEquals(UserErrorCode.ROLE_NOT_FOUND.getCode(), exception.getCode());
        verify(userRoleMapper, never()).delete(any());
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }
}
