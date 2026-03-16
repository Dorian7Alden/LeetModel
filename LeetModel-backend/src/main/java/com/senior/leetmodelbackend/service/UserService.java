package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.User;

public interface UserService {

    User getUserByEmail(String email);

    void register(String email, String password, String code);

}
