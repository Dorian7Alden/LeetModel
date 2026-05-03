package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import com.senior.leetmodelbackend.common.utils.Md5Util;
import com.senior.leetmodelbackend.mapper.UserMapper;
import com.senior.leetmodelbackend.pojo.dto.RegisterDTO;
import com.senior.leetmodelbackend.pojo.entity.Role;
import com.senior.leetmodelbackend.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

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
    public User getUserById(Long userId) {
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
    public String determineRole(Long userId) {
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
        newUser.setUserId((long) (userMapper.getMaxUserId() + 1));
        newUser.setEmail(request.getEmail());
        newUser.setPassword(Md5Util.encode(request.getPassword()));
        newUser.setUsername("user_" + newUser.getUserId());

        log.info("insertUser: {}", newUser);
        userMapper.insertUser(newUser);

        // 分配默认角色 MEMBER (role_id = 1)
        userMapper.insertUserRole(newUser.getUserId(), 1L);
    }

    /**
     * 删除用户，不存在则抛出 USER_NOT_FOUND
     */
    public void deleteUserById(Long userId) {
        log.info("deleteUserById: {}", userId);
        if (userMapper.getUserById(userId) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "删除失败，没有找到 id 为 " + userId + " 的用户");
        }
        userMapper.deleteUserById(userId);
    }

    /**
     * 更新用户信息，不存在则抛出 USER_NOT_FOUND
     */
    public void updateUserById(User user) {
        log.info("更新用户信息: {}", user);
        if (userMapper.getUserById(user.getUserId()) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "更新失败，没有找到 id 为 " + user.getUserId() + " 的用户");
        }
        userMapper.updateUserById(user);
    }

    /**
     * 重置密码，用户不存在时抛出 USER_NOT_FOUND
     */
    public void resetPassword(String email, String password) {
        if (userMapper.getUserByEmail(email) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "没有找到邮箱为 " + email + " 的用户");
        }
        userMapper.updateUserPassword(email, password);
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
    public List<Role> getUserRoles(Long userId) {
        return userMapper.getRolesByUserId(userId);
    }

    /**
     * 分配用户角色（先删后增），userId 不存在时抛出 USER_NOT_FOUND
     */
    @Transactional
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        if (userMapper.getUserById(userId) == null) {
            throw new BusinessException(ResponseCode.USER_NOT_FOUND, "分配角色失败，没有找到 id 为 " + userId + " 的用户");
        }
        userMapper.deleteUserRolesByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                userMapper.insertUserRole(userId, roleId);
            }
        }
        log.info("分配用户角色: userId={}, roleIds={}", userId, roleIds);
    }
}
