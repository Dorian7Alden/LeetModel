package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.common.utils.Md5Util;
import com.senior.leetmodelbackend.mapper.UserMapper;
import com.senior.leetmodelbackend.pojo.dto.RegisterDTO;
import com.senior.leetmodelbackend.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public User getUserByEmail(String email) {
        try {
            log.info("getUserByEmail: {}", email);
            return userMapper.getUserByEmail(email);
        }
        catch (Exception e) {
            log.error("getUserByEmail: {} error: {}", email, e.getMessage());
            return null;
        }
    }

    public void register(RegisterDTO request) {
        User newUser = new User();
        newUser.setUserId(userMapper.getMaxUserId() + 1);
        newUser.setEmail(request.getEmail());
        newUser.setPassword(Md5Util.encode(request.getPassword()));
        newUser.setUsername("user_" + newUser.getUserId());

        // 管理员识别（临时方案）
        if ("admin@email.com".equals(request.getEmail())) {
            newUser.setRole("admin");
        } else {
            newUser.setRole("user");
        }

        log.info("insertUser: {}", newUser);
        userMapper.insertUser(newUser);
    }

    public User getUserById(Integer userId) {
        try {
            log.info("getUserById: {}", userId);
            return userMapper.getUserById(userId);
        }
        catch (Exception e) {
            log.error("getUserById: {} error: {}", userId, e.getMessage());
            return null;
        }
    }

    public void deleteUserById(Integer userId) {
        try {
            log.info("deleteUserById: {}", userId);
            userMapper.deleteUserById(userId);
        }
        catch (Exception e) {
            log.error("deleteUserById: {} error: {}", userId, e.getMessage());
        }
    }

    public void updateUserById(User user) {
        log.info("更新用户信息: {}", user);
        userMapper.updateUserById(user);
    }

    public void resetPassword(String email, String password) {
        userMapper.updateUserPassword(email, password);
    }
}
