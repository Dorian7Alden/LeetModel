package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.user.dto.ChangePasswordRequest;
import com.leetmodel.user.audit.UserAuditEventProducer;
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
    private final UserAuditEventProducer audit;

    /**
     * 根据用户名精确查询用户实体。
     *
     * @param username 用户名，不能为 null
     * @return 匹配的用户实体，未找到返回 null
     */
    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 获取当前用户的脱敏资料信息。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 用户脱敏视图对象
     * @throws BusinessException 若用户不存在
     */
    @Override
    public UserVO getProfile(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);
        return toVO(user);
    }

    /**
     * 更新用户基本资料（昵称与邮箱）。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含昵称或邮箱的更新请求对象，不能为 null
     * @return 更新后的用户脱敏视图对象
     * @throws BusinessException 若用户不存在
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
     * 修改用户登录密码并重置凭据版本。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param request 包含新旧密码的修改请求对象，不能为 null
     * @throws BusinessException 若用户不存在、旧密码错误或新旧密码相同
     */
    @Override
    @Transactional
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
        audit.passwordChanged(userId);
        log.info("用户 {} 修改密码", userId);
    }

    /**
     * 上传并更新用户个人头像。
     *
     * @param userId 目标用户 ID，不能为 null
     * @param file   待上传的头像图片文件，不能为 null
     * @return 头像访问公网 URL
     * @throws BusinessException 若对象存储未启用或用户不存在
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
     * 管理员分页组合条件查询平台用户列表。
     *
     * @param query 查询条件与分页参数对象，不能为 null
     * @return 分页包装的用户管理视图对象
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
     * 管理员查询用户详细信息（含角色列表）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @return 用户管理端视图对象
     * @throws BusinessException 若用户不存在
     */
    @Override
    public UserAdminVO getUserDetail(Long userId) {
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        List<UserAdminVO.RoleSimpleVO> roles = getRolesByUserId(userId);
        return toAdminVO(user, roles);
    }

    /**
     * 管理员修改用户账号状态（启用/禁用）。
     *
     * @param userId 目标用户 ID，不能为 null
     * @param status 目标状态（1=正常 0=禁用），不能为 null
     * @throws BusinessException 若用户不存在
     */
    @Override
    @Transactional
    public void updateStatus(Long userId, Integer status) {
        // 获取用户
        User user = getById(userId);
        BusinessException.throwIf(user == null, UserErrorCode.USER_NOT_FOUND);

        // 更新用户状态
        user.setStatus(status);
        updateById(user);
        audit.statusChanged(userId, status);
        log.info("管理员更新用户 {} 状态为 {}", userId, status);
    }

    /**
     * 管理员全量替换用户绑定的角色集合。
     *
     * @param userId  目标用户 ID，不能为 null
     * @param roleIds 角色 ID 列表，不能为 null
     * @throws BusinessException 若用户不存在或指定角色 ID 缺失
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
        audit.rolesChanged(userId, distinctRoleIds);

        log.info("管理员更新用户角色完成: userId={}, roleCount={}",
                userId, distinctRoleIds.size());
    }

    // ==================== 私有方法 ====================

    /**
     * 将用户实体转换为客户端脱敏视图对象。
     *
     * @param user 用户实体
     * @return 用户脱敏 VO
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
     * 将用户实体与角色列表组装为管理端视图对象。
     *
     * @param user  用户实体
     * @param roles 角色列表
     * @return 管理端用户明细 VO
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
     * 将数据库中的头像路径安全转换为可访问的 URL 地址（兼容完整外部链接）。
     *
     * @param avatarPath 存储桶对象路径或外部绝对 URL
     * @return 最终可访问的头像 URL；若路径为空返回 null
     */
    private String resolveAvatarUrl(String avatarPath) {
        if (avatarPath == null || avatarPath.isBlank()) return null;
        if (avatarPath.startsWith("http://") || avatarPath.startsWith("https://")) {
            return avatarPath;
        }
        return storageService.getUrl(avatarPath);
    }

    /**
     * 批量获取指定用户集合的角色映射关系。
     *
     * @param userIds 用户 ID 列表
     * @return 用户 ID 到角色列表的映射 Map
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

    /**
     * 为指定用户集合构建初始空角色 Map。
     *
     * @param userIds 用户 ID 列表
     * @return 映射为空列表的初始 Map
     */
    private Map<Long, List<UserAdminVO.RoleSimpleVO>> buildEmptyRoleMap(List<Long> userIds) {
        return userIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
    }

    /**
     * 将角色实体转换为简要角色 VO。
     *
     * @param role 角色实体
     * @return 简要角色 VO
     */
    private UserAdminVO.RoleSimpleVO toRoleSimpleVO(Role role) {
        return UserAdminVO.RoleSimpleVO.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .build();
    }

    /**
     * 查询单个用户的全部角色信息列表。
     *
     * @param userId 用户 ID
     * @return 简要角色 VO 列表
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
