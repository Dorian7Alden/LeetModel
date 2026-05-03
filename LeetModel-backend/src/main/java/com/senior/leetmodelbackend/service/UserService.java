package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.common.utils.Md5Util;
import com.senior.leetmodelbackend.mapper.RoleMapper;
import com.senior.leetmodelbackend.mapper.UserMapper;
import com.senior.leetmodelbackend.pojo.dto.RegisterDTO;
import com.senior.leetmodelbackend.pojo.dto.UserUpdateDTO;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.pojo.entity.User;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /**
     * 根据邮箱查询用户，不存在则抛出 USER_NOT_FOUND
     */
    public User getUserByEmail(String email) {
        log.info("getUserByEmail: {}", email);
        User user = userMapper.getUserByEmail(email);
        if (user == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "没有找到邮箱为 " + email + " 的用户");
        }
        return user;
    }

    /**
     * 根据 ID 查询用户，不存在则抛出 USER_NOT_FOUND
     */
    public User getUserById(Integer userId) {
        log.info("getUserById: {}", userId);
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "没有找到 id 为 " + userId + " 的用户");
        }
        return user;
    }

    /**
     * 用户认证：验证邮箱和密码，失败抛出对应业务异常
     */
    public User authenticate(String email, String password) {
        User user = getUserByEmail(email);
        if (!Md5Util.matches(password, user.getPassword())) {
            log.error("用户 {} 登录失败 -----> 密码错误", email);
            throw new BusinessException(ResponseCode.USER_PASSWORD_WRONG);
        }
        return user;
    }

    /**
     * 获取用户最高角色标识（SUPER_ADMIN > ADMIN > MEMBER），用于 JWT 签发
     */
    public String determineRole(Integer userId) {
        List<Role> roles = userMapper.getRolesByUserId(userId);
        if (roles != null && !roles.isEmpty()) {
            boolean isSuperAdmin = roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
            boolean isAdmin = roles.stream().anyMatch(r -> "ADMIN".equals(r.getCode()));
            if (isSuperAdmin) {
                return "SUPER_ADMIN";
            } else if (isAdmin) {
                return "ADMIN";
            }
        }
        return "MEMBER";
    }

    /**
     * 用户注册，邮箱重复时抛出 USER_ALREADY_EXISTS，默认分配 MEMBER 角色
     */
    public void register(RegisterDTO request) {
        if (userMapper.getUserByEmail(request.getEmail()) != null) {
            throw new BusinessException(ResponseCode.USER_ALREADY_EXISTS);
        }

        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(Md5Util.encode(request.getPassword()));
        newUser.setUsername("user_" + Long.toHexString(System.nanoTime()));

        log.info("insertUser: {}", newUser);
        userMapper.insertUser(newUser);

        Role memberRole = roleMapper.getRoleByCode("MEMBER");
        if (memberRole == null) {
            throw new BusinessException(ResponseCode.SYSTEM_INTERNAL_ERROR, "系统未找到默认角色 MEMBER");
        }
        userMapper.insertUserRole(newUser.getUserId(), memberRole.getRoleId());
    }

    /**
     * 删除用户，不存在则抛出 USER_NOT_FOUND
     */
    public void deleteUserById(Integer userId) {
        log.info("deleteUserById: {}", userId);
        if (userMapper.getUserById(userId) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "删除失败，没有找到 id 为 " + userId + " 的用户");
        }
        userMapper.deleteUserById(userId);
    }

    /**
     * 更新用户信息，不存在则抛出 USER_NOT_FOUND
     */
    public void updateUserById(Integer userId, UserUpdateDTO dto) {
        if (userMapper.getUserById(userId) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "更新失败，没有找到 id 为 " + userId + " 的用户");
        }
        User user = new User();
        user.setUserId(userId);
        user.setUsername(dto.getUsername());
        user.setSchool(dto.getSchool());
        user.setAvatarFileId(dto.getAvatarFileId());
        userMapper.updateUserById(user);
    }

    /**
     * 重置密码，用户不存在时抛出 USER_NOT_FOUND
     */
    public void resetPassword(String email, String rawPassword) {
        if (userMapper.getUserByEmail(email) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "没有找到邮箱为 " + email + " 的用户");
        }
        userMapper.updateUserPassword(email, Md5Util.encode(rawPassword));
        log.info("重置密码成功: {}", email);
    }

    /**
     * 查询全部用户
     */
    public List<User> getAllUsers() {
        return userMapper.getAllUsers();
    }

    /**
     * 查询用户持有的角色列表
     */
    public List<Role> getUserRoles(Integer userId) {
        return userMapper.getRolesByUserId(userId);
    }

    /**
     * 分配用户角色（先删后增），userId 不存在时抛出 USER_NOT_FOUND
     */
    @Transactional
    public void assignUserRoles(Integer userId, List<Integer> roleIds) {
        if (userMapper.getUserById(userId) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "分配角色失败，没有找到 id 为 " + userId + " 的用户");
        }
        userMapper.deleteUserRolesByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Integer roleId : roleIds) {
                userMapper.insertUserRole(userId, roleId);
            }
        }
        log.info("分配用户角色: userId={}, roleIds={}", userId, roleIds);
    }

    public Set<String> getUserPermissionCodes(Integer userId) {
        List<String> codes = userMapper.getUserPermissionCodes(userId);
        return codes != null ? new HashSet<>(codes) : Collections.emptySet();
    }

    public boolean hasPermission(Integer userId, String permissionCode) {
        return getUserPermissionCodes(userId).contains(permissionCode);
    }
}
