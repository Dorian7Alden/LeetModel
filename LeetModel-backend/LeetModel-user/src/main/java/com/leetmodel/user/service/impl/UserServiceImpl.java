package com.leetmodel.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.user.entity.User;
import com.leetmodel.user.mapper.UserMapper;
import com.leetmodel.user.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现。
 *
 * @author LeetModel
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
