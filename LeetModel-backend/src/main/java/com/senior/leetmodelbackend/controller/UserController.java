package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.entity.enums.error.GlobalErrorCode;
import com.senior.leetmodelbackend.entity.enums.error.UserErrorCode;
import com.senior.leetmodelbackend.entity.pojo.Result;
import com.senior.leetmodelbackend.entity.pojo.User;
import com.senior.leetmodelbackend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    @Autowired
    private UserService userService;


    /**
     * 根据 email 获取用户信息
     */
    @GetMapping("/email/{email}")
    public Result<User> getUserByEmail(@PathVariable String email) {
        // TODO: 查询失败的情景，而非没有找到
        User userByEmail = userService.getUserByEmail(email);
        if (userByEmail == null) {
            // TODO: 异常太广泛了，没有考虑是什么原因导致的 null 。数据库异常没有抓取
            return Result.error(UserErrorCode.USER_NOT_FOUND, "没有找到邮箱为 " + email + " 的用户");
        }
        return Result.success("成功通过邮箱查询到用户", userByEmail);
    }

    /**
     * 根据 user_id 获取用户信息
     */
    @GetMapping("/{user_id}")
    public Result<User> getUserById(@PathVariable("user_id") Integer userId) {
        // TODO: 查询失败的情景，而非没有找到
        User userById = userService.getUserById(userId);
        if (userById == null) {
            // TODO: 异常太广泛了，没有考虑是什么原因导致的 null 。数据库异常没有抓取
            return Result.error(UserErrorCode.USER_NOT_FOUND, "没有找到 id 为 " + userId + " 的用户");
        }
        return Result.success("成功通过用户 id 查询到用户", userById);
    }

    /**
     * 根据 user_id 删除用户信息
     */
    @DeleteMapping("/{user_id}")
    public Result<String> deleteUserById(@PathVariable("user_id") Integer userId) {
        try {
            userService.deleteUserById(userId);
            return Result.success("已经成功删除用户 id 为 " + userId + " 的用户");
        } catch (Exception e) {
            return Result.error(GlobalErrorCode.RESOURCE_NOT_FOUND, "删除用户 " + userId + " 失败");
        }
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{user_id}")
    public Result<String> updateUserById(@PathVariable("user_id") Integer userId, @RequestBody User user) {
        log.info("接口捕获到用户: {}", user);
        user.setId(userId);
        userService.updateUserById(user);
        return Result.success("已经成功更新用户 id 为 " + userId + " 的用户");
    }
}
