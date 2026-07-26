package com.leetmodel.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.user.entity.User;

/**
 * 用户服务接口。
 *
 * @author LeetModel
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户实体，不存在则返回 null
     */
    User findByUsername(String username);
}
