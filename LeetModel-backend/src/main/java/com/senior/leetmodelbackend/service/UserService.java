package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.entity.pojo.User;

import java.util.Map;

public interface UserService {

    User getUserByEmail(String email);

    User getUserById(Integer userId);

    void register(String email, String password, String code);

    void register(Map<String, Object> params);

    void deleteUserById(Integer userId);

    void updateUserById(User user);

    void resetPassword(String email, String password);
}
