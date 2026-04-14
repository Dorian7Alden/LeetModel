package com.senior.leetmodelbackend.controller.user;

import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.entity.User;
import com.senior.leetmodelbackend.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
public class UpdateUserById extends UserController {

    private final UserService userService;

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
