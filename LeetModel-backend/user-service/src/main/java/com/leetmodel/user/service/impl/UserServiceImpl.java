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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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

    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户
     */
    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 获取用户个人资料
     * @param userId 用户ID
     * @return 用户资料
     */
    @Override
    public UserVO getProfile(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);
        return toVO(user);
    }

    /**
     * 更新用户个人资料
     * @param userId 用户ID
     * @param request 更新请求
     * @return 更新后的用户资料
     */
    @Override
    public UserVO updateProfile(Long userId, UserUpdateRequest request) {
        // 获取用户
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 更新用户信息
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

    /**
     * 修改密码
     * @param userId 用户ID
     * @param request 修改密码请求
     */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 校验旧密码
        BusinessException.throwIf(
                !passwordEncoder.matches(request.getOldPassword(), user.getPassword()),
                UserErrorCode.PASSWORD_OLD_INVALID
        );

        // 新旧密码不能相同
        BusinessException.throwIf(
                request.getOldPassword().equals(request.getNewPassword()),
                UserErrorCode.PASSWORD_SAME_AS_OLD
        );

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        updateById(user);
        log.info("用户 {} 修改密码", userId);
    }

    /**
     * 更新用户头像
     * @param userId 用户ID
     * @param file 头像文件
     * @return 头像URL
     */
    @Override
    public String updateAvatar(Long userId, MultipartFile file) {
        // 检查存储服务
        if (storageService == null) {
            throw new BusinessException(UserErrorCode.STORAGE_NOT_ENABLED);
        }

        // 获取用户
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 数据库只保存与域名无关的对象路径
        String avatarPath = storageService.upload(file, "avatars");
        user.setAvatarPath(avatarPath);
        updateById(user);
        log.info("用户 {} 更新头像", userId);

        return storageService.getUrl(avatarPath);
    }

    // ==================== 管理员方法 ====================

    /**
     * 分页获取用户列表
     * @param query 查询参数
     * @return 用户列表
     */
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

        // 按创建时间降序排序
        wrapper.orderByDesc(User::getCreateTime);

        // 创建分页对象
        Page<User> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<User> userPage = baseMapper.selectPage(page, wrapper);

        // 批量获取角色
        List<Long> userIds = userPage.getRecords().stream().map(User::getId).toList();
        Map<Long, List<UserAdminVO.RoleSimpleVO>> roleMap = batchGetRoles(userIds);

        // 转换为 AdminVO
        List<UserAdminVO> voList = userPage.getRecords().stream()
                .map(u -> toAdminVO(u, roleMap.getOrDefault(u.getId(), List.of())))
                .toList();

        // 创建分页对象，返回结果
        Page<UserAdminVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 获取用户详情
     * @param userId 用户ID
     * @return 用户详情
     */
    @Override
    public UserAdminVO getUserDetail(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        List<UserAdminVO.RoleSimpleVO> roles = getRolesByUserId(userId);
        return toAdminVO(user, roles);
    }

    /**
     * 更新用户状态
     * @param userId 用户ID
     * @param status 状态
     */
    @Override
    public void updateStatus(Long userId, Integer status) {
        // 获取用户
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 更新用户状态
        user.setStatus(status);
        updateById(user);
        log.info("管理员更新用户 {} 状态为 {}", userId, status);
    }

    /**
     * 更新用户角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     */
    @Override
    @Transactional
    public void updateRoles(Long userId, List<Long> roleIds) {
        // 校验用户存在
        BusinessException.throwIf(getById(userId) == null, UserErrorCode.USER_NOT_FOUND);

        // 角色去重并校验全部存在
        List<Long> distinctRoleIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
        List<Role> roles = roleMapper.selectBatchIds(distinctRoleIds);
        BusinessException.throwIf(
                roles.size() != distinctRoleIds.size(),
                UserErrorCode.ROLE_NOT_FOUND
        );

        // 删除旧关联
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(wrapper);

        // 插入新关联
        for (Long roleId : distinctRoleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }

        log.info("管理员更新用户角色完成: userId={}, roleCount={}",
                userId, distinctRoleIds.size());
    }

    // ==================== 私有方法 ====================

    /**
     * User 转换为 UserVO
     */
    private UserVO toVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(resolveAvatarUrl(user.getAvatarPath()))
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }

    /**
     * User 转换为 UserAdminVO
     */
    private UserAdminVO toAdminVO(User user, List<UserAdminVO.RoleSimpleVO> roles) {
        return UserAdminVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(resolveAvatarUrl(user.getAvatarPath()))
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .roles(roles)
                .build();
    }

    /**
     * 将数据库中的头像路径转换为访问地址。
     * 兼容迁移前已经保存的完整外部头像地址。
     */
    private String resolveAvatarUrl(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) return null;
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return avatarPath;
        }
        return storageService.getUrl(avatarPath);
    }

    /**
     * 批量获取用户角色 Map。
     * @param userIds 用户ID 列表
     * @return userId → 角色列表
     */
    private Map<Long, List<UserAdminVO.RoleSimpleVO>> batchGetRoles(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();

        // 查出所有 userId → roleId 映射
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.in(UserRole::getUserId, userIds);
        List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

        // 如果没有关联记录，返回 userId → 空列表
        if (userRoles.isEmpty()) {
            return buildEmptyRoleMap(userIds);
        }

        // 查出所有角色
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        Map<Long, Role> roleMap = roles.stream()
                .collect(Collectors.toMap(Role::getId, role -> role));

        // 组装 userId → RoleSimpleVO 列表
        Map<Long, List<UserAdminVO.RoleSimpleVO>> result = new HashMap<>();
        for (UserRole userRole : userRoles) {
            Role role = roleMap.get(userRole.getRoleId());
            if (role == null) continue;
            result.computeIfAbsent(userRole.getUserId(), key -> new ArrayList<>())
                    .add(toRoleSimpleVO(role));
        }
        return result;
    }

    private Map<Long, List<UserAdminVO.RoleSimpleVO>> buildEmptyRoleMap(List<Long> userIds) {
        return userIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
    }

    private UserAdminVO.RoleSimpleVO toRoleSimpleVO(Role role) {
        return UserAdminVO.RoleSimpleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .build();
    }

    /**
     * 根据用户ID获取角色列表
     * @param userId 用户ID
     * @return 角色列表
     */
    private List<UserAdminVO.RoleSimpleVO> getRolesByUserId(Long userId) {
        // 查询用户角色关联
        LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);

        if (userRoles.isEmpty()) return List.of();

        // 获取角色ID列表
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        // 返回角色列表
        return roleMapper.selectBatchIds(roleIds).stream()
                // 转换为 RoleSimpleVO
                .map(r -> UserAdminVO.RoleSimpleVO.builder()
                    .id(r.getId())
                    .code(r.getCode())
                    .name(r.getName())
                    .build())
                .toList();
    }
}
