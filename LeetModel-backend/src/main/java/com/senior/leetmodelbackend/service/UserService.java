package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.mapper.UserMapper;
import com.senior.leetmodelbackend.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

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
            // TODO: 异常太广泛了，没有考虑是什么原因导致的 null 。数据库异常没有抓取
            log.error("getUserByEmail: {} error: {}", email, e.getMessage());
            return null;
        }
    }

    public void register(String email, String password, String code) {
        User newUser = new User();
        newUser.setId(userMapper.getMaxUserId()+1); // 模拟逻辑自增 id
        newUser.setEmail(email);
        newUser.setPassword(password);
        // TODO: 防止报错，密码加密
        userMapper.insertUser(newUser);
    }

    public void register(Map<String, Object> params) {
        User newUser = new User();
        newUser.setId(userMapper.getMaxUserId()+1);
        newUser.setEmail((String) params.get("email"));
        newUser.setPassword((String) params.get("password"));
        // 生成一个临时用户名
        newUser.setUsername("user_" + newUser.getId());
        log.info("insertUser: {}", newUser);
        userMapper.insertUser(newUser);
    }

    public User getUserById(Integer userId) {
        try {
            log.info("getUserById: {}", userId);
            return userMapper.getUserById(userId);
        }
        catch (Exception e) {
            // TODO: 异常太广泛了，没有考虑是什么原因导致的 null 。数据库异常没有抓取
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
