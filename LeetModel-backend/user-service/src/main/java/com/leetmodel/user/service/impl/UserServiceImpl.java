package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.common.api.dto.UserPageQuery;
import com.leetmodel.user.dto.UserUpdateRequest;
import com.leetmodel.user.entity.Role;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.entity.UserRole;
import com.leetmodel.user.enums.UserErrorCode;
import com.leetmodel.user.mapper.RoleMapper;
import com.leetmodel.user.mapper.UserMapper;
import com.leetmodel.user.mapper.UserRoleMapper;
import com.leetmodel.user.service.UserService;
import com.leetmodel.common.api.vo.UserAdminVO;
import com.leetmodel.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final StorageService storageService;

    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public UserVO getProfile(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);
        return toVO(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UserUpdateRequest request) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        boolean changed = false;
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
            changed = true;
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
            changed = true;
        }
        if (changed) {
            updateById(user);
            log.info("用户 {} 更新个人信息", userId);
        }
        return toVO(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 校验旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(UserErrorCode.PASSWORD_OLD_INVALID);
        }

        // 新旧密码不能相同
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BusinessException(UserErrorCode.PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
        log.info("用户 {} 修改密码", userId);
    }

    @Override
    public String updateAvatar(Long userId, MultipartFile file) {
        if (storageService == null) {
            throw new BusinessException(UserErrorCode.STORAGE_NOT_ENABLED);
        }

        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        String objectName = storageService.upload(file, "avatars");
        String avatarUrl = storageService.getUrl(objectName);

        user.setAvatarUrl(avatarUrl);
        updateById(user);
        log.info("用户 {} 更新头像: {}", userId, objectName);

        return avatarUrl;
    }

    // ==================== 管理员方法 ====================

    @Override
    public IPage<UserAdminVO> listUsers(UserPageQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索：匹配用户名或昵称
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w
                    .like(User::getUsername, query.getKeyword())
                    .or()
                    .like(User::getNickname, query.getKeyword())
            );
        }

        // 状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }

        wrapper.orderByDesc(User::getCreateTime);

        Page<User> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<User> userPage = baseMapper.selectPage(page, wrapper);

        // 批量获取角色
        List<Long> userIds = userPage.getRecords().stream().map(User::getId).toList();
        Map<Long, List<UserAdminVO.RoleSimpleVO>> roleMap = batchGetRoles(userIds);

        List<UserAdminVO> voList = userPage.getRecords().stream()
                .map(u -> toAdminVO(u, roleMap.getOrDefault(u.getId(), List.of())))
                .toList();

        Page<UserAdminVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public UserAdminVO getUserDetail(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        List<UserAdminVO.RoleSimpleVO> roles = getRolesByUserId(userId);
        return toAdminVO(user, roles);
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        user.setStatus(status);
        updateById(user);
        log.info("管理员更新用户 {} 状态为 {}", userId, status);
    }

    @Override
    @Transactional
    public void updateRoles(Long userId, List<Long> roleIds) {
        BusinessException.throwIf(getById(userId) == null, UserErrorCode.USER_NOT_FOUND);

        // 删除旧关联
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(wrapper);

        // 插入新关联
        for (Long roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }

        log.info("管理员更新用户 {} 的角色: {}", userId, roleIds);
    }

    // ==================== 私有方法 ====================

    private UserVO toVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }

    private UserAdminVO toAdminVO(User user, List<UserAdminVO.RoleSimpleVO> roles) {
        return UserAdminVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .roles(roles)
                .build();
    }

    /**
     * 批量获取用户角色 Map。
     */
    private Map<Long, List<UserAdminVO.RoleSimpleVO>> batchGetRoles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        // 查出所有 userId → roleId 映射
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.in(UserRole::getUserId, userIds);
        List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

        if (userRoles.isEmpty()) {
            return userIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
        }

        // 查出所有 role
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        Map<Long, Role> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));

        // 组装 userId → RoleSimpleVO 列表
        return userRoles.stream()
                .filter(ur -> roleMap.containsKey(ur.getRoleId()))
                .collect(Collectors.groupingBy(
                        UserRole::getUserId,
                        Collectors.mapping(
                                ur -> {
                                    Role role = roleMap.get(ur.getRoleId());
                                    return UserAdminVO.RoleSimpleVO.builder()
                                            .id(role.getId()).code(role.getCode()).name(role.getName())
                                            .build();
                                },
                                Collectors.toList()
                        )
                ));
    }

    /**
     * 获取单个用户的角色列表。
     */
    private List<UserAdminVO.RoleSimpleVO> getRolesByUserId(Long userId) {
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

        if (userRoles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(r -> UserAdminVO.RoleSimpleVO.builder()
                        .id(r.getId()).code(r.getCode()).name(r.getName()).build())
                .toList();
    }
}
