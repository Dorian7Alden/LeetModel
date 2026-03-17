package com.senior.leetmodelbackend.service.impl;

import com.senior.leetmodelbackend.mapper.UserMapper;
import com.senior.leetmodelbackend.pojo.User;
import com.senior.leetmodelbackend.service.UserService;
import com.senior.leetmodelbackend.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private VerificationCodeService verificationCodeService;

    @Override
    public User getUserByEmail(String email) {
        return userMapper.getUserByEmail(email);
    }

    @Override
    public void register(String email, String password, String code) {
        // TODO: 为新用户添加 id
        User newUser = new User();
        newUser.setId(999);
        newUser.setEmail(email);
        newUser.setPassword(password);
        // TODO: 防止报错，密码加密
        userMapper.insertUser(newUser);
    }
}
