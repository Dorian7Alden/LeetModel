package com.leetmodel.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.common.api.dto.FileAccessUrlDTO;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.feign.FileFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
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
import com.leetmodel.user.messaging.UserAvatarEventProducer;
import com.leetmodel.user.service.impl.UserServiceImpl;
import com.leetmodel.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
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
    private FileFeignClient fileFeignClient;

    @Mock
    private UserAvatarEventProducer avatarEvents;

    @Mock
    private UserAuditEventProducer audit;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "user-service-test"),
                User.class
        );
        // MyBatis-Plus ServiceImpl 的 baseMapper 通过 @Autowired 注入，Mockito 的 @InjectMocks
        // 不会注入父类字段，需手动设置。
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("$2a$encoded");
        user.setNickname("Test");
        user.setEmail("test@example.com");
        user.setAvatarFileId(null);
        user.setAvatarUrl(null);
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
    @DisplayName("上传头像时只保存 fileId 并写入绑定事件")
    void updateAvatarStoresFileIdAndBinding() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "image-data".getBytes()
        );
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(fileFeignClient.registerForPurpose(any(), any(), any(), any()))
                .thenReturn(Result.ok(new FileAssetSummaryDTO(
                        9101L, "avatar", "USER_AVATAR", "avatar.png",
                        "image/png", 10L, "AVAILABLE_UNBOUND")));
        when(fileFeignClient.createAccessUrl(9101L))
                .thenReturn(Result.ok(new FileAccessUrlDTO("http://storage/avatar.png", 600)));

        String avatarUrl = userService.updateAvatar(1L, file);

        assertEquals(9101L, user.getAvatarFileId());
        assertNull(user.getAvatarUrl());
        assertEquals("http://storage/avatar.png", avatarUrl);
        verify(fileFeignClient).registerForPurpose(
                eq(UserAvatarEventProducer.PURPOSE_CODE), eq("profile"), eq(null), eq(file));
        verify(avatarEvents).bound(1L, 9101L);
        verify(avatarEvents, never()).unbound(any(), any());
    }

    @Test
    @DisplayName("更换头像时解除旧头像引用")
    void updateAvatarReleasesPreviousBinding() {
        user.setAvatarFileId(9000L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "image-data".getBytes()
        );
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        when(fileFeignClient.registerForPurpose(any(), any(), any(), any()))
                .thenReturn(Result.ok(new FileAssetSummaryDTO(
                        9102L, "avatar", "USER_AVATAR", "avatar.png",
                        "image/png", 10L, "AVAILABLE_UNBOUND")));
        when(fileFeignClient.createAccessUrl(9102L))
                .thenReturn(Result.ok(new FileAccessUrlDTO("http://storage/new.png", 600)));

        userService.updateAvatar(1L, file);

        verify(avatarEvents).unbound(1L, 9000L);
        verify(avatarEvents).bound(1L, 9102L);
    }

    @Test
    @DisplayName("外链头像在存储服务不可用时仍可返回")
    void externalAvatarUrlRemainsUsable() {
        user.setAvatarUrl("https://api.dicebear.com/9.x/micah/svg?seed=demo");

        assertEquals("https://api.dicebear.com/9.x/micah/svg?seed=demo",
                userService.resolveAvatarUrl(user));
        verify(fileFeignClient, never()).createAccessUrl(any());
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
    @DisplayName("管理端用户查询支持邮箱关键字和角色筛选")
    void listUsersSupportsEmailKeywordAndRoleFilter() {
        UserPageQuery query = new UserPageQuery();
        query.setKeyword("example.com");
        query.setRoleId(3L);
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 20, 0));

        userService.listUsers(query);

        ArgumentCaptor<Wrapper<User>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sqlSegment = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("email"));
        assertTrue(sqlSegment.contains("user_role"));
        assertTrue(sqlSegment.contains("role_id = 3"));
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
